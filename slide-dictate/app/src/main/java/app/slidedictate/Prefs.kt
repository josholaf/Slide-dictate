package app.slidedictate

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object Prefs {
    private const val HISTORY_CAP = 200
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

    /**
     * Dictation history, most recent last. Stored as a JSON array of
     * {"t": text, "ts": epoch millis}, capped at [HISTORY_CAP] entries.
     */
    fun history(c: Context): List<Pair<String, Long>> {
        val a = runCatching { JSONArray(sp(c).getString("history", "[]")) }.getOrElse { JSONArray() }
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            o.getString("t") to o.getLong("ts")
        }
    }

    fun addHistory(c: Context, text: String, time: Long = System.currentTimeMillis()) {
        if (text.isBlank()) return
        val a = runCatching { JSONArray(sp(c).getString("history", "[]")) }.getOrElse { JSONArray() }
        a.put(JSONObject().put("t", text).put("ts", time))
        while (a.length() > HISTORY_CAP) a.remove(0)
        sp(c).edit().putString("history", a.toString()).apply()
    }

    fun setHistory(c: Context, items: List<Pair<String, Long>>) {
        val a = JSONArray()
        for ((t, ts) in items) a.put(JSONObject().put("t", t).put("ts", ts))
        sp(c).edit().putString("history", a.toString()).apply()
    }

    fun clearHistory(c: Context) = sp(c).edit().remove("history").apply()

    /** Screen edge the handle sits on: "left" or "right". */
    fun handleSide(c: Context) = sp(c).getString("handle_side", "right") ?: "right"
    fun setHandleSide(c: Context, v: String) = sp(c).edit().putString("handle_side", v).apply()

    /** Handle size in percent (80 small, 100 normal, 140 large). */
    fun handleScale(c: Context) = sp(c).getInt("handle_scale", 100)
    fun setHandleScale(c: Context, v: Int) = sp(c).edit().putInt("handle_scale", v).apply()

    /** Handle opacity in percent (60, 80 or 100). */
    fun handleOpacity(c: Context) = sp(c).getInt("handle_opacity", 100)
    fun setHandleOpacity(c: Context, v: Int) = sp(c).edit().putInt("handle_opacity", v).apply()

}
