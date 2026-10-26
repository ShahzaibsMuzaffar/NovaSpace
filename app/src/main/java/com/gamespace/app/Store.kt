package com.gamespace.app

import android.content.Context
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

data class TapPoint(val x: Float, val y: Float)

data class TapAction(
    val id: String,
    var name: String,
    var points: MutableList<TapPoint> = mutableListOf(),
    var sequence: Boolean = false,
    var delayMs: Long = 80,
    var holdMs: Long = 60
)

data class FloatBtn(
    val id: String,
    var actionId: String,
    var x: Int = 200,
    var y: Int = 400,
    var size: Int = 64,
    var locked: Boolean = false
)

class Store(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("novaspace", Context.MODE_PRIVATE)

    var games: Set<String>
        get() = HashSet(p.getStringSet("games", emptySet()) ?: emptySet())
        set(v) { p.edit().putStringSet("games", HashSet(v)).apply() }

    var panelOn: Boolean
        get() = p.getBoolean("panelOn", true)
        set(v) { p.edit().putBoolean("panelOn", v).apply() }

    var fpsOn: Boolean
        get() = p.getBoolean("fpsOn", false)
        set(v) { p.edit().putBoolean("fpsOn", v).apply() }

    var crossOn: Boolean
        get() = p.getBoolean("crossOn", false)
        set(v) { p.edit().putBoolean("crossOn", v).apply() }

    // 0 dot, 1 plus, 2 circle, 3 sniper cross, 4 X
    var crossShape: Int
        get() = p.getInt("crossShape", 1)
        set(v) { p.edit().putInt("crossShape", v).apply() }

    var crossColor: Int
        get() = p.getInt("crossColor", Color.RED)
        set(v) { p.edit().putInt("crossColor", v).apply() }

    var crossSize: Int
        get() = p.getInt("crossSize", 40)
        set(v) { p.edit().putInt("crossSize", v).apply() }

    var crossAlpha: Int
        get() = p.getInt("crossAlpha", 90)
        set(v) { p.edit().putInt("crossAlpha", v).apply() }

    var crossDx: Int
        get() = p.getInt("crossDx", 0)
        set(v) { p.edit().putInt("crossDx", v).apply() }

    var crossDy: Int
        get() = p.getInt("crossDy", 0)
        set(v) { p.edit().putInt("crossDy", v).apply() }

    fun loadActions(): MutableList<TapAction> {
        val out = mutableListOf<TapAction>()
        val arr = JSONArray(p.getString("actions", "[]"))
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val pts = mutableListOf<TapPoint>()
            val pa = o.getJSONArray("points")
            for (j in 0 until pa.length()) {
                val po = pa.getJSONObject(j)
                pts.add(TapPoint(po.getDouble("x").toFloat(), po.getDouble("y").toFloat()))
            }
            out.add(
                TapAction(
                    o.getString("id"), o.getString("name"), pts,
                    o.getBoolean("sequence"), o.getLong("delay"), o.getLong("hold")
                )
            )
        }
        return out
    }

    fun saveActions(list: List<TapAction>) {
        val arr = JSONArray()
        for (a in list) {
            val o = JSONObject()
            o.put("id", a.id)
            o.put("name", a.name)
            o.put("sequence", a.sequence)
            o.put("delay", a.delayMs)
            o.put("hold", a.holdMs)
            val pa = JSONArray()
            for (pt in a.points) {
                pa.put(JSONObject().put("x", pt.x.toDouble()).put("y", pt.y.toDouble()))
            }
            o.put("points", pa)
            arr.put(o)
        }
        p.edit().putString("actions", arr.toString()).apply()
    }

    fun loadButtons(): MutableList<FloatBtn> {
        val out = mutableListOf<FloatBtn>()
        val arr = JSONArray(p.getString("buttons", "[]"))
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                FloatBtn(
                    o.getString("id"), o.getString("actionId"),
                    o.getInt("x"), o.getInt("y"), o.getInt("size"), o.getBoolean("locked")
                )
            )
        }
        return out
    }

    fun saveButtons(list: List<FloatBtn>) {
        val arr = JSONArray()
        for (b in list) {
            arr.put(
                JSONObject()
                    .put("id", b.id)
                    .put("actionId", b.actionId)
                    .put("x", b.x)
                    .put("y", b.y)
                    .put("size", b.size)
                    .put("locked", b.locked)
            )
        }
        p.edit().putString("buttons", arr.toString()).apply()
    }
}
