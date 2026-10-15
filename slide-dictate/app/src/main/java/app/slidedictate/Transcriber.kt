package app.slidedictate

import java.io.File

/** One finished recording in, text out. Called on a background thread. */
interface Transcriber {
    fun transcribe(pcm: ShortArray, sampleRate: Int): String
    fun close() {}
}

object Engines {
    /** Real whisper.cpp engine when the native lib + model load, stub otherwise. */
    fun load(modelFile: File): Transcriber = try {
        WhisperEngine(modelFile)
    } catch (t: Throwable) {
        StubTranscriber(modelFile.name)
    }
}

/** Placeholder so the whole flow (record, transcribe, insert) can be tested before the engine exists. */
class StubTranscriber(private val model: String) : Transcriber {
    override fun transcribe(pcm: ShortArray, sampleRate: Int): String {
        val seconds = pcm.size / sampleRate.toFloat()
        return "[stub: %.1f s of audio, model %s]".format(seconds, model)
    }
}
