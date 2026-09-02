package br.com.companheirofala

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin

enum class VisualScene { NONE, HORSE, FOOD, TOOTHBRUSH, WATER, TOILET, HANDS, BACTERIA, FEELINGS, HAPPY_FACE, SAD_FACE, ANGRY_FACE }

class ChildVisualView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var scene = VisualScene.NONE
    private var phase = 0f
    fun showScene(s: VisualScene) { scene = s; invalidate() }

    override fun onDraw(c: Canvas) {
        phase += .12f; val w=width.toFloat(); val h=height.toFloat()
        p.style=Paint.Style.FILL; p.color=Color.rgb(242,248,255); c.drawRoundRect(RectF(0f,0f,w,h),38f,38f,p)
        when(scene){
            VisualScene.HORSE->horse(c,w,h); VisualScene.FOOD->food(c,w,h); VisualScene.TOOTHBRUSH->brush(c,w,h)
            VisualScene.WATER->water(c,w,h); VisualScene.TOILET->toilet(c,w,h); VisualScene.HANDS->hands(c,w,h)
            VisualScene.BACTERIA->bacteria(c,w,h); VisualScene.FEELINGS->feelings(c,w,h)
            VisualScene.HAPPY_FACE->face(c,w,h,0); VisualScene.SAD_FACE->face(c,w,h,1); VisualScene.ANGRY_FACE->face(c,w,h,2); else->Unit
        }
        if(scene==VisualScene.BACTERIA) postInvalidateDelayed(60)
    }

    private fun horse(c:Canvas,w:Float,h:Float){
        // unmistakable friendly side-view horse: four legs, long muzzle, mane, ears and tail
        p.color=Color.rgb(184,119,68); c.drawOval(RectF(w*.18f,h*.35f,w*.70f,h*.70f),p)
        c.drawRoundRect(RectF(w*.58f,h*.25f,w*.69f,h*.52f),30f,30f,p)
        c.drawOval(RectF(w*.60f,h*.15f,w*.84f,h*.42f),p); c.drawOval(RectF(w*.73f,h*.27f,w*.91f,h*.40f),p)
        p.color=Color.rgb(126,76,45); c.drawOval(RectF(w*.62f,h*.08f,w*.68f,h*.22f),p); c.drawOval(RectF(w*.72f,h*.08f,w*.78f,h*.22f),p)
        val legs=floatArrayOf(.25f,.39f,.56f,.65f); p.color=Color.rgb(170,103,59)
        legs.forEach { x->c.drawRoundRect(RectF(w*x,h*.62f,w*(x+.055f),h*.90f),14f,14f,p); p.color=Color.rgb(70,50,42); c.drawRect(w*x,h*.86f,w*(x+.06f),h*.92f,p); p.color=Color.rgb(170,103,59) }
        p.color=Color.rgb(91,55,38); c.drawCircle(w*.76f,h*.23f,6f,p)
        p.style=Paint.Style.STROKE;p.strokeWidth=18f;p.strokeCap=Paint.Cap.ROUND;c.drawArc(RectF(w*.05f,h*.32f,w*.27f,h*.65f),120f,120f,false,p)
        p.strokeWidth=15f;c.drawArc(RectF(w*.52f,h*.13f,w*.70f,h*.49f),105f,125f,false,p);p.style=Paint.Style.FILL
        p.color=Color.WHITE;c.drawCircle(w*.755f,h*.225f,2f,p)
    }
    private fun food(c:Canvas,w:Float,h:Float){p.color=Color.WHITE;c.drawOval(RectF(w*.22f,h*.25f,w*.78f,h*.78f),p);p.color=Color.rgb(255,190,70);c.drawCircle(w*.5f,h*.5f,h*.16f,p);p.color=Color.rgb(102,190,92);c.drawCircle(w*.36f,h*.52f,h*.08f,p)}
    private fun brush(c:Canvas,w:Float,h:Float){c.save();c.rotate(-15f,w/2,h/2);p.color=Color.rgb(70,160,255);c.drawRoundRect(RectF(w*.18f,h*.48f,w*.82f,h*.62f),25f,25f,p);p.color=Color.WHITE;(0..5).forEach{i->c.drawRect(w*.66f+i*10,h*.37f,w*.69f+i*10,h*.49f,p)};c.restore()}
    private fun water(c:Canvas,w:Float,h:Float){p.color=Color.rgb(70,180,245);c.drawRoundRect(RectF(w*.34f,h*.18f,w*.66f,h*.82f),25f,25f,p);p.color=Color.argb(150,255,255,255);c.drawRect(w*.39f,h*.25f,w*.43f,h*.70f,p)}
    private fun toilet(c:Canvas,w:Float,h:Float){p.color=Color.WHITE;c.drawRoundRect(RectF(w*.30f,h*.18f,w*.68f,h*.43f),22f,22f,p);c.drawOval(RectF(w*.29f,h*.38f,w*.72f,h*.70f),p);c.drawRoundRect(RectF(w*.42f,h*.62f,w*.61f,h*.87f),18f,18f,p);p.color=Color.rgb(110,205,255);c.drawOval(RectF(w*.38f,h*.45f,w*.64f,h*.59f),p)}
    private fun hands(c:Canvas,w:Float,h:Float){p.color=Color.rgb(255,200,158);c.drawCircle(w*.38f,h*.55f,h*.17f,p);c.drawCircle(w*.62f,h*.55f,h*.17f,p);p.color=Color.rgb(75,180,245);(0..7).forEach{i->c.drawCircle(w*(.18f+.09f*i),h*(.25f+.04f*(i%2)),9f,p)}}
    private fun bacteria(c:Canvas,w:Float,h:Float){val colors=intArrayOf(Color.rgb(95,210,110),Color.rgb(245,105,155),Color.rgb(255,190,55));(0..5).forEach{i->val x=w*(.16f+.135f*i);val y=h*(.35f+.22f*(i%2))+sin(phase+i).toFloat()*10;p.color=colors[i%3];c.drawCircle(x,y,30f,p);p.color=Color.BLACK;c.drawCircle(x-9,y-4,4f,p);c.drawCircle(x+9,y-4,4f,p)}}
    private fun feelings(c:Canvas,w:Float,h:Float){faceAt(c,w*.23f,h*.5f,h*.18f,0);faceAt(c,w*.5f,h*.5f,h*.18f,1);faceAt(c,w*.77f,h*.5f,h*.18f,2)}
    private fun face(c:Canvas,w:Float,h:Float,type:Int)=faceAt(c,w/2,h/2,h*.30f,type)
    private fun faceAt(c:Canvas,x:Float,y:Float,r:Float,type:Int){p.color=when(type){0->Color.rgb(255,210,65);1->Color.rgb(105,185,255);else->Color.rgb(255,120,95)};c.drawCircle(x,y,r,p);p.color=Color.rgb(45,50,65);c.drawCircle(x-r*.35f,y-r*.18f,r*.08f,p);c.drawCircle(x+r*.35f,y-r*.18f,r*.08f,p);p.style=Paint.Style.STROKE;p.strokeWidth=r*.08f;if(type==0)c.drawArc(RectF(x-r*.45f,y-r*.05f,x+r*.45f,y+r*.48f),15f,150f,false,p) else c.drawArc(RectF(x-r*.40f,y+r*.20f,x+r*.40f,y+r*.58f),200f,140f,false,p);p.style=Paint.Style.FILL}
}
