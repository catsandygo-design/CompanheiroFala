package br.com.companheirofala

import android.content.Context
import org.json.JSONObject

class DevelopmentTracker(context: Context) {
    private val prefs = context.getSharedPreferences("development_tracker", Context.MODE_PRIVATE)

    fun recordSpeech(text: String) {
        increment("speech_turns")
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size >= 3) increment("multiword_turns")
    }

    fun recordChoice(choice: String) {
        increment("choices")
        prefs.edit().putString("last_choice", choice).apply()
    }

    fun recordLearningSuccess(topic: String) {
        increment("learning_success")
        prefs.edit().putString("last_learning_topic", topic).apply()
    }

    data class XpProgress(val level: Int, val currentXp: Int, val xpForNextLevel: Int, val totalActivities: Int)

    fun recordActivity(): XpProgress {
        var level = prefs.getInt("xp_level", 1)
        var xp = prefs.getInt("xp_current", 0) + 1
        if (xp >= XP_PER_LEVEL) {
            level += 1
            xp = 0
        }
        val total = prefs.getInt("xp_total_activities", 0) + 1
        prefs.edit().putInt("xp_level", level).putInt("xp_current", xp).putInt("xp_total_activities", total).apply()
        return XpProgress(level, xp, XP_PER_LEVEL, total)
    }

    fun xpProgress() = XpProgress(
        prefs.getInt("xp_level", 1), prefs.getInt("xp_current", 0), XP_PER_LEVEL,
        prefs.getInt("xp_total_activities", 0)
    )

    fun snapshot(): JSONObject = JSONObject().apply {
        put("speech_turns", prefs.getInt("speech_turns", 0))
        put("multiword_turns", prefs.getInt("multiword_turns", 0))
        put("choices", prefs.getInt("choices", 0))
        put("learning_success", prefs.getInt("learning_success", 0))
        put("last_choice", prefs.getString("last_choice", ""))
        put("last_learning_topic", prefs.getString("last_learning_topic", ""))
        put("xp_level", xpProgress().level)
        put("xp_current", xpProgress().currentXp)
        put("xp_total_activities", xpProgress().totalActivities)
    }

    private fun increment(key: String) {
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }

    private companion object { const val XP_PER_LEVEL = 6 }
}
