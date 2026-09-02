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
    private var waitingForMovement = false
    private var baselineAcceleration = 0f
    private var lastMovementAt = 0L
    private var lastSpokenText = ""
    private var lastSpokenAt = 0L
    private var sensorManager: SensorManager? = null
    private val engine = ConversationEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildScreen())
        updater = AppUpdater(this)
        tts = TextToSpeech(this, this)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
        } else {
            setupRecognizer()
        }

        val intro = engine.start(PlayMode.HOME)
        renderReply(intro)
        speak(intro.text)
        status.postDelayed({ updater.checkAndUpdate { message -> status.text = message } }, 1500)
    }

    override fun onResume() {
        super.onResume()
        if (::updater.isInitialized) updater.onResume()
    }

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(10))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(24, 32, 70), Color.rgb(72, 48, 104))
            )
        }

        root.addView(TextView(this).apply {
            text = "COMPANHEIRO • v0.6"
            textSize = 13f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(-1, dp(26)))

        face = RobotFaceView(this)
        root.addView(face, LinearLayout.LayoutParams(-1, 0, 1.35f))

        visual = ChildVisualView(this)
        root.addView(visual, LinearLayout.LayoutParams(-1, 0, 3.0f).apply {
            topMargin = dp(5)
            bottomMargin = dp(5)
        })

        speechBubble = TextView(this).apply {
            textSize = 19f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = roundedBackground(Color.rgb(45, 55, 95), 22f)
        }
        root.addView(speechBubble, LinearLayout.LayoutParams(-1, 0, 1.05f))

        choices = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(5))
        }
        root.addView(choices, LinearLayout.LayoutParams(-1, dp(80)))

        status = TextView(this).apply {
            text = "Toque numa opção ou converse comigo"
            textSize = 12f
            setTextColor(Color.rgb(220, 225, 245))
            gravity = Gravity.CENTER
        }
        root.addView(status, LinearLayout.LayoutParams(-1, dp(24)))

        root.addView(Button(this).apply {
            text = "CONVERSAR"
            textSize = 19f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            background = roundedBackground(Color.rgb(78, 125, 245), 25f)
            setOnClickListener { listen() }
        }, LinearLayout.LayoutParams(-1, dp(60)))

        return root
    }

    private fun renderReply(reply: ConversationReply) {
        speechBubble.text = reply.text
        face.setMood(reply.mood)
        visual.showScene(reply.scene)
        waitingForMovement = reply.waitForMovement
        renderChoices(reply.choices)
        status.text = if (reply.waitForMovement) "Eu espero você voltar" else "Toque numa opção ou converse comigo"
        reply.parentAlert?.let { saveParentAlert(it) }
    }

    private fun renderChoices(items: List<String>) {
        choices.removeAllViews()
        items.take(3).forEachIndexed { index, label ->
            val color = when (label) {
                "A" -> Color.rgb(236, 104, 104)
                "C" -> Color.rgb(67, 174, 118)
                "P" -> Color.rgb(85, 140, 240)
                "FELIZ" -> Color.rgb(224, 174, 55)
                "TRISTE" -> Color.rgb(75, 145, 220)
                "BRAVA" -> Color.rgb(225, 105, 80)
                else -> Color.rgb(71, 82, 135)
            }
            val button = Button(this).apply {
                text = label
                textSize = if (label.length <= 2) 30f else 15f
                isAllCaps = false
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
                background = roundedBackground(color, 22f)
                setOnClickListener {
                    val reply = engine.onChoice(label)
                    renderReply(reply)
                    speak(reply.text)
                }
            }
            choices.addView(button, LinearLayout.LayoutParams(0, -1, 1f).apply {
                marginStart = if (index == 0) 0 else dp(4)
                marginEnd = if (index == items.lastIndex) 0 else dp(4)
            })
        }
    }

    private fun setupRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "Reconhecimento de voz indisponível neste aparelho"
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    face.setMood(RobotMood.LISTENING)
                    status.text = "Estou ouvindo..."
                }
                override fun onBeginningOfSpeech() { face.setMood(RobotMood.LISTENING) }
                override fun onRmsChanged(rmsdB: Float) { face.setVoiceLevel(rmsdB) }
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() {
                    face.setMood(RobotMood.THINKING)
                    face.setVoiceLevel(0f)
                }
                override fun onError(error: Int) {
                    face.setMood(RobotMood.CONFUSED)
                    status.text = "Não entendi. Toque em CONVERSAR e tente de novo."
                }
                override fun onResults(results: Bundle?) {
                    val raw = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (raw.isBlank()) {
                        status.text = "Não ouvi nenhuma palavra."
                        return
                    }
                    val reply = engine.reply(raw)
                    renderReply(reply)
                    speak(reply.text)
                }
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }
    }

    private fun listen() {
        if (waitingForMovement) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
            return
        }
        if (recognizer == null) setupRecognizer()
        recognizer?.cancel()
        recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        })
    }

    private fun speak(text: String) {
        val now = System.currentTimeMillis()
        if (text == lastSpokenText && now - lastSpokenAt < 2500) return
        lastSpokenText = text
        lastSpokenAt = now
        if (!ttsReady) return
        recognizer?.cancel()
        face.setMood(RobotMood.SPEAKING)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "child_reply")
    }

    private fun configureFriendlyVoice() {
        val locale = Locale.forLanguageTag("pt-BR")
        tts?.setLanguage(locale)
        val ptBrVoices = tts?.voices.orEmpty().filter {
            it.locale.language == "pt" && it.locale.country.equals("BR", ignoreCase = true)
        }
        val preferred = ptBrVoices.sortedWith(
            compareByDescending<android.speech.tts.Voice> { !it.isNetworkConnectionRequired }
                .thenByDescending { it.quality }
                .thenByDescending { it.latency }
        ).firstOrNull()
        if (preferred != null) tts?.voice = preferred
        // Pitch excessivo deixava a voz fina/anasalada. Mantemos quase natural.
        tts?.setSpeechRate(0.90f)
        tts?.setPitch(1.04f)
    }

    override fun onInit(initStatus: Int) {
        if (initStatus != TextToSpeech.SUCCESS) return
        ttsReady = true
        configureFriendlyVoice()
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                runOnUiThread { face.setMood(RobotMood.SPEAKING) }
            }
            override fun onDone(utteranceId: String?) {
                runOnUiThread {
                    face.setMood(RobotMood.HAPPY)
                    status.text = "Sua vez. Toque em CONVERSAR ou numa opção."
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = Unit
        })
    }

    private fun saveParentAlert(message: String) {
        getSharedPreferences("parent_alerts", Context.MODE_PRIVATE)
            .edit()
            .putLong("last_alert_time", System.currentTimeMillis())
            .putString("last_alert_message", message)
            .apply()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!waitingForMovement || event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
        val total = abs(event.values[0]) + abs(event.values[1]) + abs(event.values[2])
        if (baselineAcceleration == 0f) baselineAcceleration = total
        val now = System.currentTimeMillis()
        if (abs(total - baselineAcceleration) > 5.5f && now - lastMovementAt > 2500) {
            lastMovementAt = now
            waitingForMovement = false
            val reply = engine.onMovementDetected()
            renderReply(reply)
            speak(reply.text)
        }
        baselineAcceleration = total
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_AUDIO && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) setupRecognizer()
    }

    private fun roundedBackground(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius.toInt()).toFloat()
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
