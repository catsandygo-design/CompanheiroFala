package br.com.companheirofala

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView

class FairyPortraitView(context: Context) : FrameLayout(context) {
    private val image = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        setImageResource(R.drawable.fairy_companion)
        contentDescription = "Fada companheira"
    }

    init {
        clipToOutline = true
        background = GradientDrawable().apply {
            cornerRadius = dp(30).toFloat()
            setColor(Color.TRANSPARENT)
        }
        outlineProvider = background.outlineProvider
        addView(image, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER))
    }

    fun setMood(mood: RobotMood) {
        when (mood) {
            RobotMood.LISTENING -> animate().scaleX(1.02f).scaleY(1.02f).setDuration(180).start()
            RobotMood.SPEAKING -> animate().scaleX(1.015f).scaleY(1.015f).setDuration(180).start()
            RobotMood.SAD -> animate().alpha(0.94f).scaleX(1f).scaleY(1f).setDuration(220).start()
            else -> animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).start()
        }
    }

    fun setVoiceLevel(level: Float) {
        if (level > 7f) image.animate().scaleX(1.012f).scaleY(1.012f).setDuration(90).start()
        else image.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
