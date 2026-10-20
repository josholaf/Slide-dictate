package app.slidedictate

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.text.DateFormat
import java.util.Date

/**
 * App screen with a toolbar of three tabs:
 *  - Home: setup status + the latest transcription.
 *  - Model: available models; the one in use is highlighted in green.
 *  - Settings: dark mode, accessibility (microphone, service, handle), about.
 */
class MainActivity : Activity() {

    private enum class Tab { HOME, MODEL, HISTORY, SETTINGS }

    private var tab = Tab.HOME
    private val progress = HashMap<String, Int>()
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() { super.onResume(); render() }

    override fun onRequestPermissionsResult(code: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(code, p, r)
        render()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun serviceEnabled(): Boolean {
        val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return s.split(':').any { it.startsWith("$packageName/") }
    }

    // ---- building blocks ---------------------------------------------------

    private fun heading(c: Ui.Cols, s: String) = TextView(this).apply {
        text = s; textSize = 20f; setTextColor(c.ink); setPadding(0, dp(24), 0, 0)
        setTypeface(typeface, Typeface.BOLD)
    }

    private fun txt(c: Ui.Cols, s: String, size: Float = 15f, top: Int = 8) = TextView(this).apply {
        text = s; textSize = size; setTextColor(c.sub); setPadding(0, dp(top), 0, 0)
    }

    private fun btn(c: Ui.Cols, s: String, click: () -> Unit) = Button(this).apply {
        text = s; textSize = 14f; setTextColor(c.ink)
        isAllCaps = false
        setPadding(dp(16), 0, dp(16), 0)
        background = GradientDrawable().apply {
            setColor(c.surface); cornerRadius = dp(12).toFloat(); setStroke(dp(1), c.stroke)
        }
        setOnClickListener { click() }
    }

    /** Rounded surface card, added to [content] with a top margin. */
    private fun card(c: Ui.Cols) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = GradientDrawable().apply {
            setColor(c.surface); cornerRadius = dp(16).toFloat(); setStroke(dp(1), c.stroke)
        }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(12)
        content.addView(this, lp)
    }

    private fun addBtn(parent: LinearLayout, b: Button) {
        parent.addView(b, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
    }

    private fun sw(c: Ui.Cols, s: String, checked: Boolean, change: (Boolean) -> Unit) = Switch(this).apply {
        text = s; textSize = 16f; setTextColor(c.ink); isChecked = checked
        setOnCheckedChangeListener { _, on -> change(on) }
    }

    /** Green when things are fine, red when they need attention. */
    private fun statusLine(ok: Boolean, good: String, bad: String) = TextView(this).apply {
        text = "●  " + (if (ok) good else bad)
        setTextColor(if (ok) Ui.GREEN else Ui.RED); textSize = 15f
        setPadding(0, dp(4), 0, dp(4))
    }

    private fun chip(label: String, bg: Int, fg: Int) = TextView(this).apply {
        text = label; textSize = 12f; setTextColor(fg)
        setPadding(dp(10), dp(4), dp(10), dp(4))
        background = GradientDrawable().apply { setColor(bg); cornerRadius = dp(999).toFloat() }
    }

    // ---- render ------------------------------------------------------------

    private fun render() {
        val c = Ui.cols(this)
        window.statusBarColor = c.bg
        root.setBackgroundColor(c.bg)
        root.setPadding(dp(20), dp(32), dp(20), dp(32))
        root.removeAllViews()

        root.addView(TextView(this).apply {
            text = "Slide Dictate"; textSize = 26f; setTextColor(c.ink)
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = L["tagline"]
            textSize = 15f; setTextColor(c.sub); setPadding(0, dp(6), 0, 0)
        })

        // toolbar: Home | Model | Settings
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(20), 0, 0) }
        fun tabBtn(t: Tab, label: String) = TextView(this).apply {
            text = label; textSize = 15f; gravity = Gravity.CENTER
            setTextColor(if (tab == t) c.accentInk else c.ink)
            setPadding(dp(4), dp(12), dp(4), dp(12))
            background = GradientDrawable().apply {
                setColor(if (tab == t) c.accent else c.surface)
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), if (tab == t) c.accent else c.stroke)
            }
            setOnClickListener { tab = t; render() }
        }
        bar.addView(tabBtn(Tab.HOME, L["home"]), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(tabBtn(Tab.MODEL, L["model"]), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(8), 0, dp(8), 0) })
        bar.addView(tabBtn(Tab.HISTORY, L["history"]), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(0), 0, dp(8), 0) })
        bar.addView(tabBtn(Tab.SETTINGS, L["settings"]), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(bar)

        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(content)
        when (tab) {
            Tab.HOME -> renderHome(c)
            Tab.MODEL -> renderModels(c)
            Tab.HISTORY -> renderHistory(c)
            Tab.SETTINGS -> renderSettings(c)
        }
    }

    // ---- Home tab ------------------------------------------------------------

    private fun renderHome(c: Ui.Cols) {
        val micOk = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val svc = serviceEnabled()
        val model = Prefs.model(this)?.let { id -> ModelStore.models.firstOrNull { it.id == id } }
        val modelReady = model != null && ModelStore.isReady(this, model)

        content.addView(heading(c, L["home"]))
        val st = card(c)
        st.addView(statusLine(micOk, "Microphone allowed", "Microphone not allowed"))
        st.addView(statusLine(svc, "Edge handle service on", "Edge handle service off"))
        st.addView(statusLine(modelReady,
            "Model ready: " + (model?.label ?: ""),
            "No model ready — download one in the Model tab"))
        if (!micOk) addBtn(st, btn(c, "Allow microphone") {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        })
        if (!svc) addBtn(st, btn(c, "Open accessibility settings") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })

        content.addView(heading(c, L["latest"]))
        val t = card(c)
        val last = Prefs.lastTranscription(this)
        t.addView(TextView(this).apply {
            text = last ?: "Nothing yet. Dictate from the edge handle and the text will appear here."
            textSize = 15f; setTextColor(if (last == null) c.sub else c.ink)
        })
    }

    // ---- Model tab --------------------------------------------------------------

    private fun renderModels(c: Ui.Cols) {
        content.addView(heading(c, L["models"]))
        content.addView(txt(c, "The model in use is highlighted in green.", 13f, 2))

        for (m in ModelStore.models) {
            val ready = ModelStore.isReady(this, m)
            val p = progress[m.id]
            val inUse = ready && Prefs.model(this) == m.id
            val kept = if (!ready && p == null) ModelStore.partMb(this, m) else null

            val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            head.addView(TextView(this).apply {
                text = m.label + " · " + m.mb + " MB"
                textSize = 15f; setTextColor(c.ink)
                setTypeface(typeface, Typeface.BOLD)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            head.addView(when {
                inUse -> chip("In use", Ui.GREEN, 0xFFFFFFFF.toInt())
                p != null -> chip("Downloading " + p + "%", c.accent, 0xFFFFFFFF.toInt())
                ready -> chip("Ready", c.stroke, c.ink)
                else -> chip("Not downloaded", c.stroke, c.sub)
            })

            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
            fun add(b: Button, left: Int = 0) {
                actions.addView(b, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(left) })
            }
            if (p == null && kept != null) {
                add(btn(c, "Resume") { startDownload(m) })
                add(btn(c, "Discard + redownload") { ModelStore.discardPart(this, m); startDownload(m) }, 8)
            } else if (p == null && !ready) {
                add(btn(c, "Download") { startDownload(m) })
            }
            if (p == null && ready && !inUse) add(btn(c, "Use") { Prefs.setModel(this, m.id); render() }, 8)
            if (p == null && ready) add(btn(c, "Delete") {
                Thread { ModelStore.delete(this, m); runOnUiThread { render() } }.start()
            }, 8)

            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = GradientDrawable().apply {
                    setColor(c.surface); cornerRadius = dp(16).toFloat()
                    setStroke(if (inUse) dp(2) else dp(1), if (inUse) Ui.GREEN else c.stroke)
                }
            }
            item.addView(head)
            if (kept != null) item.addView(txt(c, "Incomplete download, " + kept + " MB kept", 13f, 6))
            if (actions.childCount > 0) item.addView(actions)
            content.addView(item, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
        }
    }

    // ---- History tab -----------------------------------------------------------

    private fun renderHistory(c: Ui.Cols) {
        content.addView(heading(c, L["history"]))
        val items = Prefs.history(this)

        if (items.isEmpty()) {
            content.addView(txt(c, "Nothing yet. Every dictation is kept here.", 15f, 12))
            return
        }

        val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(12), 0, 0) }
        top.addView(btn(c, "Share all") { shareText(items.joinToString("\n\n") { it.first }) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        top.addView(btn(c, "Clear all") { Prefs.clearHistory(this); render() },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(8) })
        content.addView(top)

        // Newest first.
        for ((index, pair) in items.asReversed().withIndex()) {
            val (entryText, time) = pair
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = GradientDrawable().apply {
                    setColor(c.surface); cornerRadius = dp(16).toFloat(); setStroke(dp(1), c.stroke)
                }
            }
            val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            head.addView(TextView(this).apply {
                text = fmt.format(Date(time)); textSize = 12f; setTextColor(c.sub)
                setTypeface(typeface, Typeface.BOLD)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            head.addView(TextView(this).apply {
                text = "✕"; textSize = 16f; setTextColor(c.sub); setPadding(dp(12), 0, 0, 0)
                setOnClickListener {
                    Prefs.setHistory(this@MainActivity, items.toMutableList().also { it.removeAt(items.size - 1 - index) })
                    render()
                }
            })
            item.addView(head)
            item.addView(TextView(this).apply {
                text = entryText; textSize = 15f; setTextColor(c.ink); setPadding(0, dp(6), 0, 0)
            })
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, 0) }
            row.addView(btn(c, "Copy") { copyText(entryText) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            row.addView(btn(c, "Share") { shareText(entryText) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(8) })
            item.addView(row)

            content.addView(item, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
        }
    }

    private fun copyText(t: String) {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("dictation", t))
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
    }

    private fun shareText(t: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, t)
        }
        runCatching { startActivity(Intent.createChooser(send, "Share dictation")) }
    }

    // ---- Settings tab -------------------------------------------------------------

    private fun renderSettings(c: Ui.Cols) {
        content.addView(heading(c, L["appearance"]))
        val look = card(c)
        look.addView(sw(c, L["dark_mode"], Prefs.darkMode(this)) { on ->
            Prefs.setDarkMode(this, on)
            sendHandle(rebuild = true) // edge handle + panel follow the new palette
            render()
        })

        content.addView(heading(c, L["accessibility"]))
        val acc = card(c)
        val micOk = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        acc.addView(statusLine(micOk, "Microphone allowed", "Microphone not allowed"))
        if (!micOk) addBtn(acc, btn(c, "Allow microphone") {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        })
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val notifOk = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            acc.addView(statusLine(notifOk, "Notifications allowed (mic indicator)", "Notifications not allowed (mic indicator)"))
            if (!notifOk) addBtn(acc, btn(c, "Allow notifications") {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
            })
        }
        val svc = serviceEnabled()
        acc.addView(statusLine(svc, "Edge handle service on", "Edge handle service off"))
        if (!svc) addBtn(acc, btn(c, "Open accessibility settings") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        if (Build.VERSION.SDK_INT >= 34) {
            val muted = getSystemService(AudioManager::class.java).isMicrophoneMute
            acc.addView(statusLine(!muted,
                "Microphone live (Android 14+ foreground capture kept allowed by the recording notification)",
                "Microphone is muted — unmute the phone to dictate"))
        }

        // Battery optimization silently kills long dictations on many phones.
        val pm = getSystemService(PowerManager::class.java)
        val battOk = runCatching { pm.isIgnoringBatteryOptimizations(packageName) }.getOrDefault(false)
        acc.addView(statusLine(battOk,
            "Battery optimization ignored",
            "Battery optimization may stop dictation early"))
        if (!battOk) addBtn(acc, btn(c, "Ignore battery optimizations") {
            runCatching {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + packageName)))
            }
        })

        content.addView(heading(c, L["edge_handle"]))
        val h = card(c)
        h.addView(sw(c, L["show_handle"], Prefs.handleVisible(this)) { on ->
            Prefs.setHandleVisible(this, on); sendHandle(visible = on)
        })
        h.addView(txt(c, L["handle_hint"], 13f, 4))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, 0) }
        row.addView(btn(c, L["up"]) { nudgeHandle(-40) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(btn(c, L["down"]) { nudgeHandle(40) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(8) })
        h.addView(row)

        // Side: left / right edge.
        val side = Prefs.handleSide(this)
        addBtn(h, btn(c, L["side"] + ": " + (if (side == "right") "Right" else "Left")) {
            Prefs.setHandleSide(this, if (side == "right") "left" else "right")
            sendHandle(rebuild = true); render()
        })
        // Size: small / normal / large.
        val sizes = listOf(80 to "Small", 100 to "Normal", 140 to "Large")
        val cur = Prefs.handleScale(this)
        val sizeName = sizes.firstOrNull { it.first == cur }?.second ?: "Normal"
        addBtn(h, btn(c, L["size"] + ": " + sizeName) {
            val idx = sizes.indexOfFirst { it.first == cur }
            Prefs.setHandleScale(this, sizes[(idx + 1) % sizes.size].first)
            sendHandle(rebuild = true); render()
        })
        // Opacity: 60 / 80 / 100 %.
        val opacities = listOf(60, 80, 100)
        val curOp = Prefs.handleOpacity(this)
        addBtn(h, btn(c, L["opacity"] + ": " + (if (opacities.contains(curOp)) curOp else 100) + "%") {
            Prefs.setHandleOpacity(this, opacities[(opacities.indexOf(curOp) + 1).mod(opacities.size)])
            sendHandle(rebuild = true); render()
        })

        content.addView(heading(c, L["dictation"]))
        val d = card(c)
        d.addView(sw(c, "Offline only", Prefs.offlineOnly(this)) { on -> Prefs.setOfflineOnly(this, on) })
        d.addView(sw(c, "Auto punctuation", Prefs.autoPunctuation(this)) { on ->
            Prefs.setAutoPunctuation(this, on)
        })
        d.addView(txt(c, "Voice commands: say \"new paragraph\", \"new line\" or \"scratch that\".", 13f, 8))

        val langs = listOf("auto", "en", "fr", "es", "sw")
        val langNames = mapOf("auto" to "Auto detect", "en" to "English", "fr" to "French", "es" to "Spanish", "sw" to "Swahili")
        d.addView(btn(c, "Language: " + (langNames[Prefs.language(this)] ?: Prefs.language(this))) {
            val next = langs[(langs.indexOf(Prefs.language(this)) + 1) % langs.size]
            Prefs.setLanguage(this, next)
            render()
        })
        d.addView(txt(c, "Only applies to multilingual models.", 13f, 4))

        content.addView(heading(c, L["about"]))
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "?"
        val a = card(c)
        a.addView(TextView(this).apply {
            text = "Slide Dictate " + version; textSize = 16f; setTextColor(c.ink)
            setTypeface(typeface, Typeface.BOLD)
        })
        a.addView(txt(c, "Built by josh dev (josholaf)", 14f, 6))
        a.addView(txt(c, "Offline speech-to-text with whisper.cpp. Audio never leaves the phone.", 13f, 4))
    }

    // ---- actions ------------------------------------------------------------

    private fun sendHandle(visible: Boolean? = null, offsetYdp: Int? = null, rebuild: Boolean = false) {
        sendBroadcast(Intent(DictationService.ACTION_HANDLE).apply {
            setPackage(packageName)
            if (visible != null) putExtra(DictationService.EXTRA_VISIBLE, visible)
            if (offsetYdp != null) putExtra(DictationService.EXTRA_OFFSET_Y, offsetYdp)
            if (rebuild) putExtra(DictationService.EXTRA_REBUILD, true)
        })
    }

    private fun nudgeHandle(d: Int) {
        val v = (Prefs.handleOffsetY(this) + d).coerceIn(-320, 320)
        Prefs.setHandleOffsetY(this, v)
        sendHandle(offsetYdp = v)
    }

    private fun startDownload(m: ModelInfo) {
        progress[m.id] = 0
        render()
        Thread {
            try {
                ModelStore.download(this, m) { p -> runOnUiThread { progress[m.id] = p; render() } }
            } catch (t: Throwable) {
                runOnUiThread { Toast.makeText(this, "Download failed: " + t.message, Toast.LENGTH_LONG).show() }
            }
            runOnUiThread {
                progress.remove(m.id)
                if (ModelStore.isReady(this, m) && Prefs.model(this) == null) Prefs.setModel(this, m.id)
                render()
            }
        }.start()
    }
}
