package br.com.companheirofala

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

class MainActivity : Activity(), SensorEventListener {
    private lateinit var fairy: FairyPortraitView
    private lateinit var visual: ChildVisualView
    private lateinit var speechBubble: TextView
    private lateinit var choices: GridLayout
    private lateinit var status: TextView
    private lateinit var updater: AppUpdater
    private lateinit var voice: VoiceEngine
    private lateinit var speech: SpeechEngine
    private lateinit var events: ParentEventRepository
    private val profile = ChildProfile.gabi()
    private val engine = ConversationEngine(profile)
    private var waitingForMovement = false
    private var autoListenAfterSpeech = false
    private var baselineAcceleration = 0f
    private var lastMovementAt = 0L
    private var sensorManager: SensorManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildScreen())
        updater = AppUpdater(this); events = ParentEventRepository(this)
        speech = SpeechEngine(this,
            onState = { s -> runOnUiThread { renderInteractionState(s) } },
            onLevel = { l -> runOnUiThread { fairy.setVoiceLevel(l) } },
            onResult = { t -> runOnUiThread { handleSpoken(t) } },
            onFailure = { runOnUiThread { status.text = "Não ouvi. Toca na estrela e fala de novo." } })
        voice = VoiceEngine(this,
            onStart = { runOnUiThread { speech.markSpeaking(); fairy.setMood(RobotMood.SPEAKING) } },
            onDone = { runOnUiThread { speech.markIdle(); fairy.setMood(RobotMood.HAPPY); if (autoListenAfterSpeech && !waitingForMovement) { autoListenAfterSpeech=false; status.postDelayed({startListening()},450) } } },
            onError = { runOnUiThread { speech.markIdle(); status.text="A voz não está disponível." } })
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensorManager?.registerListener(this,it,SensorManager.SENSOR_DELAY_NORMAL) }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),10)
        val intro=engine.start(PlayMode.HOME); renderReply(intro); speakReply(intro)
        status.postDelayed({updater.checkAndUpdate { m->status.text=m }},1400)
    }

    private fun buildScreen(): View {
        val root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(10),dp(6),dp(10),dp(12))
            background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(Color.rgb(47,91,80),Color.rgb(103,160,128),Color.rgb(235,185,211)))
            setOnApplyWindowInsetsListener { v,i -> val b=i.getInsets(WindowInsets.Type.navigationBars()).bottom; v.setPadding(dp(10),dp(6),dp(10),dp(12)+b); i }
        }
        root.addView(TextView(this).apply { text="MEU CORAÇÃO • v0.12"; textSize=13f; setTextColor(Color.WHITE); gravity=Gravity.CENTER; setTypeface(typeface,Typeface.BOLD) },LinearLayout.LayoutParams(-1,dp(26)))
        fairy=FairyPortraitView(this); root.addView(fairy,LinearLayout.LayoutParams(-1,0,1.65f).apply{bottomMargin=dp(6)})
        visual=ChildVisualView(this); root.addView(visual,LinearLayout.LayoutParams(-1,0,1.25f).apply{bottomMargin=dp(6)})
        speechBubble=TextView(this).apply { textSize=17f; setTextColor(Color.rgb(57,41,67)); gravity=Gravity.CENTER; setTypeface(typeface,Typeface.BOLD); setPadding(dp(14),dp(10),dp(14),dp(10)); background=roundedBackground(Color.rgb(255,248,252),24f) }
        root.addView(speechBubble,LinearLayout.LayoutParams(-1,0,.82f))
        choices=GridLayout(this).apply{columnCount=3;rowCount=2;alignmentMode=GridLayout.ALIGN_BOUNDS;setPadding(0,dp(3),0,dp(3))}; root.addView(choices,LinearLayout.LayoutParams(-1,dp(116)))
        status=TextView(this).apply{text="Sua vez";textSize=12f;setTextColor(Color.WHITE);gravity=Gravity.CENTER};root.addView(status,LinearLayout.LayoutParams(-1,dp(22)))
        root.addView(Button(this).apply{text="★  FALAR COM A FADA  ★";textSize=17f;isAllCaps=false;setTextColor(Color.WHITE);setTypeface(typeface,Typeface.BOLD);background=roundedBackground(Color.rgb(118,75,164),30f);setOnClickListener{startListening()}},LinearLayout.LayoutParams(-1,dp(58)))
        return root
    }

    private fun renderReply(r:ConversationReply){ speechBubble.text=r.text; fairy.setMood(r.mood); visual.setScene(r.scene); visual.visibility=if(r.scene==VisualScene.NONE) View.GONE else View.VISIBLE; waitingForMovement=r.waitForMovement; renderChoices(r.choices); r.parentAlert?.let{events.record("emotion_alert",it,"child=${profile.name}")}; status.text=if(r.waitForMovement)"A fadinha espera você voltar" else "Sua vez" }
    private fun speakReply(r:ConversationReply){speech.cancel();autoListenAfterSpeech=r.keepListening;voice.speak(r.text)}
    private fun renderChoices(items:List<String>){choices.removeAllViews();items.take(6).forEachIndexed{index,label->val b=Button(this).apply{text=label;textSize=if(label.length<=2)28f else 13f;isAllCaps=false;setTextColor(Color.WHITE);setTypeface(typeface,Typeface.BOLD);background=roundedBackground(choiceColor(label),22f);setOnClickListener{if(label=="CONVERSAR"){startListening()}else{events.record("choice",label);engine.onChoice(label).also{r->renderReply(r);speakReply(r)}}}};choices.addView(b,GridLayout.LayoutParams(GridLayout.spec(index/3,1f),GridLayout.spec(index%3,1f)).apply{width=0;height=if(items.size>3)dp(52)else dp(68);setMargins(dp(3),dp(3),dp(3),dp(3))})}}
    private fun handleSpoken(t:String){events.record("speech",t);engine.reply(t).also{renderReply(it);speakReply(it)}}
    private fun startListening(){if(waitingForMovement)return;if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),10);return};voice.stop();if(!speech.startListening())status.text="Só um instantinho..."}
    private fun renderInteractionState(s:InteractionState){status.text=when(s){InteractionState.IDLE->"Sua vez";InteractionState.SPEAKING->"A fadinha está falando";InteractionState.WAITING->"Já vou te ouvir";InteractionState.LISTENING->"A fadinha está ouvindo você";InteractionState.PROCESSING->"Pensando no que você falou"};if(s==InteractionState.LISTENING)fairy.setMood(RobotMood.LISTENING)}
    override fun onSensorChanged(e:SensorEvent?){if(!waitingForMovement||e?.sensor?.type!=Sensor.TYPE_ACCELEROMETER)return;val total=abs(e.values[0])+abs(e.values[1])+abs(e.values[2]);if(baselineAcceleration==0f)baselineAcceleration=total;val now=System.currentTimeMillis();if(abs(total-baselineAcceleration)>5.5f&&now-lastMovementAt>2500){lastMovementAt=now;waitingForMovement=false;engine.onMovementDetected().also{renderReply(it);speakReply(it)}};baselineAcceleration=total}
    override fun onAccuracyChanged(sensor:Sensor?,accuracy:Int)=Unit
    override fun onResume(){super.onResume();if(::updater.isInitialized)updater.onResume()}
    private fun choiceColor(l:String)=when(l){"A","FELIZ","ANIMADA"->Color.rgb(244,170,73);"C","SIM"->Color.rgb(72,185,133);"P","NÃO","TRISTE"->Color.rgb(78,150,226);"BRAVA"->Color.rgb(232,105,105);"MEDO"->Color.rgb(151,104,211);else->Color.rgb(142,91,190)}
    private fun roundedBackground(c:Int,r:Float)=GradientDrawable().apply{setColor(c);cornerRadius=dp(r.toInt()).toFloat()}
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    override fun onDestroy(){sensorManager?.unregisterListener(this);if(::speech.isInitialized)speech.destroy();if(::voice.isInitialized)voice.shutdown();super.onDestroy()}
}
