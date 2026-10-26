package com.gamespace.app

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class GameItem(val pkg: String, val label: String, val icon: Drawable)

class GameAdapter(
    private val items: List<GameItem>,
    private val onClick: (GameItem) -> Unit,
    private val onLong: (GameItem) -> Unit
) : RecyclerView.Adapter<GameAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.gameIcon)
        val name: TextView = v.findViewById(R.id.gameName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_game, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = items[position]
        h.icon.setImageDrawable(item.icon)
        h.name.text = item.label
        h.itemView.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onClick(item)
        }
        h.itemView.setOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onLong(item)
            true
        }
    }
}

class MainActivity : AppCompatActivity() {

    private lateinit var store: Store
    private lateinit var list: RecyclerView
    private lateinit var empty: TextView
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        store = Store(this)
        list = findViewById(R.id.gamesList)
        empty = findViewById(R.id.emptyText)
        status = findViewById(R.id.statusText)
        list.layoutManager = GridLayoutManager(this, 3)

        findViewById<Button>(R.id.btnAccess).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btnAdd).setOnClickListener { showPicker() }
        findViewById<Button>(R.id.btnActions).setOnClickListener { openScreen("ActionsActivity") }
        findViewById<Button>(R.id.btnOverlay).setOnClickListener { openScreen("OverlaySettingsActivity") }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun openScreen(name: String) {
        try {
            startActivity(Intent().setClassName(this, "com.gamespace.app.$name"))
        } catch (e: Exception) {
            Toast.makeText(this, "This screen arrives in a later part", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isServiceOn(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains("$packageName/.GameSpaceService") ||
                enabled.contains("$packageName/$packageName.GameSpaceService")
    }

    private fun refresh() {
        if (isServiceOn()) {
            status.text = "Service: ON"
            status.setTextColor(0xFF3DDC84.toInt())
        } else {
            status.text = "Service: OFF (tap Enable)"
            status.setTextColor(0xFFFF5A1F.toInt())
        }

        val pm = packageManager
        val items = store.games.mapNotNull { pkg ->
            try {
                val info = pm.getApplicationInfo(pkg, 0)
                GameItem(pkg, pm.getApplicationLabel(info).toString(), pm.getApplicationIcon(info))
            } catch (e: Exception) {
                null
            }
        }.sortedBy { it.label.lowercase() }

        list.adapter = GameAdapter(items, { launch(it) }, { confirmRemove(it) })
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun launch(item: GameItem) {
        val intent = packageManager.getLaunchIntentForPackage(item.pkg)
        if (intent != null) {
            GameSpaceService.instance?.markGame(item.pkg)
            startActivity(intent)
        } else {
            Toast.makeText(this, "Cannot open ${item.label}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmRemove(item: GameItem) {
        AlertDialog.Builder(this)
            .setTitle("Remove ${item.label}?")
            .setMessage("It will be removed from NovaSpace only. The app stays installed.")
            .setPositiveButton("Remove") { _, _ ->
                store.games = store.games - item.pkg
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPicker() {
        val pm = packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(main, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != packageName }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }

        val names = apps.map { it.second }.toTypedArray()
        val current = store.games
        val checked = BooleanArray(apps.size) { current.contains(apps[it].first) }

        AlertDialog.Builder(this)
            .setTitle("Add games")
            .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("Save") { _, _ ->
                store.games = apps.indices.filter { checked[it] }.map { apps[it].first }.toSet()
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
