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
}
