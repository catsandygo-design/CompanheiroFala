package br.com.companheirofala

import android.content.Context

 data class LearningActivity(
    val word: String,
    val correctLetter: String,
    val options: List<String>,
    val scene: VisualScene
)

class ActivityEngine(context: Context) {
    private val prefs = context.getSharedPreferences("activity_engine", Context.MODE_PRIVATE)
    private val activities = listOf(
        LearningActivity("cavalo", "C", listOf("A", "C", "P"), VisualScene.HORSE),
        LearningActivity("água", "A", listOf("A", "B", "O"), VisualScene.WATER),
        LearningActivity("dente", "D", listOf("D", "T", "B"), VisualScene.TOOTHBRUSH),
        LearningActivity("mão", "M", listOf("M", "N", "P"), VisualScene.HANDS),
        LearningActivity("comida", "C", listOf("C", "F", "M"), VisualScene.FOOD)
    )

    fun next(): LearningActivity {
        val index = prefs.getInt("index", 0) % activities.size
        prefs.edit().putInt("index", index + 1).apply()
        return activities[index]
    }
}
