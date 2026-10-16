package app.slidedictate

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.abs

/**
 * Records 16 kHz mono PCM. Splits speech on silence (1.2 s) and emits each
 * chunk through onChunk while still recording, so text appears while speaking.
 * stop() returns the un-emitted tail. Without a pause the whole take arrives at stop().
 */
class Recorder {
    companion object {
        const val RATE = 16_000
        private const val FRAME = 320
        private const val SPEECH_THRESH = 400
        private const val SILENCE_FRAMES = 60
        private const val MIN_SPEECH_FRAMES = 15

        fun audible(pcm: ShortArray): Boolean {
            if (pcm.size < RATE / 2) return false
            var sum = 0L
            for (s in pcm) sum += abs(s.toInt())
            return sum / pcm.size > SPEECH_THRESH
        }
    }

    var onChunk: ((ShortArray) -> Unit)? = null

    private var rec: AudioRecord? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    private val tail = ArrayList<Short>()

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return false
        val r = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, min * 2
        )
        if (r.state != AudioRecord.STATE_INITIALIZED) { r.release(); return false }
        synchronized(tail) { tail.clear() }
        rec = r
        running = true
        r.startRecording()
        thread = Thread {
            val tmp = ShortArray(min)
            var silence = 0
            var speech = 0
            while (running) {
                val n = r.read(tmp, 0, tmp.size)
                if (n <= 0) continue
                var i = 0
                while (i + FRAME <= n) {
                    var sum = 0L
                    for (j in 0 until FRAME) sum += abs(tmp[i + j].toInt())
                    synchronized(tail) { for (j in 0 until FRAME) tail.add(tmp[i + j]) }
                    if (sum / FRAME > SPEECH_THRESH) { speech++; silence = 0 } else silence++
                    if (silence >= SILENCE_FRAMES && speech >= MIN_SPEECH_FRAMES) {
                        emitTail()
                        silence = 0; speech = 0
                    }
                    i += FRAME
                }
                synchronized(tail) { for (k in i until n) tail.add(tmp[k]) }
            }
        }.also { it.start() }
        return true
    }

    private fun emitTail() {
        val out = synchronized(tail) {
            if (tail.isEmpty()) return
            val a = tail.toShortArray(); tail.clear(); a
        }
        onChunk?.invoke(out)
    }

    fun stop(): ShortArray {
        running = false
        thread?.join()
        runCatching { rec?.stop() }
        rec?.release()
        rec = null
        onChunk = null
        synchronized(tail) {
            val out = tail.toShortArray(); tail.clear(); return out
        }
    }
}
