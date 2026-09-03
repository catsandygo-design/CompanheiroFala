package br.com.companheirofala

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
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
        phase += .10f
        val w = width.toFloat(); val h = height.toFloat()
        p.style = Paint.Style.FILL
        p.color = Color.rgb(248, 250, 255)
        c.drawRoundRect(RectF(0f, 0f, w, h), 34f, 34f, p)
        when (scene) {
            VisualScene.HORSE -> horse(c, w, h)
            VisualScene.FOOD -> food(c, w, h)
            VisualScene.TOOTHBRUSH -> brush(c, w, h)
            VisualScene.WATER -> water(c, w, h)
            VisualScene.TOILET -> toilet(c, w, h)
            VisualScene.HANDS -> hands(c, w, h)
            VisualScene.BACTERIA -> bacteria(c, w, h)
            VisualScene.FEELINGS -> feelings(c, w, h)
            VisualScene.HAPPY_FACE -> face(c, w/2, h/2, h*.27f, 0)
            VisualScene.SAD_FACE -> face(c, w/2, h/2, h*.27f, 1)
            VisualScene.ANGRY_FACE -> face(c, w/2, h/2, h*.27f, 2)
            VisualScene.SCARED_FACE -> face(c, w/2, h/2, h*.27f, 3)
            VisualScene.TIRED_FACE -> face(c, w/2, h/2, h*.27f, 4)
            VisualScene.EXCITED_FACE -> face(c, w/2, h/2, h*.27f, 5)
            else -> Unit
        }
        if (scene == VisualScene.BACTERIA) postInvalidateDelayed(60)
    }

    private fun horse(c: Canvas, w: Float, h: Float) {
        // Fundo de campo para dar contexto visual imediato.
        p.color = Color.rgb(218, 239, 255)
        c.drawRoundRect(RectF(w*.03f,h*.05f,w*.97f,h*.95f),28f,28f,p)
        p.color = Color.rgb(166, 220, 139)
        c.drawRoundRect(RectF(w*.03f,h*.67f,w*.97f,h*.95f),0f,0f,p)

        val body = Color.rgb(171, 105, 62)
        val dark = Color.rgb(83, 52, 39)
        val muzzle = Color.rgb(215, 155, 113)

        // Corpo e peito.
        p.color = body
        c.drawOval(RectF(w*.20f,h*.35f,w*.68f,h*.70f),p)
        c.drawOval(RectF(w*.56f,h*.31f,w*.71f,h*.66f),p)

        // Pescoço inclinado.
        val neck = Path().apply {
            moveTo(w*.57f,h*.39f)
            lineTo(w*.64f,h*.17f)
            lineTo(w*.75f,h*.22f)
            lineTo(w*.69f,h*.52f)
            close()
        }
        c.drawPath(neck,p)

        // Cabeça alongada com focinho claro.
        c.save(); c.rotate(-8f,w*.74f,h*.23f)
        c.drawOval(RectF(w*.66f,h*.12f,w*.84f,h*.34f),p)
        p.color = muzzle
        c.drawOval(RectF(w*.77f,h*.21f,w*.91f,h*.35f),p)
        c.restore()

        // Orelhas pontudas.
        p.color = body
        val ear1 = Path().apply { moveTo(w*.69f,h*.15f); lineTo(w*.69f,h*.04f); lineTo(w*.75f,h*.14f); close() }
        val ear2 = Path().apply { moveTo(w*.76f,h*.15f); lineTo(w*.79f,h*.05f); lineTo(w*.82f,h*.17f); close() }
        c.drawPath(ear1,p); c.drawPath(ear2,p)

        // Crina.
        p.color = dark
        val mane = Path().apply {
            moveTo(w*.64f,h*.16f); lineTo(w*.59f,h*.23f); lineTo(w*.64f,h*.26f)
            lineTo(w*.58f,h*.31f); lineTo(w*.64f,h*.34f); lineTo(w*.59f,h*.40f)
            lineTo(w*.66f,h*.42f); lineTo(w*.69f,h*.19f); close()
        }
        c.drawPath(mane,p)

        // Quatro pernas com joelhos e cascos.
        val xs = floatArrayOf(.27f,.38f,.56f,.63f)
        xs.forEachIndexed { i,x ->
            p.color = body
            val top = if (i < 2) .61f else .58f
            c.drawRoundRect(RectF(w*x,h*top,w*(x+.055f),h*.86f),13f,13f,p)
            p.color = dark
            c.drawRoundRect(RectF(w*(x-.006f),h*.82f,w*(x+.062f),h*.89f),8f,8f,p)
        }

        // Cauda longa com fios.
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND; p.strokeWidth = 15f; p.color = dark
        c.drawArc(RectF(w*.08f,h*.35f,w*.29f,h*.72f),105f,125f,false,p)
        p.strokeWidth = 5f
        c.drawLine(w*.11f,h*.63f,w*.08f,h*.83f,p)
        c.drawLine(w*.14f,h*.63f,w*.13f,h*.85f,p)
        p.style = Paint.Style.FILL

        // Olho, narina e brilho.
        p.color = Color.rgb(35,35,38)
        c.drawCircle(w*.775f,h*.20f,7f,p)
        c.drawCircle(w*.86f,h*.29f,5f,p)
        p.color = Color.WHITE
        c.drawCircle(w*.777f,h*.197f,2.3f,p)
    }

    private fun food(c: Canvas,w:Float,h:Float){
        p.color=Color.rgb(230,244,255);c.drawCircle(w*.5f,h*.5f,h*.34f,p)
        p.color=Color.WHITE;c.drawCircle(w*.5f,h*.5f,h*.27f,p)
        p.color=Color.rgb(255,186,66);c.drawCircle(w*.50f,h*.48f,h*.11f,p)
        p.color=Color.rgb(102,190,92);c.drawCircle(w*.38f,h*.55f,h*.07f,p);c.drawCircle(w*.62f,h*.55f,h*.07f,p)
    }

    private fun brush(c: Canvas,w:Float,h:Float){
        c.save();c.rotate(-14f,w/2,h/2)
        p.color=Color.rgb(87,165,245);c.drawRoundRect(RectF(w*.18f,h*.48f,w*.82f,h*.61f),25f,25f,p)
        p.color=Color.WHITE;(0..5).forEach{i->c.drawRoundRect(RectF(w*(.66f+i*.025f),h*.35f,w*(.68f+i*.025f),h*.50f),5f,5f,p)}
        c.restore()
    }

    private fun water(c: Canvas,w:Float,h:Float){
        p.color=Color.rgb(214,241,255);c.drawRoundRect(RectF(w*.32f,h*.15f,w*.68f,h*.86f),26f,26f,p)
        p.color=Color.rgb(79,181,239);c.drawRoundRect(RectF(w*.35f,h*.38f,w*.65f,h*.81f),20f,20f,p)
        p.color=Color.argb(130,255,255,255);c.drawRoundRect(RectF(w*.39f,h*.22f,w*.43f,h*.72f),12f,12f,p)
    }

    private fun toilet(c: Canvas,w:Float,h:Float){
        p.color=Color.rgb(226,244,255);c.drawRoundRect(RectF(w*.25f,h*.14f,w*.70f,h*.42f),24f,24f,p)
        p.color=Color.WHITE;c.drawOval(RectF(w*.28f,h*.36f,w*.73f,h*.70f),p);c.drawRoundRect(RectF(w*.43f,h*.61f,w*.61f,h*.88f),16f,16f,p)
        p.color=Color.rgb(98,195,238);c.drawOval(RectF(w*.37f,h*.44f,w*.64f,h*.59f),p)
    }

    private fun hands(c: Canvas,w:Float,h:Float){
        p.color=Color.rgb(255,207,171);c.drawCircle(w*.38f,h*.55f,h*.16f,p);c.drawCircle(w*.62f,h*.55f,h*.16f,p)
        p.color=Color.rgb(79,181,239);(0..7).forEach{i->c.drawCircle(w*(.17f+.095f*i),h*(.26f+.035f*(i%2)),8f,p)}
    }

    private fun bacteria(c: Canvas,w:Float,h:Float){
        val colors=intArrayOf(Color.rgb(100,205,119),Color.rgb(239,111,159),Color.rgb(250,190,71))
        (0..5).forEach{i->val x=w*(.16f+.135f*i);val y=h*(.36f+.22f*(i%2))+sin(phase+i).toFloat()*8;p.color=colors[i%3];c.drawCircle(x,y,28f,p);p.color=Color.rgb(45,45,50);c.drawCircle(x-8,y-4,3.5f,p);c.drawCircle(x+8,y-4,3.5f,p)}
    }

    private fun feelings(c: Canvas,w:Float,h:Float){
        face(c,w*.18f,h*.34f,h*.11f,0);face(c,w*.50f,h*.34f,h*.11f,1);face(c,w*.82f,h*.34f,h*.11f,2)
        face(c,w*.18f,h*.72f,h*.11f,3);face(c,w*.50f,h*.72f,h*.11f,4);face(c,w*.82f,h*.72f,h*.11f,5)
    }

    private fun face(c:Canvas,x:Float,y:Float,r:Float,type:Int){
        p.style=Paint.Style.FILL
        p.color=when(type){0->Color.rgb(255,211,79);1->Color.rgb(108,188,246);2->Color.rgb(244,116,105);3->Color.rgb(170,145,236);4->Color.rgb(157,173,188);else->Color.rgb(255,155,83)}
        c.drawCircle(x,y,r,p)
        p.color=Color.rgb(52,54,63)
        c.drawCircle(x-r*.35f,y-r*.18f,r*.075f,p);c.drawCircle(x+r*.35f,y-r*.18f,r*.075f,p)
        p.style=Paint.Style.STROKE;p.strokeWidth=r*.075f;p.strokeCap=Paint.Cap.ROUND
        when(type){0,5->c.drawArc(RectF(x-r*.43f,y-r*.02f,x+r*.43f,y+r*.48f),15f,150f,false,p);3->{p.style=Paint.Style.FILL;c.drawCircle(x,y+r*.26f,r*.12f,p)};else->c.drawArc(RectF(x-r*.40f,y+r*.18f,x+r*.40f,y+r*.55f),200f,140f,false,p)}
        p.style=Paint.Style.FILL
    }
}
