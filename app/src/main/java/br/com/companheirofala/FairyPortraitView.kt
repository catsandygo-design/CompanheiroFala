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
        adjustViewBounds = false
        setImageResource(R.drawable.fairy_companion)
        contentDescription = "Fada companheira"
        alpha = 1f
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        clipChildren = false
        clipToPadding = false
        addView(image, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER))
    }

    fun setMood(mood: RobotMood) {
        image.animate().cancel()
        when (mood) {
            RobotMood.LISTENING -> image.animate().scaleX(1.035f).scaleY(1.035f).alpha(1f).setDuration(180).start()
            RobotMood.SPEAKING -> image.animate().scaleX(1.02f).scaleY(1.02f).alpha(1f).setDuration(150).start()
            RobotMood.SAD -> image.animate().scaleX(1f).scaleY(1f).alpha(0.96f).setDuration(220).start()
            RobotMood.THINKING -> image.animate().scaleX(1.01f).scaleY(1.01f).alpha(0.98f).setDuration(180).start()
            else -> image.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(180).start()
        }
    }

    fun setVoiceLevel(level: Float) {
        val target = if (level > 7f) 1.018f else 1f
        image.animate().scaleX(target).scaleY(target).setDuration(90).start()
    }
}
