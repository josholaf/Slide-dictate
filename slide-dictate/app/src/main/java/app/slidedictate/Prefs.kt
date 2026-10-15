package app.slidedictate

import android.content.Context

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    fun offlineOnly(c: Context) = sp(c).getBoolean("offline", true)
    fun setOfflineOnly(c: Context, v: Boolean) = sp(c).edit().putBoolean("offline", v).apply()

    fun model(c: Context): String? = sp(c).getString("model", null)
    fun setModel(c: Context, id: String) = sp(c).edit().putString("model", id).apply()
}
