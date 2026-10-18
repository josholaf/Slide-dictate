package app.slidedictate

import java.util.Locale

/**
 * Tiny in-code string table (no XML resources, per repo rules).
 * English is the fallback; fr = French, rw = Kinyarwanda.
 * Kinyarwanda is best-effort — review by a speaker is welcome.
 */
object L {

    private val t: Map<String, Map<String, String>> = mapOf(
        "en" to mapOf(
            "home" to "Home",
            "model" to "Model",
            "history" to "History",
            "settings" to "Settings",
            "tagline" to "Dictation from a handle on the screen edge. Audio stays on the phone.",
            "latest" to "Latest transcription",
            "models" to "Models",
            "appearance" to "Appearance",
            "dark_mode" to "Dark mode",
            "accessibility" to "Accessibility",
            "edge_handle" to "Edge handle",
            "dictation" to "Dictation",
            "about" to "About",
            "show_handle" to "Show handle",
            "handle_hint" to "Up moves the handle toward the top. You can also drag it.",
            "up" to "Up",
            "down" to "Down",
            "side" to "Side",
            "size" to "Size",
            "opacity" to "Opacity",
        ),
        "fr" to mapOf(
            "home" to "Accueil",
            "model" to "Modèle",
            "history" to "Historique",
            "settings" to "Paramètres",
            "tagline" to "Dictée depuis une poignée sur le bord de l'écran. L'audio reste sur le téléphone.",
            "latest" to "Dernière transcription",
            "models" to "Modèles",
            "appearance" to "Apparence",
            "dark_mode" to "Mode sombre",
            "accessibility" to "Accessibilité",
            "edge_handle" to "Poignée latérale",
            "dictation" to "Dictée",
            "about" to "À propos",
            "show_handle" to "Afficher la poignée",
            "handle_hint" to "Haut déplace la poignée vers le haut. Vous pouvez aussi la faire glisser.",
            "up" to "Haut",
            "down" to "Bas",
            "side" to "Côté",
            "size" to "Taille",
            "opacity" to "Opacité",
        ),
        "rw" to mapOf(
            "home" to "Ahabanza",
            "model" to "Modeli",
            "history" to "Amateka",
            "settings" to "Igenamiterere",
            "tagline" to "Kwandika mu kuvuga, uhereye ku kintu ku mpande z'urubariro. Amajwi aguma ku telefoni.",
            "latest" to "Iyandikishwa rya nyuma",
            "models" to "Modeli",
            "appearance" to "Miterere",
            "dark_mode" to "Umwijima",
            "accessibility" to "Kuboneka",
            "edge_handle" to "Ikariso ku mpande",
            "dictation" to "Kwandika mu kuvuga",
            "about" to "Ibyerekeye",
            "show_handle" to "Yerekana ikariso",
            "handle_hint" to "Hejuru biyimura hejuru. Nanone ushobora kuyisunika.",
            "up" to "Hejuru",
            "down" to "Hasi",
            "side" to "Impande",
            "size" to "Ingano",
            "opacity" to "Uko bigaragara",
        ),
    )

    private val lang = Locale.getDefault().language.takeIf { t.containsKey(it) } ?: "en"

    operator fun get(key: String): String = t[lang]?.get(key) ?: t["en"]?.get(key) ?: key
}
