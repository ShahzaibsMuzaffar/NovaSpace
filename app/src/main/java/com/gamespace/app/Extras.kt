package com.gamespace.app

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.min

class CrosshairView(ctx: Context, private val store: Store) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val d = resources.displayMetrics.density
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - 2f * d
        if (r <= 0f) return
        val alpha = store.crossAlpha.coerceIn(10, 100) * 255 / 100

        paint.strokeWidth = 2f * d
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = store.crossColor
        paint.alpha = alpha
        paint.style = Paint.Style.STROKE

        when (store.crossShape) {
            0 -> {
                paint.style = Paint.Style.FILL
                c.drawCircle(cx, cy, 3.5f * d, paint)
            }
            1 -> {
                c.drawLine(cx - r, cy, cx + r, cy, paint)
                c.drawLine(cx, cy - r, cx, cy + r, paint)
            }
            2 -> {
                c.drawCircle(cx, cy, r, paint)
                paint.style = Paint.Style.FILL
                c.drawCircle(cx, cy, 2f * d, paint)
            }
            3 -> {
                val gap = r * 0.2f
                c.drawLine(cx - r, cy, cx - gap, cy, paint)
                c.drawLine(cx + gap, cy, cx + r, cy, paint)
                c.drawLine(cx, cy - r, cx, cy - gap, paint)
                c.drawLine(cx, cy + gap, cx, cy + r, paint)
                paint.style = Paint.Style.FILL
                c.drawCircle(cx, cy, 1.5f * d, paint)
            }
            else -> {
                val k = r * 0.75f
                c.drawLine(cx - k, cy - k, cx + k, cy + k, paint)
                c.drawLine(cx - k, cy + k, cx + k, cy - k, paint)
            }
        }
    }
}

class Extras(private val ctx: Context, private val store: Store) {

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var handle: View? = null
    private var panel: View? = null
    private var cross: View? = null
    private var fps: TextView? = null
    private val syncs = mutableListOf<() -> Unit>()

    private val orange = 0xFFFF5A1F.toInt()
    private val green = 0xFF3DDC84.toInt()
    private val grey = 0xFF9AA0A6.toInt()

    private val passFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    private val touchFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    private fun params(w: Int, h: Int, flags: Int) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        flags,
        PixelFormat.TRANSLUCENT
    )

    private fun add(v: View, lp: WindowManager.LayoutParams): Boolean {
        return try {
            wm.addView(v, lp)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun remove(v: View?) {
        if (v == null) return
        try {
            wm.removeView(v)
        } catch (e: Exception) {
        }
    }

    private fun text(s: String, size: Float, color: Int, bold: Boolean): TextView {
        val t = TextView(ctx)
        t.text = s
        t.textSize = size
        t.setTextColor(color)
        if (bold) t.typeface = Typeface.DEFAULT_BOLD
        return t
    }

    // ---------- called by the service ----------

    fun refresh(show: Boolean) {
        if (!show) {
            removeHandle()
            closePanel()
            removeCross()
            removeFps()
            return
        }
        if (panel == null) {
            if (store.panelOn) addHandle() else removeHandle()
        }
        applyCrosshair()
        applyFps()
    }

    fun destroy() {
        refresh(false)
    }

    // ---------- left edge handle ----------

    private fun addHandle() {
        if (handle != null) return
        val root = FrameLayout(ctx)
        val bar = View(ctx)
        val bg = GradientDrawable()
        bg.setColor(0xAAFF5A1F.toInt())
        bg.cornerRadius = dp(3).toFloat()
        bar.background = bg
        root.addView(
            bar,
            FrameLayout.LayoutParams(dp(5), dp(90), Gravity.START or Gravity.CENTER_VERTICAL)
        )

        var downX = 0f
        var opened = false
        root.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    opened = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!opened && e.rawX - downX > dp(18)) {
                        opened = true
                        handler.post { openPanel() }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!opened && abs(e.rawX - downX) < dp(10)) {
                        opened = true
                        handler.post { openPanel() }
                    }
                }
            }
            true
        }

        val lp = params(dp(32), dp(160), touchFlags)
        lp.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        lp.x = 0
        if (add(root, lp)) handle = root
    }

    private fun removeHandle() {
        remove(handle)
        handle = null
    }

    // ---------- side panel ----------

    private inner class PanelRoot(c: Context) : FrameLayout(c) {
        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                closePanelByUser()
                return true
            }
            return super.dispatchTouchEvent(ev)
        }
    }

    private fun closePanel() {
        remove(panel)
        panel = null
        syncs.clear()
    }

    private fun closePanelByUser() {
        closePanel()
        if (store.panelOn) addHandle()
    }

    private fun addRow(
        col: LinearLayout,
        title: String,
        state: () -> Pair<String, Int>,
        onClick: () -> Unit
    ) {
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val bg = GradientDrawable()
        bg.setColor(0xFF181A1F.toInt())
        bg.cornerRadius = dp(12).toFloat()
        bg.setStroke(dp(1), 0xFF262A31.toInt())
        row.background = bg
        row.setPadding(dp(12), dp(12), dp(12), dp(12))

        val t = text(title, 14f, 0xFFF2F2F2.toInt(), true)
        val s = text("", 12f, grey, true)
        row.addView(t, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(s)

        val sync: () -> Unit = {
            val (txt, colr) = state()
            s.text = txt
            s.setTextColor(colr)
        }
        sync()
        syncs.add(sync)
        row.setOnClickListener {
            onClick()
            syncs.toList().forEach { it() }
        }

        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = dp(8)
        col.addView(row, lp)
    }

    private fun onOff(on: Boolean): Pair<String, Int> =
        if (on) Pair("ON", green) else Pair("OFF", grey)

    private fun openPanel() {
        if (panel != null) return
        removeHandle()

        val root = PanelRoot(ctx)
        val bg = GradientDrawable()
        bg.setColor(0xF2111317.toInt())
        val r = dp(18).toFloat()
        bg.cornerRadii = floatArrayOf(0f, 0f, r, r, r, r, 0f, 0f)
        bg.setStroke(dp(1), 0xFF262A31.toInt())
        root.background = bg

        val scroll = ScrollView(ctx)
        scroll.isVerticalScrollBarEnabled = false
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(14), dp(16), dp(14), dp(16))

        val title = text("NOVASPACE", 18f, orange, true)
        title.letterSpacing = 0.15f
        col.addView(title)
        col.addView(text("Game tools", 12f, grey, false))

        addRow(col, "Buttons", {
            val l = store.loadButtons()
            when {
                l.isEmpty() -> Pair("NONE", grey)
                l.all { it.locked } -> Pair("LOCKED", green)
                else -> Pair("UNLOCKED", orange)
            }
        }) {
            val s = GameSpaceService.instance
            if (s != null) s.setAllLocked(!s.allButtonsLocked())
        }
        val hint = text("Unlock to move your buttons. Lock to use them.", 11f, grey, false)
        hint.setPadding(dp(4), dp(4), dp(4), 0)
        col.addView(hint)

        addRow(col, "Crosshair", { onOff(store.crossOn) }) {
            store.crossOn = !store.crossOn
            applyCrosshair()
        }
        addRow(col, "FPS counter", { onOff(store.fpsOn) }) {
            store.fpsOn = !store.fpsOn
            applyFps()
        }
        addRow(col, "Open NovaSpace", { Pair("OPEN", grey) }) {
            closePanelByUser()
            try {
                ctx.startActivity(
                    Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Exception) {
            }
        }
        addRow(col, "Close panel", { Pair("", grey) }) {
            closePanelByUser()
        }

        scroll.addView(col)
        root.addView(
            scroll,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val lp = params(
            dp(250),
            WindowManager.LayoutParams.MATCH_PARENT,
            touchFlags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        )
        lp.gravity = Gravity.START or Gravity.TOP
        if (add(root, lp)) panel = root else addHandle()
    }

    // ---------- crosshair ----------

    private fun removeCross() {
        remove(cross)
        cross = null
    }

    private fun applyCrosshair() {
        removeCross()
        if (!store.crossOn) return
        val size = dp(store.crossSize.coerceIn(10, 200))
        val v = CrosshairView(ctx, store)
        val lp = params(size, size, passFlags)
        lp.gravity = Gravity.CENTER
        lp.x = dp(store.crossDx)
        lp.y = dp(store.crossDy)
        if (add(v, lp)) cross = v
    }

    // ---------- FPS (screen refresh rate) ----------

    private val tick = object : Runnable {
        override fun run() {
            fps?.text = hz()
            handler.postDelayed(this, 1000)
        }
    }

    private fun hz(): String {
        val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val r = dm.getDisplay(Display.DEFAULT_DISPLAY)?.refreshRate ?: 60f
        return "${Math.round(r)} Hz"
    }

    private fun removeFps() {
        handler.removeCallbacks(tick)
        remove(fps)
        fps = null
    }

    private fun applyFps() {
        removeFps()
        if (!store.fpsOn) return
        val t = text(hz(), 12f, 0xFFFFFFFF.toInt(), true)
        t.setPadding(dp(10), dp(4), dp(10), dp(4))
        val bg = GradientDrawable()
        bg.setColor(0xCC15171C.toInt())
        bg.cornerRadius = dp(10).toFloat()
        bg.setStroke(dp(1), orange)
        t.background = bg
        val lp = params(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            passFlags
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = dp(48)
        lp.y = dp(6)
        if (add(t, lp)) {
            fps = t
            handler.postDelayed(tick, 1000)
        }
    }
}
