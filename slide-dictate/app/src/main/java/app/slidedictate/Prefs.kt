package app.slidedictate

import android.content.Context

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    fun offlineOnly(c: Context) = sp(c).getBoolean("offline", true)
    fun setOfflineOnly(c: Context, v: Boolean) = sp(c).edit().putBoolean("offline", v).apply()

    fun model(c: Context): String? = sp(c).getString("model", null)?.ifEmpty { null }
    fun setModel(c: Context, id: String) = sp(c).edit().putString("model", id).apply()
    fun clearModel(c: Context) = sp(c).edit().remove("model").apply()

    fun handleOffsetY(c: Context) = sp(c).getInt("handle_y", 0)
    fun setHandleOffsetY(c: Context, v: Int) = sp(c).edit().putInt("handle_y", v).apply()

    fun handleVisible(c: Context) = sp(c).getBoolean("handle_visible", true)
    fun setHandleVisible(c: Context, v: Boolean) = sp(c).edit().putBoolean("handle_visible", v).apply()

    fun darkMode(c: Context) = sp(c).getBoolean("dark_mode", false)
    fun setDarkMode(c: Context, v: Boolean) = sp(c).edit().putBoolean("dark_mode", v).apply()

    /** Last text the dictation service inserted, shown on the Home tab. */
    fun lastTranscription(c: Context): String? = sp(c).getString("last_text", null)
    fun setLastTranscription(c: Context, t: String) = sp(c).edit().putString("last_text", t).apply()

    /** Whisper punctuation on the inserted text; when off, sentence punctuation is stripped. */
    fun autoPunctuation(c: Context) = sp(c).getBoolean("auto_punctuation", true)
    fun setAutoPunctuation(c: Context, v: Boolean) = sp(c).edit().putBoolean("auto_punctuation", v).apply()

    /**
     * Whisper language code for multilingual models ("auto" = detect).
     * English-only models ignore this.
     */
    fun language(c: Context) = sp(c).getString("language", "auto") ?: "auto"
    fun setLanguage(c: Context, v: String) = sp(c).edit().putString("language", v).apply()
}
