package br.com.companheirofala.core.safety

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class TrustedPeopleRepository(context: Context) {
    private val prefs = context.getSharedPreferences("trusted_people", Context.MODE_PRIVATE)
    fun people(): List<TrustedPerson> = try {
        val array = JSONArray(prefs.getString("people", "[]")); (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.let { item ->
            TrustedPerson(item.optString("id"), item.optString("name"), item.optString("relationship"), item.optJSONArray("environments")?.let { envs -> (0 until envs.length()).mapNotNull { runCatching { CurrentEnvironment.valueOf(envs.getString(it)) }.getOrNull() }.toSet() } ?: emptySet(), item.optInt("priority", 99), item.optString("phone"), item.optBoolean("notificationEnabled"))
        }}
    } catch (_: Exception) { emptyList() }
    fun save(people: List<TrustedPerson>) = prefs.edit().putString("people", JSONArray().apply { people.forEach { person -> put(JSONObject().apply { put("id", person.id); put("name", person.name); put("relationship", person.relationship); put("environments", JSONArray(person.allowedEnvironments.map { it.name })); put("priority", person.priority); put("phone", person.phone); put("notificationEnabled", person.notificationEnabled) }) } }.toString()).apply()
}

class SafetyEventRepository(context: Context) {
    private val prefs = context.getSharedPreferences("safety_events", Context.MODE_PRIVATE)
    fun record(event: SafetyEvent) {
        val events = try { JSONArray(prefs.getString("events", "[]")) } catch (_: Exception) { JSONArray() }
        events.put(JSONObject().apply { put("id", event.id); put("timestamp", event.timestamp); put("category", event.category.name); put("transcript", event.transcript); put("environment", event.environment.name); put("severity", event.severity.name); put("actions", JSONArray(event.actionsTaken)); put("notificationSent", event.notificationSent) })
        while (events.length() > 100) events.remove(0)
        prefs.edit().putString("events", events.toString()).apply()
    }
}

interface NotificationService { fun notifySafety(event: SafetyEvent, person: TrustedPerson?): Boolean }
class LocalNotificationService : NotificationService { override fun notifySafety(event: SafetyEvent, person: TrustedPerson?) = false }
