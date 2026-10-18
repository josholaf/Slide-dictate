package app.slidedictate

import android.content.Context

/**
 * Light/dark palette for the code-built UI. No XML themes: every view picks colors
 * from here, and [Prefs.darkMode] decides which palette is used.
 */
object Ui {

    /** Green used for "in use" / "good" states, on both palettes. */
    const val GREEN = 0xFF1E8E3E.toInt()
    const val RED = 0xFFC62828.toInt()

    class Cols(
        val bg: Int,        // window background
        val surface: Int,   // cards, buttons
        val stroke: Int,    // card borders
        val ink: Int,       // primary text
        val sub: Int,       // secondary text
        val accent: Int,    // toolbar selection / progress
        val accentInk: Int, // text on accent
    )

    fun cols(c: Context): Cols =
        if (Prefs.darkMode(c)) Cols(
            bg = 0xFF101820.toInt(),
            surface = 0xFF1B2833.toInt(),
            stroke = 0xFF31424E.toInt(),
            ink = 0xFFE7EEF2.toInt(),
            sub = 0xFF9FB4BF.toInt(),
            accent = 0xFF0D6A6A.toInt(),
            accentInk = 0xFFFFFFFF.toInt(),
        ) else Cols(
            bg = 0xFFF6F9FA.toInt(),
            surface = 0xFFFFFFFF.toInt(),
            stroke = 0xFFCBD7DC.toInt(),
            ink = 0xFF12222B.toInt(),
            sub = 0xFF5A6B72.toInt(),
            accent = 0xFF0D6A6A.toInt(),
            accentInk = 0xFFFFFFFF.toInt(),
        )
}
