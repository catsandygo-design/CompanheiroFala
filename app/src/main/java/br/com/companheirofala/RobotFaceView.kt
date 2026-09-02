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
import kotlin.math.max
import kotlin.math.min
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
    private var voiceLevel = 0f

    private val animator = object : Runnable {
        override fun run() {
            phase += 0.13f
            invalidate()
            handler.postDelayed(this, 45)
        }
    }

    private val blinker = object : Runnable {
        override fun run() {
            if (mood != RobotMood.LISTENING) {
                blink = true
                invalidate()
                handler.postDelayed({
                    blink = false
                    invalidate()
                }, 120)
            }
            handler.postDelayed(this, 2200L + (Math.random() * 1800L).toLong())
        }
    }

    init {
        handler.post(animator)
        handler.postDelayed(blinker, 1000)
    }

    fun setMood(newMood: RobotMood) {
        mood = newMood
        invalidate()
    }

    fun setVoiceLevel(rmsDb: Float) {
        voiceLevel = min(1f, max(0f, (rmsDb + 2f) / 12f))
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
        val bob = when (mood) {
            RobotMood.SPEAKING -> sin((phase * 2).toDouble()).toFloat() * 5f
            RobotMood.HAPPY -> sin(phase.toDouble()).toFloat() * 3f
            else -> sin((phase * .55f).toDouble()).toFloat() * 2f
        }

        val glowColor = when (mood) {
            RobotMood.LISTENING -> Color.rgb(53, 224, 255)
            RobotMood.THINKING -> Color.rgb(165, 116, 255)
            RobotMood.SPEAKING -> Color.rgb(74, 210, 255)
            RobotMood.HAPPY -> Color.rgb(75, 244, 176)
            RobotMood.CONFUSED -> Color.rgb(255, 184, 83)
            RobotMood.IDLE -> Color.rgb(68, 153, 255)
        }

        val panel = RectF(w * .055f, h * .08f + bob, w * .945f, h * .92f + bob)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(9, 18, 35)
        canvas.drawRoundRect(panel, 72f, 72f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = Color.argb(130, Color.red(glowColor), Color.green(glowColor), Color.blue(glowColor))
        canvas.drawRoundRect(panel, 72f, 72f, paint)

        if (mood == RobotMood.LISTENING) {
            val pulse = 7f + voiceLevel * 20f + (sin((phase * 2.2).toDouble()).toFloat() + 1f) * 3f
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f + voiceLevel * 4f
            paint.color = Color.argb(95, 53, 224, 255)
            canvas.drawRoundRect(
                RectF(panel.left - pulse, panel.top - pulse, panel.right + pulse, panel.bottom + pulse),
                78f,
                78f,
                paint
            )
        }

        val eyeY = cy - h * .045f + bob
        val eyeSpread = w * .225f
        val eyeScale = 1f + if (mood == RobotMood.LISTENING) voiceLevel * .12f else 0f

        drawEye(canvas, cx - eyeSpread, eyeY, true, eyeScale, glowColor)
        drawEye(canvas, cx + eyeSpread, eyeY, false, eyeScale, glowColor)
        drawMouth(canvas, cx, cy + h * .20f + bob, glowColor)

        if (mood == RobotMood.THINKING) {
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(155, 181, 141, 255)
            val dotY = panel.top + h * .13f
            for (i in 0..2) {
                val r = 5f + i * 2f
                canvas.drawCircle(cx + w * .28f + i * 17f, dotY - i * 10f, r, paint)
            }
        }
    }

    private fun drawEye(canvas: Canvas, x: Float, y: Float, left: Boolean, scale: Float, glowColor: Int) {
        paint.color = glowColor

        if (blink) {
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(RectF(x - 52f, y - 5f, x + 52f, y + 6f), 10f, 10f, paint)
            return
        }

        when (mood) {
            RobotMood.HAPPY -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 13f
                canvas.drawArc(RectF(x - 52f, y - 22f, x + 52f, y + 62f), 200f, 140f, false, paint)
            }

            RobotMood.CONFUSED -> {
                paint.style = Paint.Style.FILL
                val tilt = if (left) -9f else 9f
                canvas.save()
                canvas.rotate(tilt, x, y)
                canvas.drawRoundRect(RectF(x - 45f, y - 25f, x + 45f, y + 28f), 24f, 24f, paint)
                canvas.restore()
            }

            RobotMood.THINKING -> {
                paint.style = Paint.Style.FILL
                val offsetX = cos(phase.toDouble()).toFloat() * 14f
                val offsetY = sin((phase * .7).toDouble()).toFloat() * 6f
                canvas.drawOval(RectF(x - 39f + offsetX, y - 31f + offsetY, x + 39f + offsetX, y + 31f + offsetY), paint)
            }

            else -> {
                paint.style = Paint.Style.FILL
                val talkBounce = if (mood == RobotMood.SPEAKING) sin((phase * 2.5).toDouble()).toFloat() * 5f else 0f
                val halfW = 43f * scale
                val halfH = 31f * scale
                canvas.drawRoundRect(
                    RectF(x - halfW, y - halfH + talkBounce, x + halfW, y + halfH + talkBounce),
                    28f,
                    28f,
                    paint
                )

                if (mood == RobotMood.LISTENING) {
                    paint.color = Color.rgb(8, 20, 36)
                    val pupilShift = sin((phase * .45).toDouble()).toFloat() * 5f
                    canvas.drawCircle(x + pupilShift, y, 10f + voiceLevel * 2f, paint)
                }
            }
        }
    }

    private fun drawMouth(canvas: Canvas, x: Float, y: Float, glowColor: Int) {
        paint.color = glowColor
        paint.strokeWidth = 9f
        paint.style = Paint.Style.STROKE

        when (mood) {
            RobotMood.HAPPY -> canvas.drawArc(RectF(x - 62f, y - 34f, x + 62f, y + 48f), 15f, 150f, false, paint)
            RobotMood.SPEAKING -> {
                paint.style = Paint.Style.FILL
                val open = 16f + (sin((phase * 3.2).toDouble()).toFloat() + 1f) * 12f
                canvas.drawOval(RectF(x - 35f, y - open, x + 35f, y + open), paint)
            }
            RobotMood.CONFUSED -> canvas.drawLine(x - 38f, y + 7f, x + 38f, y - 7f, paint)
            RobotMood.THINKING -> {
                paint.style = Paint.Style.FILL
                canvas.drawCircle(x + 7f, y, 10f, paint)
            }
            RobotMood.LISTENING -> {
                val smile = 8f + voiceLevel * 12f
                canvas.drawArc(RectF(x - 46f, y - smile, x + 46f, y + 30f + smile), 22f, 136f, false, paint)
            }
            RobotMood.IDLE -> canvas.drawArc(RectF(x - 46f, y - 13f, x + 46f, y + 31f), 20f, 140f, false, paint)
        }
    }
}
