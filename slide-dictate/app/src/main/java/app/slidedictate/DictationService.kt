package app.slidedictate

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
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
    private var online: SpeechRecognizer? = null
    private val chunkQ = LinkedBlockingQueue<ShortArray>()
    private var chunkWorker: Thread? = null
    @Volatile private var chunkRun = false
    @Volatile private var chunksDone = 0

    private var handle: View? = null
    private var panel: View? = null
    private var status: TextView? = null
    private var modeLine: TextView? = null
    private var micBtn: Button? = null
    private var recording = false

    private var engine: Transcriber? = null
    private var engineFile: String? = null

    companion object {
        const val ACTION_HANDLE = "app.slidedictate.HANDLE"
        const val EXTRA_VISIBLE = "visible"
        const val EXTRA_OFFSET_Y = "offset_y"
    }

    private val handleCmd = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action != ACTION_HANDLE) return
            if (i.hasExtra(EXTRA_VISIBLE)) {
                if (i.getBooleanExtra(EXTRA_VISIBLE, true)) showHandle() else hideHandle()
            }
            if (i.hasExtra(EXTRA_OFFSET_Y)) moveHandle(i.getIntExtra(EXTRA_OFFSET_Y, 0))
        }
    }

    override fun onServiceConnected() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(handleCmd, IntentFilter(ACTION_HANDLE), Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(handleCmd, IntentFilter(ACTION_HANDLE))
        }
        if (Prefs.handleVisible(this)) addHandle()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        if (online != null) cancelOnline() else if (recording) runCatching { recorder.stop() }
        stopMicHold()
        runCatching { unregisterReceiver(handleCmd) }
        runCatching { handle?.let { wm.removeView(it) } }
        runCatching { panel?.let { wm.removeView(it) } }
        engine?.close()
        super.onDestroy()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ---- overlay ----------------------------------------------------------

    private fun addHandle() {
        val r = dp(10).toFloat()
        val pill = View(this).apply {
            background = GradientDrawable().apply {
                setColor(0xFF0D6A6A.toInt())
                cornerRadii = floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r)
            }
        }
        val dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
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
            addView(pill, LinearLayout.LayoutParams(dp(22), dp(96)).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
            })
        }
        val lp = WindowManager.LayoutParams(
            dp(64), dp(160),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL; y = dp(Prefs.handleOffsetY(this@DictationService)) }
        wm.addView(dock, lp)
        handle = dock
    }

    private fun showHandle() {
        if (handle == null) {
            addHandle()
            if (panel != null) handle?.visibility = View.GONE
        } else handle?.visibility = View.VISIBLE
    }

    private fun hideHandle() {
        handle?.visibility = View.GONE
    }

    private fun moveHandle(offsetYdp: Int) {
        val h = handle ?: return
        val lp = h.layoutParams as WindowManager.LayoutParams
        lp.y = dp(offsetYdp)
        wm.updateViewLayout(h, lp)
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
        val mic = Button(this).apply {
            text = "Start dictation"
            setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_mic_audio_lines, 0, 0, 0)
            setOnClickListener { toggleRecording() }
        }
        val st = TextView(this).apply { text = "Tap start, speak, tap stop."; setTextColor(ink); textSize = 14f }
        val mode = TextView(this).apply {
            textSize = 12f; setTextColor(0xFF5A6B72.toInt())
            setPadding(0, dp(2), 0, dp(6))
        }
        val offline = Switch(this).apply {
            text = "Offline only"; setTextColor(ink); isChecked = Prefs.offlineOnly(this@DictationService)
            setOnCheckedChangeListener { _, on ->
                Prefs.setOfflineOnly(this@DictationService, on)
                updateModeLine()
                setStatus(if (on) "Offline only." else "Online mode uses Google voice typing.")
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
            addView(mode)
            addView(offline)
        }
        val lp = WindowManager.LayoutParams(
            dp(300), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.END or Gravity.BOTTOM; x = dp(10); y = dp(40) }

        wm.addView(card, lp)
        panel = card; status = st; micBtn = mic; modeLine = mode
        updateModeLine()
        handle?.visibility = View.GONE
    }

    private fun hidePanel() {
        if (online != null) cancelOnline() else if (recording) {
            stopOffline()
            chunkQ.offer(ShortArray(0))
        }
        panel?.let { runCatching { wm.removeView(it) } }
        panel = null; status = null; micBtn = null; modeLine = null
        handle?.visibility = if (Prefs.handleVisible(this)) View.VISIBLE else View.GONE
    }

    private fun setStatus(s: String) { status?.text = s }

    /** Live mode line under the status: which engine and model will be used right now. */
    private fun updateModeLine() {
        val tv = modeLine ?: return
        val m = Prefs.model(this)?.let { id -> ModelStore.models.firstOrNull { it.id == id } }
        tv.text = when {
            !Prefs.offlineOnly(this) -> "Online · Google voice typing"
            m == null -> "Offline · no model selected yet"
            else -> "Offline · " + m.label + " (" + m.mb + " MB)"
        }
    }

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
        if (engineFile != f.name) {
            engine?.close()
            engine = Engines.load(f)
            engineFile = f.name
            val err = Engines.lastLoadError
            main.post {
                setStatus(if (err == null) "Engine: whisper.cpp ready."
                           else "STUB engine - real one failed: " + err)
            }
        }
        return engine!!
    }

    private fun toggleRecording() {
        if (recording) { finishRecording(); return }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            setStatus("Open Slide Dictate and allow the microphone."); return
        }
        if (!Prefs.offlineOnly(this)) { startOnline(); return }
        if (modelFile() == null) { setStatus("Open Slide Dictate, download a model, and tap Use."); return }
        recorder.onChunk = { pcm -> chunkQ.offer(pcm) }
        if (!recorder.start()) { recorder.onChunk = null; setStatus("The microphone is busy or blocked."); return }
        recording = true
        startMicHold()
        micBtn?.text = "Stop"
        setStatus("Listening... pause and text appears as you speak.")
        chunkRun = true
        chunksDone = 0
        chunkWorker = Thread {
            while (chunkRun || chunkQ.isNotEmpty()) {
                val c = try {
                    chunkQ.poll(200, TimeUnit.MILLISECONDS)
                } catch (e: InterruptedException) {
                    null
                } ?: continue
                if (c.isEmpty()) break
                transcribeChunk(c)
            }
        }.also { it.start() }
    }

    private fun transcribeChunk(pcm: ShortArray) {
        val file = modelFile()
        if (file == null) {
            main.post { setStatus("Open Slide Dictate, download a model, and tap Use.") }
            return
        }
        val text = try {
            engineFor(file).transcribe(pcm, Recorder.RATE).trim()
        } catch (t: Throwable) {
            main.post { setStatus("Transcription failed: ${t.message}") }
            return
        }
        chunksDone++
        main.post { insert(text) }
    }

    private fun stopOffline(): ShortArray {
        recording = false
        stopMicHold()
        micBtn?.text = "Start dictation"
        recorder.onChunk = null
        val rest = recorder.stop()
        chunkRun = false
        return rest
    }

    private fun startOnline() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            setStatus("Google voice typing is not on this phone. Turn on Offline only.")
            return
        }
        val sr = runCatching { SpeechRecognizer.createSpeechRecognizer(this) }.getOrNull()
        if (sr == null) { setStatus("Could not start online recognition."); return }
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
            override fun onPartialResults(partial: Bundle?) {
                if (online == null) return
                partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.let { setStatus(it) }
            }
            override fun onResults(results: Bundle?) {
                if (online == null) return
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                cleanupOnline()
                insert(text)
            }
            override fun onError(error: Int) {
                if (online == null) return
                cleanupOnline()
                setStatus("Online recognition failed.")
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
        if (runCatching { sr.startListening(intent) }.isFailure) {
            runCatching { sr.destroy() }
            setStatus("Could not start online recognition."); return
        }
        online = sr
        recording = true
        startMicHold()
        micBtn?.text = "Stop"
        setStatus("Listening online...")
    }

    private fun cleanupOnline() {
        recording = false
        stopMicHold()
        micBtn?.text = "Start dictation"
        runCatching { online?.destroy() }
        online = null
    }

    private fun cancelOnline() {
        runCatching { online?.cancel() }
        cleanupOnline()
    }

    private fun stopOnline() {
        setStatus("Transcribing...")
        runCatching { online?.stopListening() }
    }

    private fun finishRecording() {
        if (online != null) { stopOnline(); return }
        if (!recording) return
        val rest = stopOffline()
        if (Recorder.audible(rest)) {
            setStatus("Transcribing...")
            chunkQ.offer(rest)
        } else if (chunksDone == 0) {
            setStatus("Heard nothing.")
        }
        chunkQ.offer(ShortArray(0))
    }

    /**
     * The focused input node, or — when nothing reports input focus (common in WebViews and
     * some apps) — the first editable node found by scanning the active window. Games with
     * custom canvases still return null and the caller falls back to the clipboard.
     */
    private fun focusedEditable(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null && (focused.isEditable || focused.text != null)) return focused
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            if (n.isEditable) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return focused
    }

    private fun insert(text: String) {
        if (text.isBlank()) { setStatus("Heard nothing."); return }
        Prefs.setLastTranscription(this, text)
        val node = focusedEditable()
        if (node == null) { copy(text); setStatus("No text field is focused. Copied instead."); return }
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

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
