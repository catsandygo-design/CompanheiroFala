package br.com.companheirofala

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ParentEvent(
    val timestamp: Long,
    val type: String,
    val value: String,
    val detail: String = ""
)

class ParentEventRepository(context: Context) {
    private val prefs = context.getSharedPreferences("parent_events", Context.MODE_PRIVATE)

    fun record(type: String, value: String, detail: String = "") {
        val events = readRaw()
        events.put(JSONObject().apply {
            put("timestamp", System.currentTimeMillis())
            put("type", type)
            put("value", value)
            put("detail", detail)
        })
        while (events.length() > 150) {
            val trimmed = JSONArray()
            for (i in 1 until events.length()) trimmed.put(events.get(i))
            prefs.edit().putString("events", trimmed.toString()).apply()
            return
        }
        prefs.edit().putString("events", events.toString()).apply()
    }

    fun recent(limit: Int = 20): List<ParentEvent> {
        val array = readRaw()
        val result = mutableListOf<ParentEvent>()
        val start = (array.length() - limit).coerceAtLeast(0)
        for (i in start until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            result += ParentEvent(
                timestamp = item.optLong("timestamp"),
                type = item.optString("type"),
                value = item.optString("value"),
                detail = item.optString("detail")
            )
        }
        return result.reversed()
    }

    private fun readRaw(): JSONArray = try {
        JSONArray(prefs.getString("events", "[]") ?: "[]")
    } catch (_: Exception) {
        JSONArray()
    }
}
