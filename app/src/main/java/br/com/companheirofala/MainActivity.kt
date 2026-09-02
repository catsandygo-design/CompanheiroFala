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
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

class MainActivity : Activity(), SensorEventListener {
    private lateinit var face: RobotFaceView
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
        updater = AppUpdater(this)
        events = ParentEventRepository(this)

        speech = SpeechEngine(
            context = this,
            onState = { state -> runOnUiThread { renderInteractionState(state) } },
            onLevel = { level -> runOnUiThread { face.setVoiceLevel(level) } },
            onResult = { text -> runOnUiThread { handleSpoken(text) } },
            onFailure = { runOnUiThread { status.text = "Não ouvi direitinho. Toca no botão e fala de novo." } }
        )
        voice = VoiceEngine(
            context = this,
            onStart = { runOnUiThread { speech.markSpeaking(); face.setMood(RobotMood.SPEAKING) } },
            onDone = {
                runOnUiThread {
                    speech.markIdle(); face.setMood(RobotMood.HAPPY)
                    if (autoListenAfterSpeech && !waitingForMovement) {
                        autoListenAfterSpeech = false
                        status.postDelayed({ startListening() }, 600)
                    }
                }
            },
            onError = { runOnUiThread { speech.markIdle(); status.text = "A voz não está disponível neste aparelho." } }
        )

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
        }

        val intro = engine.start(PlayMode.HOME)
        renderReply(intro)
        speakReply(intro)
        status.postDelayed({ updater.checkAndUpdate { message -> status.text = message } }, 1400)
    }

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(10))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(36,48,104), Color.rgb(98,67,132))
            )
        }
        root.addView(TextView(this).apply {
            text = "COMPANHEIRO • v0.8"
            textSize = 12f
            setTextColor(Color.rgb(235,240,255))
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(-1, dp(24)))

        face = RobotFaceView(this)
        root.addView(face, LinearLayout.LayoutParams(-1, 0, 1.28f))

        visual = ChildVisualView(this)
        root.addView(visual, LinearLayout.LayoutParams(-1, 0, 2.45f).apply {
            topMargin = dp(6); bottomMargin = dp(8)
        })

        speechBubble = TextView(this).apply {
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(18), dp(12), dp(18), dp(12))
            background = roundedBackground(Color.rgb(55,67,120), 26f)
        }
        root.addView(speechBubble, LinearLayout.LayoutParams(-1, 0, 1.18f))

        choices = GridLayout(this).apply {
            columnCount = 3
            rowCount = 2
            alignmentMode = GridLayout.ALIGN_BOUNDS
            setPadding(0, dp(7), 0, dp(6))
        }
        root.addView(choices, LinearLayout.LayoutParams(-1, dp(136)))

        status = TextView(this).apply {
            text = "Sua vez"
            textSize = 12f
            setTextColor(Color.rgb(235,235,250))
            gravity = Gravity.CENTER
        }
        root.addView(status, LinearLayout.LayoutParams(-1, dp(26)))

        root.addView(Button(this).apply {
            text = "FALAR COMIGO"
            textSize = 20f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            background = roundedBackground(Color.rgb(73,130,247), 28f)
            setOnClickListener { startListening() }
        }, LinearLayout.LayoutParams(-1, dp(64)))
        return root
    }

    private fun renderReply(reply: ConversationReply) {
        speechBubble.text = reply.text
        face.setMood(reply.mood)
        visual.showScene(reply.scene)
        waitingForMovement = reply.waitForMovement
        renderChoices(reply.choices)
        reply.parentAlert?.let { events.record("emotion_alert", it, "child=${profile.name}") }
        status.text = if (reply.waitForMovement) "Eu fico aqui esperando você" else "Sua vez"
    }

    private fun speakReply(reply: ConversationReply) {
        speech.cancel()
        autoListenAfterSpeech = reply.keepListening
        voice.speak(reply.text)
    }

    private fun renderChoices(items: List<String>) {
        choices.removeAllViews()
        val visible = items.take(6)
        visible.forEachIndexed { index, label ->
            val row = index / 3
            val col = index % 3
            val button = Button(this).apply {
                text = label
                textSize = if (label.length <= 2) 30f else 14f
                isAllCaps = false
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
                background = roundedBackground(choiceColor(label), 24f)
                setOnClickListener {
                    if (label == "CONVERSAR") { startListening(); return@setOnClickListener }
                    events.record("choice", label)
                    val reply = engine.onChoice(label)
                    renderReply(reply)
                    speakReply(reply)
                }
            }
            val lp = GridLayout.LayoutParams(GridLayout.spec(row, 1f), GridLayout.spec(col, 1f)).apply {
                width = 0
                height = if (visible.size > 3) dp(60) else dp(76)
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            choices.addView(button, lp)
        }
    }

    private fun handleSpoken(text: String) {
        events.record("speech", text)
        val reply = engine.reply(text)
        renderReply(reply)
        speakReply(reply)
    }

    private fun startListening() {
        if (waitingForMovement) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
            return
        }
        voice.stop()
        if (!speech.startListening()) status.text = "Só um instante..."
    }

    private fun renderInteractionState(state: InteractionState) {
        when (state) {
            InteractionState.IDLE -> status.text = "Sua vez"
            InteractionState.SPEAKING -> { status.text = "Eu estou falando"; face.setMood(RobotMood.SPEAKING) }
            InteractionState.WAITING -> status.text = "Já vou te ouvir"
            InteractionState.LISTENING -> { status.text = "Estou ouvindo você"; face.setMood(RobotMood.LISTENING) }
            InteractionState.PROCESSING -> { status.text = "Pensando no que você falou"; face.setMood(RobotMood.THINKING) }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!waitingForMovement || event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
        val total = abs(event.values[0]) + abs(event.values[1]) + abs(event.values[2])
        if (baselineAcceleration == 0f) baselineAcceleration = total
        val now = System.currentTimeMillis()
        if (abs(total - baselineAcceleration) > 5.5f && now - lastMovementAt > 2500) {
            lastMovementAt = now; waitingForMovement = false
            events.record("routine", "returned_from_bathroom")
            val reply = engine.onMovementDetected(); renderReply(reply); speakReply(reply)
        }
        baselineAcceleration = total
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_AUDIO && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) status.text = "Microfone pronto"
    }
    override fun onResume() { super.onResume(); if (::updater.isInitialized) updater.onResume() }

    private fun choiceColor(label: String) = when (label) {
        "A", "FELIZ", "ANIMADA" -> Color.rgb(238,174,63)
        "C", "SIM" -> Color.rgb(70,181,126)
        "P", "NÃO", "TRISTE" -> Color.rgb(73,145,230)
        "BRAVA" -> Color.rgb(231,107,83)
        "MEDO" -> Color.rgb(154,116,222)
        "CANSADA" -> Color.rgb(110,128,154)
        else -> Color.rgb(85,98,160)
    }

    private fun roundedBackground(color: Int, radius: Float) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius.toInt()).toFloat() }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        sensorManager?.unregisterListener(this)
        if (::speech.isInitialized) speech.destroy()
        if (::voice.isInitialized) voice.shutdown()
        super.onDestroy()
    }

    companion object { private const val REQUEST_AUDIO = 10 }
}
