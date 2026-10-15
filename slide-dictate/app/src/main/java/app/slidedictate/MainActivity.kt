package app.slidedictate

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Setup screen: permissions, accessibility service, model downloads, offline switch. */
class MainActivity : Activity() {

    private val progress = HashMap<String, Int>()
    private lateinit var box: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(32), dp(20), dp(32))
        }
        setContentView(ScrollView(this).apply { addView(box) })
    }

    override fun onResume() { super.onResume(); render() }

    override fun onRequestPermissionsResult(code: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(code, p, r)
        render()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(s: String, size: Float = 16f, bold: Boolean = false, top: Int = 14) =
        TextView(this).apply {
            text = s; textSize = size; setPadding(0, dp(top), 0, 0)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun btn(s: String, click: () -> Unit) = Button(this).apply { text = s; setOnClickListener { click() } }

    private fun serviceEnabled(): Boolean {
        val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return s.split(':').any { it.startsWith("$packageName/") }
    }

    private fun render() {
        box.removeAllViews()
        box.addView(label("Slide Dictate", 26f, true, 0))
        box.addView(label("Dictation from a handle on the screen edge. Audio stays on the phone.", 15f, top = 6))

        val micOk = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        box.addView(label(if (micOk) "Microphone: allowed" else "Microphone: not allowed", top = 24))
        if (!micOk) box.addView(btn("Allow microphone") {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        })

        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val notifOk = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!notifOk) box.addView(btn("Allow notifications (for mic indicator)") {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
            })
        }

        val svc = serviceEnabled()
        box.addView(label(if (svc) "Edge handle: on" else "Edge handle: off. Turn on Slide Dictate under Accessibility."))
        if (!svc) box.addView(btn("Open accessibility settings") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })

        box.addView(Switch(this).apply {
            text = "Offline only"; textSize = 16f; setPadding(0, dp(18), 0, 0)
            isChecked = Prefs.offlineOnly(this@MainActivity)
            setOnCheckedChangeListener { _, on -> Prefs.setOfflineOnly(this@MainActivity, on) }
        })

        box.addView(label("Models", 20f, true, 24))
        for (m in ModelStore.models) {
            val ready = ModelStore.isReady(this, m)
            val p = progress[m.id]
            val inUse = ready && Prefs.model(this) == m.id
            val state = when {
                p != null -> "  downloading $p%"
                inUse -> "  in use"
                ready -> "  ready"
                else -> ""
            }
            box.addView(label("${m.label}, ${m.mb} MB$state"))
            if (p == null && !ready) box.addView(btn("Download") { startDownload(m) })
            if (p == null && ready && !inUse) box.addView(btn("Use") { Prefs.setModel(this, m.id); render() })
        }
    }

    private fun startDownload(m: ModelInfo) {
        progress[m.id] = 0
        render()
        Thread {
            try {
                ModelStore.download(this, m) { p -> runOnUiThread { progress[m.id] = p; render() } }
            } catch (t: Throwable) {
                runOnUiThread { Toast.makeText(this, "Download failed: ${t.message}", Toast.LENGTH_LONG).show() }
            }
            runOnUiThread {
                progress.remove(m.id)
                if (ModelStore.isReady(this, m) && Prefs.model(this) == null) Prefs.setModel(this, m.id)
                render()
            }
        }.start()
    }
}
