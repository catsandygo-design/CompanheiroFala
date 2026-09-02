package br.com.companheirofala

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var status: TextView
    private lateinit var heard: TextView
    private lateinit var button: Button
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(40, 60, 40, 60)
            setBackgroundColor(Color.WHITE)
        }
        val title = TextView(this).apply { text="Companheiro Fala\n\n🙂"; textSize=34f; gravity=Gravity.CENTER }
        status = TextView(this).apply { text="Pronto para conversar"; textSize=20f; gravity=Gravity.CENTER; setPadding(0,30,0,20) }
        heard = TextView(this).apply { text="Toque no botão e fale."; textSize=18f; gravity=Gravity.CENTER; setPadding(0,10,0,30) }
        button = Button(this).apply { text="🎙️ FALAR"; textSize=22f; setOnClickListener { listen() } }
        box.addView(title); box.addView(status); box.addView(heard); box.addView(button)
        setContentView(box)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 10)
        else setupRecognizer()
    }

    private fun setupRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { status.text="Reconhecimento de voz indisponível"; return }
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object: RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) { status.text="Estou ouvindo..."; button.isEnabled=false }
                override fun onBeginningOfSpeech() { status.text="Pode falar" }
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() { status.text="Pensando..." }
                override fun onError(e: Int) { button.isEnabled=true; status.text="Não consegui entender. Tente novamente." }
                override fun onResults(r: Bundle?) {
                    button.isEnabled=true
                    val raw=r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    respond(raw)
                }
                override fun onPartialResults(r: Bundle?) {
                    val p=r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if(!p.isNullOrBlank()) heard.text="Ouvindo: $p"
                }
                override fun onEvent(t: Int, p: Bundle?) {}
            })
        }
    }

    private fun listen() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),10); return }
        if(recognizer==null) setupRecognizer()
        val i=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE,"pt-BR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true)
        }
        recognizer?.startListening(i)
    }

    private fun respond(raw:String) {
        if(raw.isBlank()){ status.text="Não entendi. Pode falar de novo?"; return }
        val low=raw.lowercase(Locale("pt","BR"))
        val normalized=if(low.contains("municornio") || low.contains("municórnio")) "unicórnio" else raw
        heard.text=if(normalized!=raw) "Eu ouvi: $raw\nEntendi como: $normalized" else "Eu ouvi: $raw"
        val reply=if(normalized.lowercase().contains("unicórnio")) "Unicórnio! Que bonito! Vamos falar juntos? U-ni-cór-nio." else "Eu ouvi $normalized. Conta mais!"
        status.text=reply
        if(ttsReady) tts?.speak(reply,TextToSpeech.QUEUE_FLUSH,null,"reply")
    }

    override fun onInit(s:Int) { if(s==TextToSpeech.SUCCESS){ val r=tts?.setLanguage(Locale("pt","BR")); ttsReady=r!=TextToSpeech.LANG_MISSING_DATA && r!=TextToSpeech.LANG_NOT_SUPPORTED; tts?.setSpeechRate(.9f) } }
    override fun onRequestPermissionsResult(r:Int,p:Array<out String>,g:IntArray){ super.onRequestPermissionsResult(r,p,g); if(r==10 && g.firstOrNull()==PackageManager.PERMISSION_GRANTED) setupRecognizer() }
    override fun onDestroy(){ recognizer?.destroy(); tts?.shutdown(); super.onDestroy() }
}
