package app.slidedictate

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

/** Records 16 kHz mono PCM until stop(), which returns everything captured. */
class Recorder {
    companion object { const val RATE = 16_000 }

    private var rec: AudioRecord? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    private val chunks = ArrayList<ShortArray>()

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return false
        val r = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, min * 2
        )
        if (r.state != AudioRecord.STATE_INITIALIZED) { r.release(); return false }
        synchronized(chunks) { chunks.clear() }
        rec = r
        running = true
        r.startRecording()
        thread = Thread {
            val tmp = ShortArray(min)
            while (running) {
                val n = r.read(tmp, 0, tmp.size)
                if (n > 0) synchronized(chunks) { chunks.add(tmp.copyOf(n)) }
            }
        }.also { it.start() }
        return true
    }

    fun stop(): ShortArray {
        running = false
        thread?.join()
        runCatching { rec?.stop() }
        rec?.release()
        rec = null
        synchronized(chunks) {
            val out = ShortArray(chunks.sumOf { it.size })
            var i = 0
            for (c in chunks) { c.copyInto(out, i); i += c.size }
            return out
        }
    }
}
