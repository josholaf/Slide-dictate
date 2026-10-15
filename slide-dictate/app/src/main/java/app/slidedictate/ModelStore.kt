package app.slidedictate

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class ModelInfo(val id: String, val label: String, val file: String, val url: String, val mb: Int)

/** Whisper models in ggml format (whisper.cpp). Files live in the app's private storage. */
object ModelStore {
    private const val BASE = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/"

    val models = listOf(
        ModelInfo("tiny", "Whisper Tiny (English)", "ggml-tiny.en.bin", BASE + "ggml-tiny.en.bin", 75),
        ModelInfo("base", "Whisper Base (English)", "ggml-base.en.bin", BASE + "ggml-base.en.bin", 142),
        ModelInfo("small", "Whisper Small (English)", "ggml-small.en.bin", BASE + "ggml-small.en.bin", 466),
    )

    private fun dir(c: Context) = File(c.filesDir, "models").apply { mkdirs() }
    fun path(c: Context, m: ModelInfo) = File(dir(c), m.file)
    fun isReady(c: Context, m: ModelInfo) = path(c, m).exists()

    fun delete(c: Context, m: ModelInfo) {
        path(c, m).delete()
        File(dir(c), m.file + ".part").delete()
        if (Prefs.model(c) == m.id) Prefs.clearModel(c)
    }

    /**
     * Blocking. Call from a background thread. Writes to .part, then renames when complete.
     * Resumes an interrupted download with HTTP Range when the server allows it (Hugging Face does),
     * otherwise restarts from scratch.
     */
    fun download(c: Context, m: ModelInfo, onProgress: (Int) -> Unit) {
        val tmp = File(dir(c), m.file + ".part")
        val have = if (tmp.exists()) tmp.length() else 0L
        val conn = URL(m.url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        if (have > 0) conn.setRequestProperty("Range", "bytes=$have-")
        val resumed = conn.responseCode == HttpURLConnection.HTTP_PARTIAL
        if (!resumed) tmp.delete()
        val total = (if (resumed) have else 0L) + conn.contentLengthLong
        conn.inputStream.use { input ->
            java.io.FileOutputStream(tmp, resumed).use { out ->
                val buf = ByteArray(64 * 1024)
                var done = if (resumed) have else 0L
                var last = -1
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    if (total > 0) {
                        val p = (done * 100 / total).toInt()
                        if (p != last) { last = p; onProgress(p) }
                    }
                }
            }
        }
        check(tmp.renameTo(path(c, m))) { "Could not save model file" }
    }
}
