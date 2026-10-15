package app.slidedictate

import java.io.File

/**
 * On-device Whisper engine backed by whisper.cpp through JNI.
 * Runs CPU-only so it works on any arm64 / arm32 / x86_64 device, offline.
 * Throws if the native lib or model cannot be loaded; callers fall back to stub.
 */
class WhisperEngine(modelFile: File) : Transcriber {

    private var handle: Long = 0L
    private var closed = false

    init {
        val err = loadError
        check(err == null) { "native lib missing: $err" }
        handle = nativeInit(modelFile.absolutePath)
        check(handle != 0L) { "whisper init failed for ${modelFile.name}" }
    }

    override fun transcribe(pcm: ShortArray, sampleRate: Int): String {
        check(!closed) { "engine closed" }
        require(pcm.isNotEmpty()) { "no audio" }
        // Recorder already emits 16 kHz; whisper.cpp expects 16 kHz float in [-1, 1].
        // Resampling here keeps the engine correct even if Recorder changes rate.
        val floats = toMonoFloat16k(pcm, sampleRate)
        return nativeTranscribe(handle, floats) ?: ""
    }

    override fun close() {
        if (!closed && handle != 0L) {
            runCatching { nativeFree(handle) }
            handle = 0L
        }
        closed = true
    }

    private external fun nativeInit(path: String): Long
    private external fun nativeTranscribe(handle: Long, samples: FloatArray): String?
    private external fun nativeFree(handle: Long)

    companion object {
        val loadError: String? = try {
            System.loadLibrary("whisper_jni")
            null
        } catch (t: Throwable) {
            t.message ?: t.toString()
        }

        fun toMonoFloat16k(pcm: ShortArray, sampleRate: Int): FloatArray {
            val src = if (sampleRate == 16_000) {
                FloatArray(pcm.size) { i -> (pcm[i] / 32768f).coerceIn(-1f, 1f) }
            } else {
                val ratio = sampleRate / 16_000f
                val outLen = (pcm.size / ratio).toInt().coerceAtLeast(1)
                FloatArray(outLen) { i ->
                    val pos = (i * ratio).toInt().coerceIn(0, pcm.size - 1)
                    (pcm[pos] / 32768f).coerceIn(-1f, 1f)
                }
            }
            return src
        }
    }
}
