package br.com.companheirofala

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin

enum class VisualScene { NONE, HORSE, TOOTHBRUSH, WATER, TOILET, HANDS, BACTERIA, LETTER_A, LETTER_B, LETTER_C }

class ChildVisualView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var scene = VisualScene.NONE
    private var phase = 0f

    fun showScene(newScene: VisualScene) {
        scene = newScene
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        phase += 0.12f
        val w = width.toFloat()
        val h = height.toFloat()
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(20, 34, 58)
        canvas.drawRoundRect(RectF(0f, 0f, w, h), 42f, 42f, paint)
        when (scene) {
            VisualScene.HORSE -> drawHorse(canvas, w, h)
            VisualScene.TOOTHBRUSH -> drawToothbrush(canvas, w, h)
            VisualScene.WATER -> drawWater(canvas, w, h)
            VisualScene.TOILET -> drawToilet(canvas, w, h)
            VisualScene.HANDS -> drawHands(canvas, w, h)
            VisualScene.BACTERIA -> drawBacteria(canvas, w, h)
            VisualScene.LETTER_A -> drawLetter(canvas, w, h, "A")
            VisualScene.LETTER_B -> drawLetter(canvas, w, h, "B")
            VisualScene.LETTER_C -> drawLetter(canvas, w, h, "C")
            VisualScene.NONE -> Unit
        }
        if (scene == VisualScene.BACTERIA) postInvalidateDelayed(60)
    }

    private fun drawHorse(canvas: Canvas, w: Float, h: Float) {
        paint.color = Color.rgb(176, 116, 67)
        canvas.drawOval(RectF(w*.27f, h*.35f, w*.72f, h*.72f), paint)
        canvas.drawCircle(w*.68f, h*.33f, h*.15f, paint)
        paint.color = Color.rgb(105, 63, 42)
        canvas.drawRect(w*.32f, h*.66f, w*.38f, h*.9f, paint)
        canvas.drawRect(w*.58f, h*.66f, w*.64f, h*.9f, paint)
        paint.color = Color.BLACK
        canvas.drawCircle(w*.72f, h*.29f, 8f, paint)
        paint.color = Color.rgb(91, 54, 32)
        canvas.drawArc(RectF(w*.55f, h*.1f, w*.78f, h*.5f), 130f, 120f, false, paint)
    }

    private fun drawToothbrush(canvas: Canvas, w: Float, h: Float) {
        paint.color = Color.rgb(72, 178, 255)
        canvas.save(); canvas.rotate(-18f, w/2, h/2)
        canvas.drawRoundRect(RectF(w*.18f, h*.48f, w*.82f, h*.62f), 28f, 28f, paint)
        paint.color = Color.WHITE
        for (i in 0..5) canvas.drawRect(w*.67f+i*10f, h*.38f, w*.71f+i*10f, h*.49f, paint)
        canvas.restore()
    }

    private fun drawWater(canvas: Canvas, w: Float, h: Float) {
        paint.color = Color.rgb(84, 194, 255)
        canvas.drawRoundRect(RectF(w*.32f, h*.2f, w*.68f, h*.82f), 30f, 30f, paint)
        paint.color = Color.argb(120, 255,255,255)
        canvas.drawRect(w*.37f, h*.3f, w*.42f, h*.72f, paint)
    }

    private fun drawToilet(canvas: Canvas, w: Float, h: Float) {
        paint.color = Color.WHITE
        canvas.drawRoundRect(RectF(w*.28f, h*.18f, w*.7f, h*.45f), 24f,24f,paint)
        canvas.drawOval(RectF(w*.3f, h*.37f, w*.72f, h*.72f), paint)
        canvas.drawRoundRect(RectF(w*.42f, h*.62f, w*.62f, h*.88f),20f,20f,paint)
        paint.color = Color.rgb(111,205,255)
        canvas.drawOval(RectF(w*.38f,h*.44f,w*.64f,h*.61f),paint)
    }

    private fun drawHands(canvas: Canvas, w: Float, h: Float) {
        paint.color = Color.rgb(255, 201, 158)
        canvas.drawCircle(w*.38f,h*.52f,h*.18f,paint)
        canvas.drawCircle(w*.62f,h*.52f,h*.18f,paint)
        paint.color = Color.rgb(93, 194, 255)
        for (i in 0..6) {
            val x = w*.22f + i*w*.09f
            canvas.drawCircle(x, h*.28f + (i%2)*18f, 9f, paint)
        }
    }

    private fun drawBacteria(canvas: Canvas, w: Float, h: Float) {
        val colors = intArrayOf(Color.rgb(114,239,118), Color.rgb(255,111,164), Color.rgb(255,208,80))
        for (i in 0..5) {
            val x = w*(.18f + .13f*i)
            val y = h*(.32f + .22f*((i%2))) + sin(phase+i).toFloat()*12f
            paint.color = colors[i%colors.size]
            canvas.drawCircle(x,y,34f,paint)
            paint.color = Color.BLACK
            canvas.drawCircle(x-10f,y-5f,4f,paint)
            canvas.drawCircle(x+10f,y-5f,4f,paint)
        }
        paint.color = Color.rgb(255, 224, 68)
        val mouth = 22f + sin(phase*2).toFloat()*10f
        canvas.drawArc(RectF(w*.38f,h*.6f,w*.62f,h*.88f), mouth, 360f-mouth*2, true, paint)
    }

    private fun drawLetter(canvas: Canvas, w: Float, h: Float, letter: String) {
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = h*.72f
        paint.isFakeBoldText = true
        canvas.drawText(letter, w/2, h*.76f, paint)
        paint.isFakeBoldText = false
    }
}