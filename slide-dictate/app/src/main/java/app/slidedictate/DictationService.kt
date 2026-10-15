package app.slidedictate

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import java.io.File
import kotlin.math.abs

/**
 * One service does three jobs: draws the edge handle and panel (accessibility overlay,
 * so no "draw over other apps" permission), records audio, and types the result
 * into whichever text field has focus.
 */
class DictationService : AccessibilityService() {

    private lateinit var wm: WindowManager
    private val main = Handler(Looper.getMainLooper())
    private val recorder = Recorder()

    private var handle: View? = null
    private var panel: View? = null
    private var status: TextView? = null
    private var micBtn: Button? = null
    private var recording = false

    private var engine: Transcriber? = null
    private var engineFile: String? = null

    override fun onServiceConnected() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        addHandle()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        if (recording) recorder.stop()
        stopMicHold()
        runCatching { handle?.let { wm.removeView(it) } }
        runCatching { panel?.let { wm.removeView(it) } }
        engine?.close()
        super.onDestroy()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ---- overlay ----------------------------------------------------------

    private fun addHandle() {
        val r = dp(10).toFloat()
        val v = View(this).apply {
            background = GradientDrawable().apply {
                setColor(0xFF0D6A6A.toInt())
                cornerRadii = floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r)
            }
            var downX = 0f
            setOnTouchListener { _, ev ->
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> downX = ev.rawX
                    MotionEvent.ACTION_UP -> {
                        val dx = ev.rawX - downX
                        if (dx < -dp(16) || abs(dx) < dp(6)) showPanel()
                    }
                }
                true
            }
        }
        val lp = WindowManager.LayoutParams(
            dp(22), dp(96),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
        wm.addView(v, lp)
        handle = v
    }

    private fun showPanel() {
        if (panel != null) return
        val ink = 0xFF12222B.toInt()

        val title = TextView(this).apply { text = "Dictate"; textSize = 16f; setTextColor(ink) }
        val close = TextView(this).apply {
            text = "\u00D7"; textSize = 24f; setTextColor(ink); setPadding(dp(12), 0, 0, 0)
            setOnClickListener { hidePanel() }
        }
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(title, LinearLayout.LayoutParams(0, -2, 1f))
            addView(close)
        }
        val mic = Button(this).apply { text = "Start dictation"; setOnClickListener { toggleRecording() } }
        val st = TextView(this).apply { text = "Tap start, speak, tap stop."; setTextColor(ink); textSize = 14f }
        val offline = Switch(this).apply {
            text = "Offline only"; setTextColor(ink); isChecked = Prefs.offlineOnly(this@DictationService)
            setOnCheckedChangeListener { _, on ->
                Prefs.setOfflineOnly(this@DictationService, on)
                setStatus(if (on) "Offline only." else "Online mode is not built yet.")
            }
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                setColor(0xFFFFFFFF.toInt()); cornerRadius = dp(20).toFloat(); setStroke(dp(1), 0xFFCBD7DC.toInt())
            }
            addView(head)
            addView(mic)
            addView(st)
            addView(offline)
        }
        val lp = WindowManager.LayoutParams(
            dp(300), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.END or Gravity.BOTTOM; x = dp(10); y = dp(40) }

        wm.addView(card, lp)
        panel = card; status = st; micBtn = mic
        handle?.visibility = View.GONE
    }

    private fun hidePanel() {
        if (recording) { recorder.stop(); recording = false; stopMicHold() }
        panel?.let { runCatching { wm.removeView(it) } }
        panel = null; status = null; micBtn = null
        handle?.visibility = View.VISIBLE
    }

    private fun setStatus(s: String) { status?.text = s }

    // ---- dictation --------------------------------------------------------

    private fun startMicHold() {
        runCatching {
            val i = Intent(this, MicForegroundService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        }
    }

    private fun stopMicHold() {
        runCatching { stopService(Intent(this, MicForegroundService::class.java)) }
    }

    private fun modelFile(): File? {
        val id = Prefs.model(this) ?: return null
        val m = ModelStore.models.firstOrNull { it.id == id } ?: return null
        return ModelStore.path(this, m).takeIf { it.exists() }
    }

    private fun engineFor(f: File): Transcriber {
        if (engineFile != f.name) { engine?.close(); engine = Engines.load(f); engineFile = f.name }
        return engine!!
    }

    private fun toggleRecording() {
        if (recording) { finishRecording(); return }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            setStatus("Open Slide Dictate and allow the microphone."); return
        }
        if (!Prefs.offlineOnly(this)) { setStatus("Online mode is not built yet. Turn on Offline only."); return }
        if (modelFile() == null) { setStatus("Open Slide Dictate, download a model, and tap Use."); return }
        if (!recorder.start()) { setStatus("The microphone is busy or blocked."); return }
        recording = true
        startMicHold()
        micBtn?.text = "Stop"
        setStatus("Listening...")
    }

    private fun finishRecording() {
        recording = false
        stopMicHold()
        micBtn?.text = "Start dictation"
        val pcm = recorder.stop()
        val file = modelFile() ?: return
        setStatus("Transcribing...")
        Thread {
            val text = try {
                engineFor(file).transcribe(pcm, Recorder.RATE).trim()
            } catch (t: Throwable) {
                main.post { setStatus("Transcription failed: ${t.message}") }
                return@Thread
            }
            main.post { insert(text) }
        }.start()
    }

    private fun insert(text: String) {
        if (text.isBlank()) { setStatus("Heard nothing."); return }
        val node = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (node == null) { copy(text); setStatus("No text field is focused. Copied instead."); return }

        val cur = if (node.isShowingHintText) "" else node.text?.toString().orEmpty()
        val s = node.textSelectionStart.let { if (it < 0) cur.length else it.coerceAtMost(cur.length) }
        val e = node.textSelectionEnd.let { if (it < 0) s else it.coerceIn(s, cur.length) }
        val lead = if (s > 0 && !cur[s - 1].isWhitespace()) " " else ""
        val added = lead + text
        val out = cur.substring(0, s) + added + cur.substring(e)

        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, out)
        }
        if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            val pos = s + added.length
            val sel = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, pos)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, pos)
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, sel)
            setStatus("Inserted.")
        } else {
            copy(text)
            val pasted = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            setStatus(if (pasted) "Inserted by paste." else "Could not type here. Text is on the clipboard.")
        }
    }

    private fun copy(text: String) {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("dictation", text))
    }
}
