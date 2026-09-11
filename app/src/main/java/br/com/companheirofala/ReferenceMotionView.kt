package br.com.companheirofala

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.abs
import kotlin.math.sin

/** Desenha apenas efeitos vetoriais; nunca recorta nem redesenha a arte de fundo. */
class ReferenceMotionView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val startedAt = System.currentTimeMillis()

    var speaking: Boolean = false
        set(value) { field = value; invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        val time = (System.currentTimeMillis() - startedAt) / 1000f

        // Brilhos acompanham a Lumi, sem copiar pixels da ilustração original.
        val fairyX = width * (.255f + sin(time * 1.15f) * .014f)
        val fairyY = height * (.205f + sin(time * 2.2f) * .005f)
        glow(canvas, fairyX - width * .13f, fairyY + height * .055f, width * .009f, Color.argb(190, 255, 245, 108))
        glow(canvas, fairyX + width * .135f, fairyY + height * .075f, width * .006f, Color.argb(175, 255, 255, 255))
        if (speaking) drawMouth(canvas, time)

        // Sinais vivos ao redor dos cartões, sem encobrir desenhos ou letras.
        val bounce = abs(sin(time * 2.5f))
        bubble(canvas, .105f, .397f - bounce * .018f, .008f, Color.argb(150, 220, 252, 255))
        bubble(canvas, .275f, .445f - bounce * .012f, .005f, Color.argb(130, 220, 252, 255))
        sparkle(canvas, .575f, .397f, .010f, time * 80f, Color.argb(180, 255, 205, 235))
        sparkle(canvas, .887f, .405f - abs(sin(time * 3.4f)) * .010f, .009f, time * -95f, Color.argb(210, 255, 255, 255))
        musicNote(canvas, .105f, .588f + sin(time * 3.1f) * .009f, Color.argb(180, 255, 245, 125))
        sparkle(canvas, .562f, .596f, .009f, time * 65f, Color.argb(185, 255, 245, 125))
        heart(canvas, .880f, .590f + sin(time * 3.3f) * .008f, .010f, Color.argb(185, 255, 140, 188))
        postInvalidateOnAnimation()
    }

    private fun drawMouth(canvas: Canvas, time: Float) {
        val cx = width * .268f
        val cy = height * .205f
        val open = height * (.003f + abs(sin(time * 13f)) * .004f)
        paint.color = Color.rgb(132, 49, 68)
        canvas.drawOval(RectF(cx - width * .014f, cy - open, cx + width * .014f, cy + open), paint)
    }

    private fun glow(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        paint.color = color
        paint.style = Paint.Style.FILL
        canvas.drawCircle(x, y, radius, paint)
        paint.color = Color.argb(100, 255, 255, 255)
        canvas.drawCircle(x, y, radius * .38f, paint)
    }

    private fun bubble(canvas: Canvas, x: Float, y: Float, radiusFraction: Float, color: Int) {
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width * .0025f
        canvas.drawCircle(width * x, height * y, width * radiusFraction, paint)
        paint.style = Paint.Style.FILL
    }

    private fun sparkle(canvas: Canvas, x: Float, y: Float, radiusFraction: Float, rotation: Float, color: Int) {
        val cx = width * x; val cy = height * y; val radius = width * radiusFraction
        paint.color = color; paint.strokeWidth = width * .003f
        canvas.save(); canvas.rotate(rotation, cx, cy)
        canvas.drawLine(cx - radius, cy, cx + radius, cy, paint)
        canvas.drawLine(cx, cy - radius, cx, cy + radius, paint)
        canvas.restore()
    }

    private fun heart(canvas: Canvas, x: Float, y: Float, sizeFraction: Float, color: Int) {
        val cx = width * x; val cy = height * y; val size = width * sizeFraction
        paint.color = color
        canvas.drawCircle(cx - size * .45f, cy - size * .2f, size * .5f, paint)
        canvas.drawCircle(cx + size * .45f, cy - size * .2f, size * .5f, paint)
        canvas.save(); canvas.rotate(45f, cx, cy + size * .25f)
        canvas.drawRect(cx - size * .7f, cy - size * .35f, cx + size * .7f, cy + size * 1.05f, paint)
        canvas.restore()
    }

    private fun musicNote(canvas: Canvas, x: Float, y: Float, color: Int) {
        val cx = width * x; val cy = height * y; val size = width * .011f
        paint.color = color; paint.strokeWidth = width * .003f
        canvas.drawCircle(cx, cy, size * .52f, paint)
        canvas.drawLine(cx + size * .45f, cy, cx + size * .45f, cy - size * 2.1f, paint)
        canvas.drawLine(cx + size * .45f, cy - size * 2.1f, cx + size * 1.35f, cy - size * 1.75f, paint)
    }
}
