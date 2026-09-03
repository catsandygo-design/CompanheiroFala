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

    fun snapshot(): JSONObject = JSONObject().apply {
        put("speech_turns", prefs.getInt("speech_turns", 0))
        put("multiword_turns", prefs.getInt("multiword_turns", 0))
        put("choices", prefs.getInt("choices", 0))
        put("learning_success", prefs.getInt("learning_success", 0))
        put("last_choice", prefs.getString("last_choice", ""))
        put("last_learning_topic", prefs.getString("last_learning_topic", ""))
    }

    private fun increment(key: String) {
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }
}
