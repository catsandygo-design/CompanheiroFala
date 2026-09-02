package br.com.companheirofala

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

enum class RobotMood { IDLE, LISTENING, THINKING, SPEAKING, HAPPY, CONFUSED }

class RobotFaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handler = Handler(Looper.getMainLooper())
    private var mood = RobotMood.IDLE
    private var blink = false
    private var phase = 0f

    private val animator = object : Runnable {
        override fun run() {
            phase += 0.16f
            invalidate()
            handler.postDelayed(this, 50)
        }
    }

    private val blinker = object : Runnable {
        override fun run() {
            blink = true
            invalidate()
            handler.postDelayed({
                blink = false
                invalidate()
            }, 130)
            handler.postDelayed(this, 2400L + (Math.random() * 1800L).toLong())
        }
    }

    init {
        handler.post(animator)
        handler.postDelayed(blinker, 1200)
    }

    fun setMood(newMood: RobotMood) {
        mood = newMood
        invalidate()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w / 2f
        val cy = h / 2f
        val bob = sin(phase.toDouble()).toFloat() * 4f
        val face = RectF(w * .12f, h * .16f + bob, w * .88f, h * .84f + bob)

        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(18, 26, 45)
        canvas.drawRoundRect(face, 52f, 52f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 6f
        paint.color = when (mood) {
            RobotMood.LISTENING -> Color.rgb(75, 220, 255)
            RobotMood.THINKING -> Color.rgb(160, 120, 255)
            RobotMood.SPEAKING -> Color.rgb(63, 205, 255)
            RobotMood.HAPPY -> Color.rgb(87, 240, 181)
            RobotMood.CONFUSED -> Color.rgb(255, 190, 92)
            RobotMood.IDLE -> Color.rgb(70, 160, 255)
        }
        canvas.drawRoundRect(face, 52f, 52f, paint)

        val eyeY = cy - h * .07f + bob
        val leftX = cx - w * .18f
        val rightX = cx + w * .18f
        drawEye(canvas, leftX, eyeY, true)
        drawEye(canvas, rightX, eyeY, false)
        drawMouth(canvas, cx, cy + h * .16f + bob)

        if (mood == RobotMood.LISTENING) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            paint.color = Color.argb(150, 75, 220, 255)
            val pulse = 14f + (sin((phase * 2).toDouble()).toFloat() + 1f) * 8f
            canvas.drawCircle(cx, cy, w * .42f + pulse, paint)
        }
    }

    private fun drawEye(canvas: Canvas, x: Float, y: Float, left: Boolean) {
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(95, 225, 255)

        if (blink) {
            canvas.drawRoundRect(RectF(x - 42f, y - 4f, x + 42f, y + 5f), 8f, 8f, paint)
            return
        }

        when (mood) {
            RobotMood.HAPPY -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 10f
                val r = RectF(x - 38f, y - 12f, x + 38f, y + 52f)
                canvas.drawArc(r, 200f, 140f, false, paint)
            }
            RobotMood.CONFUSED -> {
                val tilt = if (left) -10f else 10f
                canvas.save()
                canvas.rotate(tilt, x, y)
                canvas.drawRoundRect(RectF(x - 34f, y - 14f, x + 34f, y + 24f), 18f, 18f, paint)
                canvas.restore()
            }
            RobotMood.THINKING -> {
                val offset = cos(phase.toDouble()).toFloat() * 10f
                canvas.drawCircle(x + offset, y, 24f, paint)
            }
            else -> {
                val offset = if (mood == RobotMood.SPEAKING) sin((phase * 2).toDouble()).toFloat() * 5f else 0f
                canvas.drawRoundRect(RectF(x - 31f, y - 24f + offset, x + 31f, y + 24f + offset), 22f, 22f, paint)
            }
        }
    }

    private fun drawMouth(canvas: Canvas, x: Float, y: Float) {
        paint.color = Color.rgb(117, 225, 255)
        paint.strokeWidth = 8f
        paint.style = Paint.Style.STROKE
        when (mood) {
            RobotMood.HAPPY -> canvas.drawArc(RectF(x - 48f, y - 28f, x + 48f, y + 36f), 15f, 150f, false, paint)
            RobotMood.SPEAKING -> {
                paint.style = Paint.Style.FILL
                val open = 14f + (sin((phase * 3).toDouble()).toFloat() + 1f) * 10f
                canvas.drawOval(RectF(x - 30f, y - open, x + 30f, y + open), paint)
            }
            RobotMood.CONFUSED -> canvas.drawLine(x - 28f, y + 6f, x + 28f, y - 6f, paint)
            RobotMood.THINKING -> canvas.drawCircle(x + 5f, y, 10f, paint)
            else -> canvas.drawArc(RectF(x - 38f, y - 12f, x + 38f, y + 28f), 20f, 140f, false, paint)
        }
    }
}
