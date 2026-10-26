package com.gamespace.app

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class OverlaySettingsActivity : AppCompatActivity() {

    private lateinit var store: Store
    private lateinit var box: LinearLayout
    private lateinit var preview: CrosshairView

    private val orange = 0xFFFF5A1F.toInt()
    private val green = 0xFF3DDC84.toInt()
    private val grey = 0xFF9AA0A6.toInt()
    private val white = 0xFFF2F2F2.toInt()

    private val shapeNames = listOf("Dot", "Plus", "Circle", "Sniper cross", "X")
    private val colorNames = listOf("Red", "Green", "Yellow", "White", "Orange", "Cyan")
    private val colorValues = listOf(
        Color.RED, Color.GREEN, Color.YELLOW, Color.WHITE, 0xFFFF8A00.toInt(), Color.CYAN
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        val scroll = ScrollView(this)
        box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(16), dp(16), dp(16), dp(28))
        scroll.addView(box)
        setContentView(scroll)
        build()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(s: String, size: Float, color: Int, bold: Boolean): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = size
        t.setTextColor(color)
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        return t
    }

    private fun card(horizontal: Boolean): LinearLayout {
        val v = LinearLayout(this)
        v.orientation = if (horizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        if (horizontal) v.gravity = Gravity.CENTER_VERTICAL
        v.background = ContextCompat.getDrawable(this, R.drawable.card_bg)
        v.setPadding(dp(14), dp(12), dp(14), dp(12))
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = dp(8)
        v.layoutParams = lp
        return v
    }

    private fun pushToService() {
        GameSpaceService.instance?.refreshAll()
    }

    private fun updatePreview() {
        val size = dp(store.crossSize.coerceIn(10, 200))
        val lp = preview.layoutParams as FrameLayout.LayoutParams
        lp.width = size
        lp.height = size
        preview.layoutParams = lp
        preview.translationX = dp(store.crossDx).toFloat()
        preview.translationY = dp(store.crossDy).toFloat()
        preview.invalidate()
    }

    private fun toggle(title: String, get: () -> Boolean, set: (Boolean) -> Unit) {
        val row = card(true)
        val t = label(title, 15f, white, true)
        val s = label("", 13f, grey, true)
        row.addView(t, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(s)
        fun sync() {
            s.text = if (get()) "ON" else "OFF"
            s.setTextColor(if (get()) green else grey)
        }
        sync()
        row.setOnClickListener {
            set(!get())
            sync()
            pushToService()
        }
        box.addView(row)
    }

    private fun chooser(title: String, names: List<String>, current: () -> Int, pick: (Int) -> Unit) {
        val row = card(true)
        val t = label(title, 15f, white, true)
        val s = label("", 13f, orange, true)
        row.addView(t, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(s)
        fun sync() {
            val i = current()
            s.text = if (i in names.indices) names[i] else "Custom"
        }
        sync()
        row.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(names.toTypedArray()) { _, i ->
                    pick(i)
                    sync()
                    updatePreview()
                    pushToService()
                }
                .show()
        }
        box.addView(row)
    }

    private fun slider(
        title: String, min: Int, max: Int, unit: String,
        get: () -> Int, set: (Int) -> Unit
    ) {
        val wrap = card(false)
        val head = label("$title: ${get()}$unit", 14f, white, true)
        val sb = SeekBar(this)
        sb.max = max - min
        sb.progress = get() - min
        sb.progressTintList = ColorStateList.valueOf(orange)
        sb.thumbTintList = ColorStateList.valueOf(orange)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (!fromUser) return
                set(p + min)
                head.text = "$title: ${p + min}$unit"
                updatePreview()
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}

            override fun onStopTrackingTouch(s: SeekBar?) {
                pushToService()
            }
        })
        wrap.addView(head)
        wrap.addView(sb)
        box.addView(wrap)
    }

    private fun build() {
        box.addView(label("OVERLAY", 14f, orange, true))
        box.addView(
            label(
                "These show only over the games in your NovaSpace library.",
                12f, grey, false
            )
        )

        // live preview
        val pBox = FrameLayout(this)
        pBox.background = ContextCompat.getDrawable(this, R.drawable.card_bg)
        val pLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(170)
        )
        pLp.topMargin = dp(12)
        pBox.layoutParams = pLp
        val pTag = label("PREVIEW", 11f, grey, true)
        pBox.addView(
            pTag,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START
            ).also { it.setMargins(dp(12), dp(10), 0, 0) }
        )
        preview = CrosshairView(this, store)
        pBox.addView(
            preview,
            FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER)
        )
        box.addView(pBox)
        updatePreview()

        toggle("Crosshair", { store.crossOn }, { store.crossOn = it })
        chooser("Shape", shapeNames, { store.crossShape }, { store.crossShape = it })
        chooser(
            "Color", colorNames,
            { colorValues.indexOf(store.crossColor) },
            { store.crossColor = colorValues[it] }
        )
        slider("Size", 10, 200, " dp", { store.crossSize }, { store.crossSize = it })
        slider("Opacity", 10, 100, "%", { store.crossAlpha }, { store.crossAlpha = it })
        slider("Move left / right", -150, 150, " dp", { store.crossDx }, { store.crossDx = it })
        slider("Move up / down", -150, 150, " dp", { store.crossDy }, { store.crossDy = it })

        val head = label("OTHER", 14f, orange, true)
        head.setPadding(0, dp(22), 0, 0)
        box.addView(head)
        toggle("FPS counter", { store.fpsOn }, { store.fpsOn = it })
        toggle("Side panel handle", { store.panelOn }, { store.panelOn = it })

        val note = label(
            "The FPS counter shows your screen's refresh rate (for example 60 or 120 Hz), not the " +
                    "game's real FPS. Real game FPS comes in version 2 with Shizuku.\n\n" +
                    "To open the side panel in a game, tap or swipe right on the orange bar at the " +
                    "left edge. If your phone's back gesture triggers instead, just tap the bar.",
            12f, grey, false
        )
        note.setPadding(0, dp(16), 0, 0)
        box.addView(note)
    }
}
