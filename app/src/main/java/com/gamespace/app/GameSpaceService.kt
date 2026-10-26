package com.gamespace.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

class GameSpaceService : AccessibilityService() {

    companion object {
        var instance: GameSpaceService? = null
    }

    lateinit var store: Store
    private lateinit var wm: WindowManager
    private lateinit var extras: Extras
    private val handler = Handler(Looper.getMainLooper())
    private val buttonViews = HashMap<String, View>()
    private var recorder: RecorderView? = null
    private var recordingActionId: String? = null

    private val holdTokens = HashMap<String, Int>()
    private var tokenCounter = 0

    var inGame = false
        private set

    private val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        store = Store(this)
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        extras = Extras(this, store)
        toast("NovaSpace is on")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == "com.android.systemui" || pkg.contains("inputmethod")) return
        val now = store.games.contains(pkg)
        if (now != inGame) {
            inGame = now
            refreshAll()
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        clearButtons()
        removeRecorder()
        try {
            extras.destroy()
        } catch (e: Exception) {
        }
        super.onDestroy()
    }

    // ---------- helpers ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun pill(text: String, color: Int): TextView {
        val t = TextView(this)
        t.text = text
        t.setTextColor(Color.WHITE)
        t.textSize = 13f
        t.typeface = Typeface.DEFAULT_BOLD
        t.setPadding(dp(12), dp(8), dp(12), dp(8))
        val bg = GradientDrawable()
        bg.setColor(color)
        bg.cornerRadius = dp(18).toFloat()
        t.background = bg
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(dp(3), 0, dp(3), 0)
        t.layoutParams = lp
        return t
    }

    // ---------- refresh ----------

    fun refreshAll() {
        clearButtons()
        removeRecorder()
        if (inGame) {
            if (recordingActionId != null) showRecorder() else showButtons()
        }
        extras.refresh(inGame && recordingActionId == null)
    }

    fun startRecording(actionId: String) {
        recordingActionId = actionId
        refreshAll()
    }

    // ---------- lock all / unlock all (used by the side panel) ----------

    fun allButtonsLocked(): Boolean {
        val l = store.loadButtons()
        return l.isNotEmpty() && l.all { it.locked }
    }

    fun setAllLocked(locked: Boolean) {
        val l = store.loadButtons()
        l.forEach { it.locked = locked }
        store.saveButtons(l)
        refreshAll()
    }

    // ---------- floating buttons ----------

    private fun styleButton(v: TextView, b: FloatBtn) {
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(0xDD15171C.toInt())
        if (b.locked) {
            bg.setStroke(dp(2), 0xFF3DDC84.toInt())
        } else {
            bg.setStroke(dp(2), 0xFFFF5A1F.toInt(), dp(5).toFloat(), dp(3).toFloat())
        }
        v.background = bg
    }

    private fun showButtons() {
        val actions = store.loadActions()
        for (b in store.loadButtons()) {
            val a = actions.find { it.id == b.actionId } ?: continue
            val tv = TextView(this)
            tv.text = a.name.take(3).uppercase()
            tv.gravity = Gravity.CENTER
            tv.setTextColor(Color.WHITE)
            tv.textSize = 12f
            tv.typeface = Typeface.DEFAULT_BOLD
            tv.alpha = 0.85f
            styleButton(tv, b)

            val px = dp(b.size)
            val lp = WindowManager.LayoutParams(
                px, px,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                baseFlags,
                PixelFormat.TRANSLUCENT
            )
            lp.gravity = Gravity.TOP or Gravity.START
            lp.x = b.x
            lp.y = b.y
            tv.setOnTouchListener(ButtonTouch(b, a, lp))
            try {
                wm.addView(tv, lp)
                buttonViews[b.id] = tv
            } catch (e: Exception) {
            }
        }
    }

    private fun clearButtons() {
        for (id in holdTokens.keys.toList()) holdTokens[id] = -1
        for (v in buttonViews.values) {
            try {
                wm.removeView(v)
            } catch (e: Exception) {
            }
        }
        buttonViews.clear()
    }

    private fun persist(b: FloatBtn) {
        val list = store.loadButtons()
        val i = list.indexOfFirst { it.id == b.id }
        if (i >= 0) {
            list[i] = b
            store.saveButtons(list)
        }
    }

    inner class ButtonTouch(
        private val b: FloatBtn,
        private val a: TapAction,
        private val lp: WindowManager.LayoutParams
    ) : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var moved = false

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = lp.x
                    startY = lp.y
                    moved = false
                    v.alpha = 1f
                    if (b.locked) {
                        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        startHold(b.id, a)
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!b.locked) {
                        val dx = e.rawX - downX
                        val dy = e.rawY - downY
                        if (moved || abs(dx) > dp(6) || abs(dy) > dp(6)) {
                            moved = true
                            lp.x = startX + dx.toInt()
                            lp.y = startY + dy.toInt()
                            try {
                                wm.updateViewLayout(v, lp)
                            } catch (ex: Exception) {
                            }
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    v.alpha = 0.85f
                    if (b.locked) {
                        stopHold(b.id)
                    } else if (moved) {
                        b.x = lp.x
                        b.y = lp.y
                        persist(b)
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.alpha = 0.85f
                    stopHold(b.id)
                }
            }
            return true
        }
    }

    // ---------- tap action: press and hold ----------

    private fun active(id: String, token: Int) = holdTokens[id] == token

    private fun stopHold(id: String) {
        holdTokens[id] = -1
    }

    private fun startHold(id: String, a: TapAction) {
        if (a.points.isEmpty()) return
        val token = ++tokenCounter
        holdTokens[id] = token
        if (a.sequence) loopSequence(id, a, token) else pressTogether(id, a, token)
    }

    // All points pressed at the same time and kept down while your finger is held.
    private fun pressTogether(id: String, a: TapAction, token: Int) {
        val chunk = a.holdMs.coerceAtLeast(40L)
        val pts = a.points.take(GestureDescription.getMaxStrokeCount())
        val paths = pts.map { p -> Path().apply { moveTo(p.x, p.y) } }
        val strokes = paths.map { GestureDescription.StrokeDescription(it, 0L, chunk, true) }
        sendChunk(id, strokes, paths, chunk, token, false)
    }

    private fun sendChunk(
        id: String,
        strokes: List<GestureDescription.StrokeDescription>,
        paths: List<Path>,
        chunk: Long,
        token: Int,
        last: Boolean
    ) {
        val g = GestureDescription.Builder()
        strokes.forEach { g.addStroke(it) }
        val cb = object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (last) return
                val keep = active(id, token)
                val next = strokes.mapIndexed { i, s -> s.continueStroke(paths[i], 0L, chunk, keep) }
                sendChunk(id, next, paths, chunk, token, !keep)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {}
        }
        dispatchGesture(g.build(), cb, handler)
    }

    // One by one: runs the sequence once, and repeats it while your finger is held.
    private fun loopSequence(id: String, a: TapAction, token: Int) {
        val pts = a.points.take(GestureDescription.getMaxStrokeCount())
        val hold = a.holdMs.coerceAtLeast(1L)
        val gap = a.delayMs.coerceAtLeast(0L)
        val g = GestureDescription.Builder()
        pts.forEachIndexed { i, p ->
            val path = Path()
            path.moveTo(p.x, p.y)
            g.addStroke(GestureDescription.StrokeDescription(path, i * (hold + gap), hold))
        }
        val cb = object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (active(id, token)) loopSequence(id, a, token)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {}
        }
        dispatchGesture(g.build(), cb, handler)
    }

    // ---------- recorder ----------

    private fun showRecorder() {
        val rec = RecorderView(this)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            baseFlags or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        try {
            wm.addView(rec, lp)
            recorder = rec
        } catch (e: Exception) {
        }
    }

    private fun removeRecorder() {
        recorder?.let {
            try {
                wm.removeView(it)
            } catch (e: Exception) {
            }
        }
        recorder = null
    }

    private fun finishRecording(save: Boolean) {
        val id = recordingActionId
        val pts = recorder?.points?.toList() ?: emptyList()
        if (save && id != null) {
            val list = store.loadActions()
            val a = list.find { it.id == id }
            if (a != null) {
                a.points = pts.toMutableList()
                store.saveActions(list)
            }
            toast("Saved ${pts.size} taps")
        }
        recordingActionId = null
        refreshAll()
    }

    inner class RecorderView(ctx: Context) : FrameLayout(ctx) {
        val points = mutableListOf<TapPoint>()
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFF5A1F.toInt() }
        private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = dp(14).toFloat()
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        private val loc = IntArray(2)
        private val counter = pill("REC 0", 0xFF15171C.toInt())
        private val bar = LinearLayout(ctx)
        private var barTop = true

        init {
            setWillNotDraw(false)
            setBackgroundColor(0x33000000)
            bar.orientation = LinearLayout.HORIZONTAL

            val undo = pill("Undo", 0xFF262A31.toInt())
            val clear = pill("Clear", 0xFF262A31.toInt())
            val flip = pill("Move bar", 0xFF262A31.toInt())
            val save = pill("Save", 0xFF1F8F55.toInt())
            val cancel = pill("Cancel", 0xFFB3261E.toInt())

            undo.setOnClickListener {
                if (points.isNotEmpty()) points.removeAt(points.size - 1)
                update()
            }
            clear.setOnClickListener {
                points.clear()
                update()
            }
            flip.setOnClickListener {
                barTop = !barTop
                val lp = bar.layoutParams as LayoutParams
                lp.gravity = (if (barTop) Gravity.TOP else Gravity.BOTTOM) or Gravity.CENTER_HORIZONTAL
                bar.layoutParams = lp
            }
            save.setOnClickListener { finishRecording(true) }
            cancel.setOnClickListener { finishRecording(false) }

            bar.addView(counter)
            bar.addView(undo)
            bar.addView(clear)
            bar.addView(flip)
            bar.addView(save)
            bar.addView(cancel)

            val lp = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            )
            lp.setMargins(0, dp(8), 0, dp(8))
            addView(bar, lp)
        }

        private fun update() {
            counter.text = "REC ${points.size}"
            invalidate()
        }

        override fun onDraw(c: Canvas) {
            super.onDraw(c)
            getLocationOnScreen(loc)
            points.forEachIndexed { i, p ->
                val cx = p.x - loc[0]
                val cy = p.y - loc[1]
                c.drawCircle(cx, cy, dp(18).toFloat(), fill)
                c.drawText((i + 1).toString(), cx, cy + dp(5), txt)
            }
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (e.actionMasked == MotionEvent.ACTION_DOWN) {
                points.add(TapPoint(e.rawX, e.rawY))
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                update()
            }
            return true
        }
    }
}
