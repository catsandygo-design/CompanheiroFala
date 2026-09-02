package br.com.companheirofala

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import kotlin.math.abs

class MainActivity : Activity(), TextToSpeech.OnInitListener, SensorEventListener {

    private lateinit var face: RobotFaceView
    private lateinit var visual: ChildVisualView
    private lateinit var speechBubble: TextView
    private lateinit var choices: LinearLayout
    private lateinit var status: TextView
    private lateinit var updater: AppUpdater

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var continuousMode = false
    private var waitingForMovement = false
    private var baselineAcceleration = 0f
    private var lastMovementAt = 0L
    private var sensorManager: SensorManager? = null
    private val engine = ConversationEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        setContentView(buildScreen())
        updater = AppUpdater(this)

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
        } else setupRecognizer()

        val intro = engine.start(PlayMode.CHAT)
        renderReply(intro)
        speak(intro.text, false)

        status.postDelayed({
            updater.checkAndUpdate { message -> status.text = message }
        }, 1200)
    }

    override fun onResume() {
        super.onResume()
        if (::updater.isInitialized) updater.onResume()
    }

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(12))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(11,20,43), Color.rgb(29,23,65)))
        }

        val header = TextView(this).apply {
            text = "COMPANHEIRO FALA • v0.5"
            textSize = 13f
            setTextColor(Color.rgb(179,214,255))
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
        }
        root.addView(header, LinearLayout.LayoutParams(-1, dp(28)))

        face = RobotFaceView(this)
        root.addView(face, LinearLayout.LayoutParams(-1, 0, 2.3f))

        visual = ChildVisualView(this)
        root.addView(visual, LinearLayout.LayoutParams(-1, 0, 2.6f).apply {
            topMargin = dp(4); bottomMargin = dp(6)
        })

        speechBubble = TextView(this).apply {
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(18), dp(14), dp(18), dp(14))
            background = roundedBackground(Color.rgb(34,48,86), 24f)
        }
        root.addView(speechBubble, LinearLayout.LayoutParams(-1, 0, 1.6f))

        choices = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(6))
        }
        root.addView(choices, LinearLayout.LayoutParams(-1, dp(82)))

        status = TextView(this).apply {
            text = "Fale ou toque numa figura"
            textSize = 13f
            setTextColor(Color.rgb(185,201,232))
            gravity = Gravity.CENTER
        }
        root.addView(status, LinearLayout.LayoutParams(-1, dp(28)))

        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        bottom.addView(bigButton("FALAR", Color.rgb(57,118,255)) {
            continuousMode = true
            listen()
        }, LinearLayout.LayoutParams(0, dp(60), 1.7f).apply { marginEnd = dp(6) }))
        bottom.addView(bigButton("ABC", Color.rgb(124,83,230)) {
            continuousMode = true
            val r = engine.start(PlayMode.LETTERS)
            renderReply(r); speak(r.text, true)
        }, LinearLayout.LayoutParams(0, dp(60), 1f).apply { marginStart = dp(6) }))
        root.addView(bottom, LinearLayout.LayoutParams(-1, dp(64)))

        return root
    }

    private fun bigButton(label: String, color: Int, action: () -> Unit, lp: LinearLayout.LayoutParams): Button {
        return Button(this).apply {
            text = label
            textSize = 20f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            background = roundedBackground(color, 22f)
            setOnClickListener { action() }
            layoutParams = lp
        }
    }

    private fun renderReply(reply: ConversationReply) {
        speechBubble.text = reply.text
        face.setMood(reply.mood)
        visual.showScene(reply.scene)
        waitingForMovement = reply.waitForMovement
        renderChoices(reply.choices)
        status.text = if (reply.waitForMovement) "Pode deixar o celular na mesa" else "Fale ou toque numa opção"
    }

    private fun renderChoices(items: List<String>) {
        choices.removeAllViews()
        items.take(3).forEachIndexed { index, label ->
            val color = when (label) {
                "A" -> Color.rgb(249,95,95)
                "B" -> Color.rgb(83,170,255)
                "C" -> Color.rgb(89,206,126)
                else -> Color.rgb(61,82,130)
            }
            val button = Button(this).apply {
                text = label
                textSize = if (label.length <= 2) 30f else 16f
                isAllCaps = false
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
                background = roundedBackground(color, 22f)
                setOnClickListener {
                    var r = engine.onChoice(label)
                    if (label == "NÃO" && speechBubble.text.toString().contains("Lavou", true)) {
                        r = engine.onHandsNotWashed()
                    }
                    continuousMode = true
                    renderReply(r)
                    speak(r.text, r.keepListening)
                }
            }
            choices.addView(button, LinearLayout.LayoutParams(0, -1, 1f).apply {
                marginStart = if (index == 0) 0 else dp(4)
                marginEnd = if (index == items.lastIndex) 0 else dp(4)
            })
        }
    }

    private fun setupRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { face.setMood(RobotMood.LISTENING); status.text = "Estou ouvindo..." }
                override fun onBeginningOfSpeech() { face.setMood(RobotMood.LISTENING) }
                override fun onRmsChanged(rmsdB: Float) { face.setVoiceLevel(rmsdB) }
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() { face.setMood(RobotMood.THINKING); face.setVoiceLevel(0f) }
                override fun onError(error: Int) {
                    face.setMood(RobotMood.CONFUSED)
                    status.text = "Não entendi. Toque em FALAR."
                }
                override fun onResults(results: Bundle?) {
                    val raw = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (raw.isNotBlank()) {
                        val r = engine.reply(raw)
                        renderReply(r)
                        speak(r.text, r.keepListening)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }
    }

    private fun listen() {
        if (!continuousMode || waitingForMovement) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        if (recognizer == null) setupRecognizer()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        recognizer?.startListening(intent)
    }

    private fun speak(text: String, listenAfter: Boolean) {
        if (!ttsReady) {
            if (listenAfter && continuousMode) speechBubble.postDelayed({ listen() }, 600)
            return
        }
        face.setMood(RobotMood.SPEAKING)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, if (listenAfter) "listen" else "done")
    }

    private fun configurePrincessVoice() {
        val locale = Locale("pt", "BR")
        val voices = tts?.voices.orEmpty().filter { it.locale.language == "pt" }
        val chosen = voices.sortedWith(compareByDescending<android.speech.tts.Voice> { !it.isNetworkConnectionRequired }
            .thenByDescending { it.quality }).firstOrNull()
        if (chosen != null) tts?.voice = chosen
        tts?.setLanguage(locale)
        tts?.setSpeechRate(0.82f)
        tts?.setPitch(1.28f)
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        ttsReady = true
        configurePrincessVoice()
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { runOnUiThread { face.setMood(RobotMood.SPEAKING) } }
            override fun onDone(utteranceId: String?) {
                runOnUiThread {
                    face.setMood(RobotMood.HAPPY)
                    if (utteranceId == "listen" && continuousMode && !waitingForMovement) speechBubble.postDelayed({ listen() }, 550)
                }
            }
            @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) = Unit
        })
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!waitingForMovement || event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
        val total = abs(event.values[0]) + abs(event.values[1]) + abs(event.values[2])
        if (baselineAcceleration == 0f) baselineAcceleration = total
        val now = System.currentTimeMillis()
        if (abs(total - baselineAcceleration) > 5.5f && now - lastMovementAt > 2500) {
            lastMovementAt = now
            waitingForMovement = false
            val r = engine.onMovementDetected()
            renderReply(r)
            continuousMode = true
            speak(r.text, true)
        }
        baselineAcceleration = total
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_AUDIO && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) setupRecognizer()
    }

    private fun roundedBackground(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius.toInt()).toFloat()
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        sensorManager?.unregisterListener(this)
        recognizer?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }

    companion object { private const val REQUEST_AUDIO = 10 }
}
