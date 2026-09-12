package br.com.companheirofala

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Personagens da tela inicial em camadas: nenhum card, fundo ou texto é animado.
 * Cada objeto vem da folha PNG transparente e é desenhado/transformado separadamente.
 */
class AnimatedHomeElementsView(context: Context) : View(context) {
    private val sprites = BitmapFactory.decodeResource(resources, R.drawable.lumi_layer_sprites)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val startedAt = System.currentTimeMillis()

    var speaking: Boolean = false
        set(value) { field = value; invalidate() }

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return
        val time = (System.currentTimeMillis() - startedAt) / 1000f
        drawWaterCard(canvas, time)
        drawToiletCard(canvas, time)
        drawToothCard(canvas, time)
        drawPlayCard(canvas, time)
        drawSleepCard(canvas, time)
        drawEmotionCard(canvas, time)
        postInvalidateOnAnimation()
    }

    private fun drawLumi(canvas: Canvas, t: Float) {
        val flight = sin(t * .68f)
        val bob = sin(t * 2.5f) * height * .004f
        val box = rect(.195f + flight * .010f, .002f + bob / height, .405f, .275f)
        // A pose de boca aberta é um frame completo, nunca uma forma desenhada no rosto.
        val talkingFrame = speaking && ((t * 7f).toInt() % 5 in 1..3)
        sprite(canvas, if (talkingFrame) 2 else 1, 3, box, rotation = flight * 3f, scale = 1f + sin(t * 1.2f) * .015f)
    }

    private fun drawWaterCard(canvas: Canvas, t: Float) {
        card(canvas, .04f, .342f, BLUE, "Água")
        val phase = (t % 4.4f).coerceAtMost(1.15f) / 1.15f
        val jump = if (phase >= 1f) 0f else sin(phase * PI).toFloat() * -.030f
        val rotation = if (phase >= 1f) 0f else phase * 360f
        val squash = if (phase < .13f || phase > .86f) .93f else 1.04f
        inCard(canvas, .04f, .342f) { sprite(canvas, 0, 0, rect(.075f, .355f + jump, .220f, .122f), rotation, 1f, squash) }
    }

    private fun drawToiletCard(canvas: Canvas, t: Float) {
        card(canvas, .355f, .342f, GREEN, "Banheiro")
        inCard(canvas, .355f, .342f) {
            sprite(canvas, 1, 0, rect(.415f, .380f, .170f, .115f))
        val cycle = t % 5.2f
        val lidAngle = when {
            cycle < .45f -> -75f * (cycle / .45f)
            cycle < 1.05f -> -75f
            cycle < 1.50f -> -75f * (1f - (cycle - 1.05f) / .45f)
            else -> 0f
        }
        // Tampa independente, com pivô junto à dobradiça inferior.
            sprite(canvas, 2, 0, rect(.435f, .355f, .140f, .105f), rotation = lidAngle, pivotX = .505f, pivotY = .446f)
        }
    }

    private fun drawToothCard(canvas: Canvas, t: Float) {
        card(canvas, .670f, .342f, PINK, "Escovar\nos dentes")
        inCard(canvas, .670f, .342f) {
            sprite(canvas, 0, 1, rect(.735f, .365f, .150f, .130f))
        val active = (t % 4.8f).coerceAtMost(1.55f)
        val x = if (active >= 1.55f) 0f else sin(active * 12f) * .020f
        val angle = if (active >= 1.55f) 0f else sin(active * 12f) * 12f
            sprite(canvas, 1, 1, rect(.785f + x, .360f, .075f, .085f), rotation = angle)
        }
    }

    private fun drawPlayCard(canvas: Canvas, t: Float) {
        card(canvas, .04f, .530f, YELLOW, "Brincar")
        val active = (t % 5.6f).coerceAtMost(2.8f)
        val bearBob = if (active >= 2.8f) 0f else sin(active * 7f) * .004f
        inCard(canvas, .04f, .530f) {
            sprite(canvas, 2, 1, rect(.075f, .552f + bearBob, .145f, .125f))
        val p = if (active >= 2.8f) 0f else active / 2.8f
        val ballX = .205f + sin(p * PI).toFloat() * .060f
        val ballY = .620f - sin(p * PI).toFloat() * .042f + abs(sin(p * PI * 2)).toFloat() * .016f
            sprite(canvas, 0, 2, squareRect(ballX, ballY, .080f), rotation = p * 720f)
        }
    }

    private fun drawSleepCard(canvas: Canvas, t: Float) {
        card(canvas, .355f, .530f, PURPLE, "Dormir")
        inCard(canvas, .355f, .530f) {
            val breathing = 1f + sin(t * PI / 1.1).toFloat() * .020f
            sprite(canvas, 1, 2, rect(.395f, .565f, .220f, .105f), scale = breathing)
            drawZ(canvas, .545f, .575f, t % 3.2f, "Z")
            drawZ(canvas, .575f, .555f, (t + 1.05f) % 3.2f, "z")
            drawZ(canvas, .605f, .535f, (t + 2.1f) % 3.2f, "Z")
        }
    }

    private fun drawEmotionCard(canvas: Canvas, t: Float) {
        card(canvas, .670f, .530f, TEAL, "Como estou\nme sentindo")
        inCard(canvas, .670f, .530f) {
            val angle = t * .65f
            emotion(canvas, 0, angle, .795f, .598f)
            emotion(canvas, 1, angle + 2.09f, .795f, .598f)
            emotion(canvas, 2, angle + 4.18f, .795f, .598f)
        }
    }

    private fun drawBunny(canvas: Canvas, t: Float) {
        val p = (t % 3.8f).coerceAtMost(1.0f)
        val jump = if (p >= 1f) 0f else sin(p * PI).toFloat() * -.020f
        val scaleY = if (p < .15f) .93f else if (p > .85f) 1.05f else 1f
        sprite(canvas, 0, 3, rect(.735f, .715f + jump, .185f, .145f), scaleY = scaleY)
    }

    private fun emotion(canvas: Canvas, index: Int, angle: Float, cx: Float, cy: Float) {
        val radiusX = .060f; val radiusY = .030f
        val x = cx + cos(angle.toDouble()).toFloat() * radiusX
        val y = cy + sin(angle.toDouble()).toFloat() * radiusY
        val cell = source(2, 2)
        val third = cell.width() / 3
        val src = Rect(cell.left + third * index, cell.top, cell.left + third * (index + 1), cell.bottom)
        drawSource(canvas, src, rect(x - .037f, y - .040f, .075f, .080f), scale = 1f + sin(angle * 3f).toFloat() * .04f)
    }

    private fun drawZ(canvas: Canvas, x: Float, y: Float, phase: Float, letter: String) {
        val p = phase / 3.2f
        paint.color = Color.argb(((1f - p) * 255).toInt(), 255, 255, 255)
        paint.textSize = width * (.020f + p * .012f)
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText(letter, width * x, height * (y - p * .055f), paint)
    }

    private fun card(canvas: Canvas, left: Float, top: Float, color: Int, label: String) {
        val r = rect(left, top, .290f, .175f)
        paint.shader = LinearGradient(r.left, r.top, r.right, r.bottom, lighten(color), color, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(r, width * .035f, width * .035f, paint)
        paint.shader = null
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = width * .034f
        paint.typeface = Typeface.DEFAULT_BOLD
        val lines = label.split('\n')
        lines.forEachIndexed { i, line -> canvas.drawText(line, r.centerX(), r.bottom - height * (.022f + (lines.size - i - 1) * .030f), paint) }
        paint.textAlign = Paint.Align.LEFT
    }

    private fun inCard(canvas: Canvas, left: Float, top: Float, block: () -> Unit) {
        val bounds = rect(left, top, .290f, .175f)
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(bounds, width * .035f, width * .035f, Path.Direction.CW) })
        block()
        canvas.restore()
    }

    private fun sprite(canvas: Canvas, col: Int, row: Int, destination: RectF, rotation: Float = 0f, scale: Float = 1f, scaleY: Float = scale, pivotX: Float? = null, pivotY: Float? = null) {
        drawSource(canvas, source(col, row), destination, rotation, scale, scaleY, pivotX, pivotY)
    }

    private fun drawSource(canvas: Canvas, src: Rect, destination: RectF, rotation: Float = 0f, scale: Float = 1f, scaleY: Float = scale, pivotX: Float? = null, pivotY: Float? = null) {
        val px = pivotX?.times(width) ?: destination.centerX()
        val py = pivotY?.times(height) ?: destination.centerY()
        canvas.save()
        canvas.scale(scale, scaleY, px, py)
        canvas.rotate(rotation, px, py)
        canvas.drawBitmap(sprites, src, destination, paint)
        canvas.restore()
    }

    private fun source(col: Int, row: Int): Rect = Rect(sprites.width * col / 3, sprites.height * row / 4, sprites.width * (col + 1) / 3, sprites.height * (row + 1) / 4)
    private fun rect(x: Float, y: Float, w: Float, h: Float) = RectF(width * x, height * y, width * (x + w), height * (y + h))
    private fun squareRect(x: Float, y: Float, sideWidthFraction: Float) = rect(x, y, sideWidthFraction, sideWidthFraction * width / height)
    private fun lighten(color: Int) = Color.rgb(min(255, Color.red(color) + 35), min(255, Color.green(color) + 35), min(255, Color.blue(color) + 35))

    private companion object {
        const val BLUE = 0xFF24B8F1.toInt(); const val GREEN = 0xFF74C948.toInt(); const val PINK = 0xFFF065AD.toInt()
        const val YELLOW = 0xFFFFB72C.toInt(); const val PURPLE = 0xFF7854E8.toInt(); const val TEAL = 0xFF2BC5B5.toInt()
    }
}
