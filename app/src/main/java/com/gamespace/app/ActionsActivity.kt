package com.gamespace.app

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.UUID

class ActionsActivity : AppCompatActivity() {

    private lateinit var store: Store
    private lateinit var box: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        val scroll = ScrollView(this)
        box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(16), dp(16), dp(16), dp(24))
        scroll.addView(box)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(text: String, size: Float, color: Int, bold: Boolean): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(color)
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        t.setPadding(0, dp(4), 0, dp(4))
        return t
    }

    private fun row(title: String, sub: String, onClick: () -> Unit): LinearLayout {
        val v = LinearLayout(this)
        v.orientation = LinearLayout.VERTICAL
        v.background = ContextCompat.getDrawable(this, R.drawable.card_bg)
        v.setPadding(dp(14), dp(12), dp(14), dp(12))
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = dp(8)
        v.layoutParams = lp
        v.addView(label(title, 16f, 0xFFF2F2F2.toInt(), true))
        v.addView(label(sub, 12f, 0xFF9AA0A6.toInt(), false))
        v.setOnClickListener { onClick() }
        return v
    }

    private fun render() {
        box.removeAllViews()
        val actions = store.loadActions()
        val buttons = store.loadButtons()
        val grey = 0xFF9AA0A6.toInt()

        box.addView(label("ACTIONS", 14f, 0xFFFF5A1F.toInt(), true))
        box.addView(
            label(
                "An action is a saved list of taps. Record it over your game, then add a floating button for it.",
                12f, grey, false
            )
        )
        box.addView(row("+ New action", "Create an empty action") { newAction() })
        for (a in actions) {
            val mode = if (a.sequence) "one by one, ${a.delayMs} ms gap" else "all together"
            box.addView(row(a.name, "${a.points.size} taps, $mode") { actionMenu(a) })
        }

        val head = label("FLOATING BUTTONS", 14f, 0xFFFF5A1F.toInt(), true)
        head.setPadding(0, dp(24), 0, dp(4))
        box.addView(head)
        var shown = 0
        for (b in buttons) {
            val a = actions.find { it.id == b.actionId } ?: continue
            val state = if (b.locked) "locked" else "unlocked"
            box.addView(row("${a.name} button", "${b.size} dp, $state") { buttonMenu(b) })
            shown++
        }
        if (shown == 0) {
            box.addView(label("No buttons yet. Open an action and choose Add floating button.", 12f, grey, false))
        }

        val tip = label(
            "In a game: swipe in from the left edge to open the side panel and use Lock buttons or Unlock buttons. " +
                    "Unlocked buttons (orange dashed ring) can be dragged. " +
                    "Locked buttons (green ring) fire their action. Hold one for continuous fire.",
            12f, grey, false
        )
        tip.setPadding(0, dp(20), 0, 0)
        box.addView(tip)
    }

    private fun ask(title: String, initial: String, numeric: Boolean, ok: (String) -> Unit) {
        val et = EditText(this)
        et.setText(initial)
        if (numeric) et.inputType = InputType.TYPE_CLASS_NUMBER
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(et)
            .setPositiveButton("OK") { _, _ -> ok(et.text.toString().trim()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun newAction() {
        ask("Action name", "", false) { name ->
            if (name.isNotEmpty()) {
                val list = store.loadActions()
                list.add(TapAction(UUID.randomUUID().toString(), name))
                store.saveActions(list)
                render()
            }
        }
    }

    private fun saveAction(a: TapAction) {
        val list = store.loadActions()
        val i = list.indexOfFirst { it.id == a.id }
        if (i >= 0) {
            list[i] = a
            store.saveActions(list)
        }
        GameSpaceService.instance?.refreshAll()
        render()
    }

    private fun actionMenu(a: TapAction) {
        val items = arrayOf(
            "Record taps",
            "Mode: " + (if (a.sequence) "one by one" else "together") + " (tap to switch)",
            "Delay between taps: ${a.delayMs} ms",
            "Add floating button",
            "Rename",
            "Delete"
        )
        AlertDialog.Builder(this)
            .setTitle(a.name)
            .setItems(items) { _, i ->
                when (i) {
                    0 -> record(a)
                    1 -> {
                        a.sequence = !a.sequence
                        saveAction(a)
                    }
                    2 -> ask("Delay in ms", a.delayMs.toString(), true) { v ->
                        a.delayMs = (v.toLongOrNull() ?: 80L).coerceIn(0L, 3000L)
                        saveAction(a)
                    }
                    3 -> addButton(a)
                    4 -> ask("Action name", a.name, false) { v ->
                        if (v.isNotEmpty()) {
                            a.name = v
                            saveAction(a)
                        }
                    }
                    5 -> deleteAction(a)
                }
            }
            .show()
    }

    private fun record(a: TapAction) {
        val s = GameSpaceService.instance
        if (s == null) {
            Toast.makeText(this, "Turn on NovaSpace in Accessibility settings first", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        s.startRecording(a.id)
        Toast.makeText(
            this,
            "Now open your game from NovaSpace. Tap the spots in order, then press Save.",
            Toast.LENGTH_LONG
        ).show()
        finish()
    }

    private fun addButton(a: TapAction) {
        val list = store.loadButtons()
        list.add(FloatBtn(UUID.randomUUID().toString(), a.id, 200 + 90 * list.size, 300))
        store.saveButtons(list)
        GameSpaceService.instance?.refreshAll()
        Toast.makeText(this, "Button added. It appears over your games.", Toast.LENGTH_SHORT).show()
        render()
    }

    private fun deleteAction(a: TapAction) {
        val list = store.loadActions()
        list.removeAll { it.id == a.id }
        store.saveActions(list)
        val btns = store.loadButtons()
        btns.removeAll { it.actionId == a.id }
        store.saveButtons(btns)
        GameSpaceService.instance?.refreshAll()
        render()
    }

    private fun saveButton(b: FloatBtn) {
        val list = store.loadButtons()
        val i = list.indexOfFirst { it.id == b.id }
        if (i >= 0) {
            list[i] = b
            store.saveButtons(list)
        }
        GameSpaceService.instance?.refreshAll()
        render()
    }

    private fun buttonMenu(b: FloatBtn) {
        val items = arrayOf(
            "Size: ${b.size} dp",
            if (b.locked) "Unlock" else "Lock",
            "Reset position",
            "Remove"
        )
        AlertDialog.Builder(this)
            .setTitle("Floating button")
            .setItems(items) { _, i ->
                when (i) {
                    0 -> ask("Size in dp (40 to 120)", b.size.toString(), true) { v ->
                        b.size = (v.toIntOrNull() ?: 64).coerceIn(40, 120)
                        saveButton(b)
                    }
                    1 -> {
                        b.locked = !b.locked
                        saveButton(b)
                    }
                    2 -> {
                        b.x = 200
                        b.y = 300
                        saveButton(b)
                    }
                    3 -> {
                        val list = store.loadButtons()
                        list.removeAll { it.id == b.id }
                        store.saveButtons(list)
                        GameSpaceService.instance?.refreshAll()
                        render()
                    }
                }
            }
            .show()
    }
}
