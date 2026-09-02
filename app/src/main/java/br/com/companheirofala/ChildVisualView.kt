package br.com.companheirofala

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin

enum class VisualScene {
    NONE, HORSE, FOOD, TOOTHBRUSH, WATER, TOILET, HANDS, BACTERIA,
    FEELINGS, HAPPY_FACE, SAD_FACE, ANGRY_FACE, SCARED_FACE, TIRED_FACE, EXCITED_FACE
}

class ChildVisualView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var scene = VisualScene.NONE
    private var phase = 0f
    fun showScene(s: VisualScene) { scene = s; invalidate() }

    override fun onDraw(c: Canvas) {
        phase += .12f
        val w = width.toFloat(); val h = height.toFloat()
        p.style = Paint.Style.FILL
        p.color = Color.rgb(246, 250, 255)
        c.drawRoundRect(RectF(0f, 0f, w, h), 38f, 38f, p)
        when (scene) {
            VisualScene.HORSE -> horse(c, w, h)
            VisualScene.FOOD -> food(c, w, h)
            VisualScene.TOOTHBRUSH -> brush(c, w, h)
            VisualScene.WATER -> water(c, w, h)
            VisualScene.TOILET -> toilet(c, w, h)
            VisualScene.HANDS -> hands(c, w, h)
            VisualScene.BACTERIA -> bacteria(c, w, h)
            VisualScene.FEELINGS -> feelings(c, w, h)
            VisualScene.HAPPY_FACE -> face(c, w, h, 0)
            VisualScene.SAD_FACE -> face(c, w, h, 1)
            VisualScene.ANGRY_FACE -> face(c, w, h, 2)
            VisualScene.SCARED_FACE -> face(c, w, h, 3)
            VisualScene.TIRED_FACE -> face(c, w, h, 4)
            VisualScene.EXCITED_FACE -> face(c, w, h, 5)
            else -> Unit
        }
        if (scene == VisualScene.BACTERIA) postInvalidateDelayed(60)
    }

    private fun horse(c: Canvas, w: Float, h: Float) {
        // Friendly side-view horse with clear silhouette: body, neck, long muzzle, ears, mane, tail and four legs.
        p.color = Color.rgb(186, 120, 70)
        c.drawOval(RectF(w*.16f, h*.34f, w*.68f, h*.70f), p)
        c.drawRoundRect(RectF(w*.56f, h*.25f, w*.69f, h*.53f), 28f, 28f, p)
        c.drawOval(RectF(w*.60f, h*.14f, w*.83f, h*.42f), p)
        c.drawOval(RectF(w*.72f, h*.26f, w*.92f, h*.41f), p)
        p.color = Color.rgb(122, 75, 44)
        c.drawOval(RectF(w*.62f, h*.07f, w*.68f, h*.22f), p)
        c.drawOval(RectF(w*.72f, h*.07f, w*.78f, h*.22f), p)
        val legs = floatArrayOf(.23f, .37f, .54f, .64f)
        legs.forEach { x ->
            p.color = Color.rgb(170, 103, 59)
            c.drawRoundRect(RectF(w*x, h*.62f, w*(x+.055f), h*.90f), 14f, 14f, p)
            p.color = Color.rgb(65, 48, 41)
            c.drawRect(w*x, h*.86f, w*(x+.06f), h*.92f, p)
        }
        p.color = Color.rgb(82, 52, 36)
        c.drawCircle(w*.76f, h*.23f, 7f, p)
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND; p.strokeWidth = 18f
        c.drawArc(RectF(w*.04f, h*.31f, w*.27f, h*.68f), 115f, 125f, false, p)
        p.strokeWidth = 15f
        c.drawArc(RectF(w*.51f, h*.12f, w*.70f, h*.50f), 105f, 130f, false, p)
        p.style = Paint.Style.FILL
        p.color = Color.WHITE
        c.drawCircle(w*.758f, h*.228f, 2.5f, p)
    }

    private fun food(c: Canvas, w: Float, h: Float) {
        p.color = Color.WHITE; c.drawOval(RectF(w*.22f,h*.25f,w*.78f,h*.78f),p)
        p.color = Color.rgb(255,190,70); c.drawCircle(w*.5f,h*.5f,h*.16f,p)
        p.color = Color.rgb(102,190,92); c.drawCircle(w*.36f,h*.52f,h*.08f,p)
    }
    private fun brush(c: Canvas, w: Float, h: Float) {
        c.save(); c.rotate(-15f,w/2,h/2)
        p.color=Color.rgb(70,160,255); c.drawRoundRect(RectF(w*.18f,h*.48f,w*.82f,h*.62f),25f,25f,p)
        p.color=Color.WHITE; (0..5).forEach{i->c.drawRect(w*.66f+i*10,h*.37f,w*.69f+i*10,h*.49f,p)}
        c.restore()
    }
    private fun water(c: Canvas, w: Float, h: Float) {
        p.color=Color.rgb(70,180,245); c.drawRoundRect(RectF(w*.34f,h*.18f,w*.66f,h*.82f),25f,25f,p)
        p.color=Color.argb(150,255,255,255); c.drawRect(w*.39f,h*.25f,w*.43f,h*.70f,p)
    }
    private fun toilet(c: Canvas, w: Float, h: Float) {
        p.color=Color.WHITE; c.drawRoundRect(RectF(w*.30f,h*.18f,w*.68f,h*.43f),22f,22f,p)
        c.drawOval(RectF(w*.29f,h*.38f,w*.72f,h*.70f),p); c.drawRoundRect(RectF(w*.42f,h*.62f,w*.61f,h*.87f),18f,18f,p)
        p.color=Color.rgb(110,205,255); c.drawOval(RectF(w*.38f,h*.45f,w*.64f,h*.59f),p)
    }
    private fun hands(c: Canvas, w: Float, h: Float) {
        p.color=Color.rgb(255,200,158); c.drawCircle(w*.38f,h*.55f,h*.17f,p); c.drawCircle(w*.62f,h*.55f,h*.17f,p)
        p.color=Color.rgb(75,180,245); (0..7).forEach{i->c.drawCircle(w*(.18f+.09f*i),h*(.25f+.04f*(i%2)),9f,p)}
    }
    private fun bacteria(c: Canvas, w: Float, h: Float) {
        val colors=intArrayOf(Color.rgb(95,210,110),Color.rgb(245,105,155),Color.rgb(255,190,55))
        (0..5).forEach{i->
            val x=w*(.16f+.135f*i); val y=h*(.35f+.22f*(i%2))+sin(phase+i).toFloat()*10
            p.color=colors[i%3]; c.drawCircle(x,y,30f,p); p.color=Color.BLACK
            c.drawCircle(x-9,y-4,4f,p); c.drawCircle(x+9,y-4,4f,p)
        }
    }

    private fun feelings(c: Canvas, w: Float, h: Float) {
        faceAt(c,w*.18f,h*.34f,h*.12f,0); faceAt(c,w*.50f,h*.34f,h*.12f,1); faceAt(c,w*.82f,h*.34f,h*.12f,2)
        faceAt(c,w*.18f,h*.72f,h*.12f,3); faceAt(c,w*.50f,h*.72f,h*.12f,4); faceAt(c,w*.82f,h*.72f,h*.12f,5)
    }

    private fun face(c: Canvas, w: Float, h: Float, type: Int) = faceAt(c,w/2,h/2,h*.30f,type)

    private fun faceAt(c: Canvas, x: Float, y: Float, r: Float, type: Int) {
        p.style=Paint.Style.FILL
        p.color = when(type){
            0->Color.rgb(255,210,65); 1->Color.rgb(105,185,255); 2->Color.rgb(255,120,95)
            3->Color.rgb(177,145,245); 4->Color.rgb(150,170,190); else->Color.rgb(255,151,75)
        }
        c.drawCircle(x,y,r,p)
        p.color=Color.rgb(45,50,65)
        when(type){
            4 -> { p.style=Paint.Style.STROKE; p.strokeWidth=r*.08f; c.drawLine(x-r*.48f,y-r*.12f,x-r*.20f,y-r*.08f,p); c.drawLine(x+r*.20f,y-r*.08f,x+r*.48f,y-r*.12f,p) }
            5 -> { c.drawCircle(x-r*.35f,y-r*.18f,r*.10f,p); c.drawCircle(x+r*.35f,y-r*.18f,r*.10f,p) }
            else -> { c.drawCircle(x-r*.35f,y-r*.18f,r*.08f,p); c.drawCircle(x+r*.35f,y-r*.18f,r*.08f,p) }
        }
        p.style=Paint.Style.STROKE; p.strokeWidth=r*.08f
        when(type){
            0,5 -> c.drawArc(RectF(x-r*.45f,y-r*.05f,x+r*.45f,y+r*.48f),15f,150f,false,p)
            3 -> { p.style=Paint.Style.FILL; c.drawCircle(x,y+r*.28f,r*.13f,p) }
            else -> c.drawArc(RectF(x-r*.40f,y+r*.20f,x+r*.40f,y+r*.58f),200f,140f,false,p)
        }
        p.style=Paint.Style.FILL
    }
}
