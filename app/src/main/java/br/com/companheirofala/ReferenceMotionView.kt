package br.com.companheirofala

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import kotlin.math.abs
import kotlin.math.sin

/** Anima partes da arte aprovada sem substituir o layout nem o motor conversacional. */
class ReferenceMotionView(context: Context) : View(context) {
    private val art = BitmapFactory.decodeResource(resources, R.drawable.lumi_home_reference)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val mouthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(173, 63, 83) }
    private val startedAt = System.currentTimeMillis()

    var speaking: Boolean = false
        set(value) { field = value; invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        val time = (System.currentTimeMillis() - startedAt) / 1000f

        // Lumi: voo lateral e leve balanço de asas.
        drawCrop(canvas, .045f, .075f, .455f, .340f, dx = sin(time * 1.3f) * width * .012f, dy = sin(time * 2f) * height * .006f)
        if (speaking) drawMouth(canvas, time)

        // Personagens dos cartões: movimentos pequenos para manter a arte legível.
        drawCrop(canvas, .085f, .355f, .295f, .480f, dy = sin(time * 4f) * -height * .007f, clipOval = true) // gota
        drawCrop(canvas, .395f, .360f, .625f, .485f, rotation = sin(time * 2.5f) * 3.5f, clipOval = true) // vaso
        drawCrop(canvas, .720f, .355f, .925f, .480f, dy = abs(sin(time * 3.5f)) * -height * .010f, clipOval = true) // dente
        drawCrop(canvas, .070f, .545f, .310f, .675f, dx = sin(time * 3.2f) * width * .008f, rotation = sin(time * 3.2f) * 3f, clipOval = true) // ursinho
        drawCrop(canvas, .395f, .548f, .620f, .680f, rotation = sin(time * 1.9f) * 3f, clipOval = true) // lua
        drawCrop(canvas, .705f, .548f, .935f, .675f, dy = sin(time * 3.2f) * -height * .006f, clipOval = true) // carinhas
        postInvalidateOnAnimation()
    }

    private fun drawMouth(canvas: Canvas, time: Float) {
        val cx = width * .268f
        val cy = height * .190f
        val open = height * (.005f + abs(sin(time * 12f)) * .004f)
        canvas.drawOval(RectF(cx - width * .018f, cy - open, cx + width * .018f, cy + open), mouthPaint)
    }

    private fun drawCrop(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        dx: Float = 0f,
        dy: Float = 0f,
        rotation: Float = 0f,
        clipOval: Boolean = false
    ) {
        val src = Rect((art.width * left).toInt(), (art.height * top).toInt(), (art.width * right).toInt(), (art.height * bottom).toInt())
        val dst = RectF(width * left, height * top, width * right, height * bottom)
        canvas.save()
        if (clipOval) {
            val path = Path().apply { addOval(RectF(dst.left + width * .005f, dst.top + height * .003f, dst.right - width * .005f, dst.bottom - height * .003f), Path.Direction.CW) }
            canvas.clipPath(path)
        }
        canvas.rotate(rotation, dst.centerX(), dst.centerY())
        canvas.translate(dx, dy)
        canvas.drawBitmap(art, src, dst, paint)
        canvas.restore()
    }
}
