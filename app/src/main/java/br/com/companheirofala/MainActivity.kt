package br.com.companheirofala

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.hardware.*
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import kotlin.math.abs

class MainActivity : Activity(), SensorEventListener {
    private lateinit var fairy: FairyCompanionView; private lateinit var visual: ChildVisualView; private lateinit var speechBubble: TextView; private lateinit var choices: GridLayout; private lateinit var status: TextView; private lateinit var updater: AppUpdater; private lateinit var voice: VoiceEngine; private lateinit var speech: SpeechEngine; private lateinit var events: ParentEventRepository
    private val profile=ChildProfile.gabi(); private val engine=ConversationEngine(profile); private var waitingForMovement=false; private var autoListenAfterSpeech=false; private var baselineAcceleration=0f; private var lastMovementAt=0L; private var sensorManager:SensorManager?=null

    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContentView(buildScreen());updater=AppUpdater(this);events=ParentEventRepository(this)
        speech=SpeechEngine(this,{s->runOnUiThread{renderInteractionState(s)}},{l->runOnUiThread{fairy.setVoiceLevel(l)}},{t->runOnUiThread{handleSpoken(t)}},{runOnUiThread{status.text="Não ouvi direitinho. Toca na estrela e fala de novo."}})
        voice=VoiceEngine(this,{runOnUiThread{speech.markSpeaking();fairy.setMood(RobotMood.SPEAKING)}},{runOnUiThread{speech.markIdle();fairy.setMood(RobotMood.HAPPY);if(autoListenAfterSpeech&&!waitingForMovement){autoListenAfterSpeech=false;status.postDelayed({startListening()},600)}}},{runOnUiThread{speech.markIdle();status.text="A voz não está disponível neste aparelho."}})
        sensorManager=getSystemService(Context.SENSOR_SERVICE) as SensorManager;sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let{sensorManager?.registerListener(this,it,SensorManager.SENSOR_DELAY_NORMAL)}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),10)
        val intro=engine.start(PlayMode.HOME);renderReply(intro);speakReply(intro);status.postDelayed({updater.checkAndUpdate{m->status.text=m}},1400)}

    private fun buildScreen():View{val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(10),dp(6),dp(10),dp(8));background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(Color.rgb(92,55,139),Color.rgb(236,126,183),Color.rgb(105,193,221)))}
        root.addView(TextView(this).apply{text="MEU CORAÇÃO • v0.10";textSize=13f;setTextColor(Color.WHITE);gravity=Gravity.CENTER;setTypeface(typeface,1)},LinearLayout.LayoutParams(-1,dp(26)))
        fairy=FairyCompanionView(this);root.addView(fairy,LinearLayout.LayoutParams(-1,0,1.65f))
        visual=ChildVisualView(this);root.addView(visual,LinearLayout.LayoutParams(-1,0,2.15f).apply{topMargin=dp(5);bottomMargin=dp(6)})
        speechBubble=TextView(this).apply{textSize=18f;setTextColor(Color.rgb(72,42,94));gravity=Gravity.CENTER;setTypeface(typeface,1);setPadding(dp(16),dp(10),dp(16),dp(10));background=roundedBackground(Color.rgb(255,245,252),26f)};root.addView(speechBubble,LinearLayout.LayoutParams(-1,0,1.05f))
        choices=GridLayout(this).apply{columnCount=3;rowCount=2;alignmentMode=GridLayout.ALIGN_BOUNDS;setPadding(0,dp(5),0,dp(4))};root.addView(choices,LinearLayout.LayoutParams(-1,dp(130)))
        status=TextView(this).apply{text="Sua vez";textSize=12f;setTextColor(Color.WHITE);gravity=Gravity.CENTER};root.addView(status,LinearLayout.LayoutParams(-1,dp(24)))
        root.addView(Button(this).apply{text="★  FALAR COM A FADA  ★";textSize=18f;isAllCaps=false;setTextColor(Color.WHITE);setTypeface(typeface,1);background=roundedBackground(Color.rgb(124,72,185),30f);setOnClickListener{startListening()}},LinearLayout.LayoutParams(-1,dp(62)));return root}

    private fun renderReply(r:ConversationReply){speechBubble.text=r.text;fairy.setMood(r.mood);visual.showScene(r.scene);waitingForMovement=r.waitForMovement;renderChoices(r.choices);r.parentAlert?.let{events.record("emotion_alert",it,"child=${profile.name}")};status.text=if(r.waitForMovement)"A fadinha fica aqui esperando você" else "Sua vez"}
    private fun speakReply(r:ConversationReply){speech.cancel();autoListenAfterSpeech=r.keepListening;voice.speak(r.text)}
    private fun renderChoices(items:List<String>){choices.removeAllViews();val visible=items.take(6);visible.forEachIndexed{i,label->val b=Button(this).apply{text=label;textSize=if(label.length<=2)30f else 14f;isAllCaps=false;setTextColor(Color.WHITE);setTypeface(typeface,1);background=roundedBackground(choiceColor(label),24f);setOnClickListener{if(label=="CONVERSAR"){startListening();return@setOnClickListener};events.record("choice",label);engine.onChoice(label).also{r->renderReply(r);speakReply(r)}}};choices.addView(b,GridLayout.LayoutParams(GridLayout.spec(i/3,1f),GridLayout.spec(i%3,1f)).apply{width=0;height=if(visible.size>3)dp(58) else dp(74);setMargins(dp(4),dp(4),dp(4),dp(4))})}}
    private fun handleSpoken(t:String){events.record("speech",t);engine.reply(t).also{renderReply(it);speakReply(it)}}
    private fun startListening(){if(waitingForMovement)return;if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),10);return};voice.stop();if(!speech.startListening())status.text="Só um instantinho..."}
    private fun renderInteractionState(s:InteractionState){when(s){InteractionState.IDLE->status.text="Sua vez";InteractionState.SPEAKING->{status.text="A fadinha está falando";fairy.setMood(RobotMood.SPEAKING)};InteractionState.WAITING->status.text="Já vou te ouvir";InteractionState.LISTENING->{status.text="A fadinha está ouvindo você";fairy.setMood(RobotMood.LISTENING)};InteractionState.PROCESSING->{status.text="Pensando no que você falou";fairy.setMood(RobotMood.THINKING)}}}
    override fun onSensorChanged(e:SensorEvent?){if(!waitingForMovement||e?.sensor?.type!=Sensor.TYPE_ACCELEROMETER)return;val total=abs(e.values[0])+abs(e.values[1])+abs(e.values[2]);if(baselineAcceleration==0f)baselineAcceleration=total;val now=System.currentTimeMillis();if(abs(total-baselineAcceleration)>5.5f&&now-lastMovementAt>2500){lastMovementAt=now;waitingForMovement=false;events.record("routine","returned_from_bathroom");engine.onMovementDetected().also{renderReply(it);speakReply(it)}};baselineAcceleration=total}
    override fun onAccuracyChanged(sensor:Sensor?,accuracy:Int)=Unit
    override fun onResume(){super.onResume();if(::updater.isInitialized)updater.onResume()}
    private fun choiceColor(l:String)=when(l){"A","FELIZ","ANIMADA"->Color.rgb(244,170,73);"C","SIM"->Color.rgb(72,185,133);"P","NÃO","TRISTE"->Color.rgb(78,150,226);"BRAVA"->Color.rgb(232,105,105);"MEDO"->Color.rgb(151,104,211);"CANSADA"->Color.rgb(111,130,157);else->Color.rgb(142,91,190)}
    private fun roundedBackground(c:Int,r:Float)=GradientDrawable().apply{setColor(c);cornerRadius=dp(r.toInt()).toFloat()};private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    override fun onDestroy(){sensorManager?.unregisterListener(this);if(::speech.isInitialized)speech.destroy();if(::voice.isInitialized)voice.shutdown();super.onDestroy()}
}

class FairyCompanionView(context:Context):View(context){private val p=Paint(Paint.ANTI_ALIAS_FLAG);private var mood=RobotMood.HAPPY;private var voiceLevel=0f;private var phase=0f
    fun setMood(m:RobotMood){mood=m;invalidate()};fun setVoiceLevel(v:Float){voiceLevel=v.coerceIn(0f,1f);invalidate()}
    override fun onDraw(c:Canvas){phase+=.08f;val w=width.toFloat();val h=height.toFloat();val cx=w/2;val cy=h*.54f
        p.color=Color.argb(70,255,244,155);c.drawCircle(cx,cy,h*.42f,p)
        p.color=Color.argb(155,255,175,235);c.drawOval(RectF(cx-w*.34f,cy-h*.20f,cx-w*.06f,cy+h*.20f),p);c.drawOval(RectF(cx+w*.06f,cy-h*.20f,cx+w*.34f,cy+h*.20f),p)
        p.color=Color.rgb(255,220,184);c.drawCircle(cx,cy-h*.08f,h*.18f,p);p.color=Color.rgb(74,43,69);c.drawArc(RectF(cx-h*.19f,cy-h*.29f,cx+h*.19f,cy+h*.04f),180f,180f,true,p)
        p.color=Color.rgb(54,39,69);val eyeY=cy-h*.09f;val ex=h*.07f;c.drawCircle(cx-ex,eyeY,h*.018f,p);c.drawCircle(cx+ex,eyeY,h*.018f,p)
        p.style=Paint.Style.STROKE;p.strokeWidth=h*.014f;p.strokeCap=Paint.Cap.ROUND;when(mood){RobotMood.CONFUSED,RobotMood.THINKING->c.drawArc(RectF(cx-h*.07f,cy+h*.01f,cx+h*.07f,cy+h*.08f),195f,150f,false,p);else->c.drawArc(RectF(cx-h*.07f,cy-h*.01f,cx+h*.07f,cy+h*.08f),10f,160f,false,p)};p.style=Paint.Style.FILL
        p.color=Color.rgb(238,126,190);val dress=Path().apply{moveTo(cx-h*.10f,cy+h*.08f);lineTo(cx-h*.22f,cy+h*.36f);lineTo(cx+h*.22f,cy+h*.36f);lineTo(cx+h*.10f,cy+h*.08f);close()};c.drawPath(dress,p)
        p.color=Color.rgb(255,224,94);c.drawCircle(cx,cy-h*.31f,h*.035f,p);c.drawCircle(cx-h*.07f,cy-h*.28f,h*.028f,p);c.drawCircle(cx+h*.07f,cy-h*.28f,h*.028f,p)
        if(mood==RobotMood.LISTENING||voiceLevel>.1f){p.style=Paint.Style.STROKE;p.strokeWidth=4f;p.color=Color.argb(180,255,245,145);c.drawCircle(cx,cy,h*(.40f+.06f*voiceLevel),p);p.style=Paint.Style.FILL}
        postInvalidateDelayed(90)}
}