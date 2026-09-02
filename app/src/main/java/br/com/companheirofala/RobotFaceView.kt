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

enum class RobotMood { IDLE, LISTENING, THINKING, SPEAKING, HAPPY, CONFUSED, CURIOUS, SURPRISED, SAD, PROUD }

class RobotFaceView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handler = Handler(Looper.getMainLooper())
    private var mood = RobotMood.IDLE
    private var blink = false
    private var phase = 0f
    private var voiceLevel = 0f

    private val animator = object : Runnable {
        override fun run() { phase += 0.13f; invalidate(); handler.postDelayed(this, 45) }
    }
    private val blinker = object : Runnable {
        override fun run() {
            if (mood != RobotMood.LISTENING) {
                blink = true; invalidate(); handler.postDelayed({ blink = false; invalidate() }, 115)
            }
            handler.postDelayed(this, 2200L + (Math.random() * 1800L).toLong())
        }
    }

    init { handler.post(animator); handler.postDelayed(blinker, 900) }
    fun setMood(newMood: RobotMood) { mood = newMood; invalidate() }
    fun setVoiceLevel(rmsDb: Float) { voiceLevel = min(1f, max(0f, (rmsDb + 2f) / 12f)); invalidate() }
    override fun onDetachedFromWindow() { handler.removeCallbacksAndMessages(null); super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat(); if (w <= 0f || h <= 0f) return
        val cx = w / 2f; val cy = h / 2f
        val bob = when (mood) {
            RobotMood.SPEAKING -> sin((phase * 2).toDouble()).toFloat() * 5f
            RobotMood.HAPPY, RobotMood.PROUD -> sin(phase.toDouble()).toFloat() * 3f
            else -> sin((phase * .55f).toDouble()).toFloat() * 2f
        }
        val glow = when (mood) {
            RobotMood.LISTENING -> Color.rgb(53,224,255)
            RobotMood.THINKING, RobotMood.CURIOUS -> Color.rgb(165,116,255)
            RobotMood.SPEAKING -> Color.rgb(74,210,255)
            RobotMood.HAPPY, RobotMood.PROUD -> Color.rgb(75,244,176)
            RobotMood.CONFUSED, RobotMood.SURPRISED -> Color.rgb(255,184,83)
            RobotMood.SAD -> Color.rgb(104,164,255)
            RobotMood.IDLE -> Color.rgb(68,153,255)
        }
        val panel = RectF(w*.055f,h*.08f+bob,w*.945f,h*.92f+bob)
        paint.style=Paint.Style.FILL; paint.color=Color.rgb(9,18,35); canvas.drawRoundRect(panel,72f,72f,paint)
        paint.style=Paint.Style.STROKE; paint.strokeWidth=4f; paint.color=Color.argb(135,Color.red(glow),Color.green(glow),Color.blue(glow)); canvas.drawRoundRect(panel,72f,72f,paint)
        if (mood==RobotMood.LISTENING) {
            val pulse=7f+voiceLevel*20f+(sin((phase*2.2).toDouble()).toFloat()+1f)*3f
            paint.strokeWidth=3f+voiceLevel*4f; paint.color=Color.argb(95,53,224,255)
            canvas.drawRoundRect(RectF(panel.left-pulse,panel.top-pulse,panel.right+pulse,panel.bottom+pulse),78f,78f,paint)
        }
        val eyeY=cy-h*.045f+bob; val spread=w*.225f; val scale=1f+if(mood==RobotMood.LISTENING)voiceLevel*.12f else 0f
        drawEye(canvas,cx-spread,eyeY,true,scale,glow); drawEye(canvas,cx+spread,eyeY,false,scale,glow)
        drawMouth(canvas,cx,cy+h*.20f+bob,glow)
    }

    private fun drawEye(c: Canvas, x: Float, y: Float, left: Boolean, scale: Float, color: Int) {
        paint.color=color
        if(blink){paint.style=Paint.Style.FILL;c.drawRoundRect(RectF(x-52f,y-5f,x+52f,y+6f),10f,10f,paint);return}
        when(mood){
            RobotMood.HAPPY, RobotMood.PROUD -> {paint.style=Paint.Style.STROKE;paint.strokeWidth=13f;c.drawArc(RectF(x-52f,y-22f,x+52f,y+62f),200f,140f,false,paint)}
            RobotMood.SAD -> {paint.style=Paint.Style.STROKE;paint.strokeWidth=13f;c.drawArc(RectF(x-50f,y-60f,x+50f,y+20f),20f,140f,false,paint)}
            RobotMood.CONFUSED -> {paint.style=Paint.Style.FILL;c.save();c.rotate(if(left)-9f else 9f,x,y);c.drawRoundRect(RectF(x-45f,y-25f,x+45f,y+28f),24f,24f,paint);c.restore()}
            RobotMood.SURPRISED -> {paint.style=Paint.Style.STROKE;paint.strokeWidth=12f;c.drawCircle(x,y,39f,paint)}
            RobotMood.CURIOUS -> {paint.style=Paint.Style.FILL;val oy=if(left)-9f else 4f;c.drawOval(RectF(x-39f,y-31f+oy,x+39f,y+31f+oy),paint)}
            RobotMood.THINKING -> {paint.style=Paint.Style.FILL;val ox=cos(phase.toDouble()).toFloat()*14f;val oy=sin((phase*.7).toDouble()).toFloat()*6f;c.drawOval(RectF(x-39f+ox,y-31f+oy,x+39f+ox,y+31f+oy),paint)}
            else -> {paint.style=Paint.Style.FILL;val bounce=if(mood==RobotMood.SPEAKING)sin((phase*2.5).toDouble()).toFloat()*5f else 0f;val hw=43f*scale;val hh=31f*scale;c.drawRoundRect(RectF(x-hw,y-hh+bounce,x+hw,y+hh+bounce),28f,28f,paint);if(mood==RobotMood.LISTENING){paint.color=Color.rgb(8,20,36);c.drawCircle(x+sin((phase*.45).toDouble()).toFloat()*5f,y,10f+voiceLevel*2f,paint)}}
        }
    }

    private fun drawMouth(c:Canvas,x:Float,y:Float,color:Int){paint.color=color;paint.strokeWidth=9f;paint.style=Paint.Style.STROKE;when(mood){
        RobotMood.HAPPY,RobotMood.PROUD->c.drawArc(RectF(x-62f,y-34f,x+62f,y+48f),15f,150f,false,paint)
        RobotMood.SAD->c.drawArc(RectF(x-55f,y+4f,x+55f,y+55f),200f,140f,false,paint)
        RobotMood.SURPRISED->{paint.style=Paint.Style.FILL;c.drawCircle(x,y,22f,paint)}
        RobotMood.SPEAKING->{paint.style=Paint.Style.FILL;val open=16f+(sin((phase*3.2).toDouble()).toFloat()+1f)*12f;c.drawOval(RectF(x-35f,y-open,x+35f,y+open),paint)}
        RobotMood.CONFUSED->c.drawLine(x-38f,y+7f,x+38f,y-7f,paint)
        RobotMood.THINKING,RobotMood.CURIOUS->{paint.style=Paint.Style.FILL;c.drawCircle(x+7f,y,10f,paint)}
        RobotMood.LISTENING->{val smile=8f+voiceLevel*12f;c.drawArc(RectF(x-46f,y-smile,x+46f,y+30f+smile),22f,136f,false,paint)}
        RobotMood.IDLE->c.drawArc(RectF(x-46f,y-13f,x+46f,y+31f),20f,140f,false,paint)
    }}
}
