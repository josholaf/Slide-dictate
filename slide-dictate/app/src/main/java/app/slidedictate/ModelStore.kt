package app.slidedictate

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class ModelInfo(
    val id: String,
    val label: String,
    val file: String,
    val url: String,
    val mb: Int,
    /** SHA-256 of the complete file (Hugging Face LFS oid). Corrupt downloads are rejected. */
    val sha256: String,
)

/** Whisper models in ggml format (whisper.cpp). Files live in the app's private storage. */
object ModelStore {
    private const val BASE = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/"

    val models = listOf(
        ModelInfo("tiny", "Whisper Tiny (English)", "ggml-tiny.en.bin", BASE + "ggml-tiny.en.bin", 75,
            "921e4cf8686fdd993dcd081a5da5b6c365bfde1162e72b08d75ac75289920b1f"),
        ModelInfo("base", "Whisper Base (English)", "ggml-base.en.bin", BASE + "ggml-base.en.bin", 142,
            "a03779c86df3323075f5e796cb2ce5029f00ec8869eee3fdfb897afe36c6d002"),
        ModelInfo("small", "Whisper Small (English)", "ggml-small.en.bin", BASE + "ggml-small.en.bin", 466,
            "c6138d6d58ecc8322097e0f987c32f1be8bb0a18532a3f88f734d1bbf9c41e5d"),
        ModelInfo("tiny-multi", "Whisper Tiny (multilingual)", "ggml-tiny.bin", BASE + "ggml-tiny.bin", 75,
            "be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21"),
        ModelInfo("base-multi", "Whisper Base (multilingual)", "ggml-base.bin", BASE + "ggml-base.bin", 142,
            "60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe"),
        ModelInfo("small-multi", "Whisper Small (multilingual)", "ggml-small.bin", BASE + "ggml-small.bin", 466,
            "1be3a9b2063867b937e64e2ec7483364a79917e157fa98c5d94b5c1fffea987b"),
        // Quantized q5_1 variants: ~40% smaller downloads, slightly lower accuracy.
        ModelInfo("tiny.en-q5_1", "Whisper Tiny (English, q5_1)", "ggml-tiny.en-q5_1.bin", BASE + "ggml-tiny.en-q5_1.bin", 31,
            "c77c5766f1cef09b6b7d47f21b546cbddd4157886b3b5d6d4f709e91e66c7c2b"),
        ModelInfo("base.en-q5_1", "Whisper Base (English, q5_1)", "ggml-base.en-q5_1.bin", BASE + "ggml-base.en-q5_1.bin", 57,
            "4baf70dd0d7c4247ba2b81fafd9c01005ac77c2f9ef064e00dcf195d0e2fdd2f"),
        ModelInfo("small.en-q5_1", "Whisper Small (English, q5_1)", "ggml-small.en-q5_1.bin", BASE + "ggml-small.en-q5_1.bin", 181,
            "bfdff4894dcb76bbf647d56263ea2a96645423f1669176f4844a1bf8e478ad30"),
        ModelInfo("tiny-q5_1", "Whisper Tiny (multilingual, q5_1)", "ggml-tiny-q5_1.bin", BASE + "ggml-tiny-q5_1.bin", 31,
            "818710568da3ca15689e31a743197b520007872ff9576237bda97bd1b469c3d7"),
        ModelInfo("base-q5_1", "Whisper Base (multilingual, q5_1)", "ggml-base-q5_1.bin", BASE + "ggml-base-q5_1.bin", 57,
            "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898"),
        ModelInfo("small-q5_1", "Whisper Small (multilingual, q5_1)", "ggml-small-q5_1.bin", BASE + "ggml-small-q5_1.bin", 181,
            "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb"),
        // Larger models for much better accuracy (need a powerful phone; Medium is 1.4 GB).
        // large-v3-turbo deliberately NOT listed: needs whisper.cpp >= 1.7 (vendored copy is 1.5.4).
        ModelInfo("medium.en", "Whisper Medium (English)", "ggml-medium.en.bin", BASE + "ggml-medium.en.bin", 1463,
            "cc37e93478338ec7700281a7ac30a10128929eb8f427dda2e865faa8f6da4356"),
        ModelInfo("medium.en-q5_0", "Whisper Medium (English, q5_0)", "ggml-medium.en-q5_0.bin", BASE + "ggml-medium.en-q5_0.bin", 514,
            "76733e26ad8fe1c7a5bf7531a9d41917b2adc0f20f2e4f5531688a8c6cd88eb0"),
        ModelInfo("medium-multi", "Whisper Medium (multilingual)", "ggml-medium.bin", BASE + "ggml-medium.bin", 1463,
            "6c14d5adee5f86394037b4e4e8b59f1673b6cee10e3cf0b11bbdbee79c156208"),
        ModelInfo("medium-multi-q5_0", "Whisper Medium (multilingual, q5_0)", "ggml-medium-q5_0.bin", BASE + "ggml-medium-q5_0.bin", 514,
            "19fea4b380c3a618ec4723c3eef2eb785ffba0d0538cf43f8f235e7b3b34220f"),
    )

    private fun dir(c: Context) = File(c.filesDir, "models").apply { mkdirs() }
    fun path(c: Context, m: ModelInfo) = File(dir(c), m.file)
    fun isReady(c: Context, m: ModelInfo) = path(c, m).exists()

    /** Leftover partial download, if any. */
    fun partFile(c: Context, m: ModelInfo) = File(dir(c), m.file + ".part")

    /** Whole MB kept in the partial file, or null when there is none. */
    fun partMb(c: Context, m: ModelInfo): Long? {
        val f = partFile(c, m).takeIf { it.exists() && it.length() > 0 } ?: return null
        return f.length() / 1024 / 1024
    }

    /** Delete the partial file so the next download starts from scratch. */
    fun discardPart(c: Context, m: ModelInfo) {
        partFile(c, m).delete()
    }

    fun delete(c: Context, m: ModelInfo) {
        path(c, m).delete()
        partFile(c, m).delete()
        if (Prefs.model(c) == m.id) Prefs.clearModel(c)
    }

    /** SHA-256 of a file as lowercase hex. Blocking: never call on the UI thread for model files. */
    fun sha256Of(f: File): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(256 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** True when the downloaded model exists and matches its checksum. Blocking, see [sha256Of]. */
    fun verify(c: Context, m: ModelInfo): Boolean =
        path(c, m).takeIf { it.exists() }?.let {
            runCatching { sha256Of(it).equals(m.sha256, ignoreCase = true) }.getOrDefault(false)
        } == true

    /**
     * Blocking. Call from a background thread. Writes to .part, then renames when complete.
     * Resumes an interrupted download with HTTP Range when the server allows it (Hugging Face does),
     * otherwise restarts from scratch.
     */
    fun download(c: Context, m: ModelInfo, onProgress: (Int) -> Unit) {
        val tmp = partFile(c, m)
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
        if (!verify(c, m)) {
            path(c, m).delete()
            throw IllegalStateException("Checksum mismatch for ${m.file}. Deleted, please retry.")
        }
    }
}
