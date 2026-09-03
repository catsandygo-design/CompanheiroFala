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
        updater = AppUpdater(this)
        events = ParentEventRepository(this)

        speech = SpeechEngine(
            context = this,
            onState = { state -> runOnUiThread { renderInteractionState(state) } },
            onLevel = { level -> runOnUiThread { fairy.setVoiceLevel(level) } },
            onResult = { text -> runOnUiThread { handleSpoken(text) } },
            onFailure = { runOnUiThread { status.text = "Não ouvi direitinho. Toca na estrela e fala de novo." } }
        )
        voice = VoiceEngine(
            context = this,
            onStart = { runOnUiThread { speech.markSpeaking(); fairy.setMood(RobotMood.SPEAKING) } },
            onDone = {
                runOnUiThread {
                    speech.markIdle()
                    fairy.setMood(RobotMood.HAPPY)
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
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 10)
        }

        val intro = engine.start(PlayMode.HOME)
        renderReply(intro)
        speakReply(intro)
        status.postDelayed({ updater.checkAndUpdate { message -> status.text = message } }, 1400)
    }

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(8))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(37, 75, 67), Color.rgb(80, 139, 111), Color.rgb(230, 174, 203))
            )
        }

        root.addView(TextView(this).apply {
            text = "MEU CORAÇÃO • v0.11"
            textSize = 13f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(-1, dp(26)))

        fairy = FairyPortraitView(this)
        root.addView(fairy, LinearLayout.LayoutParams(-1, 0, 2.35f).apply {
            bottomMargin = dp(8)
        })

        visual = ChildVisualView(this).apply { visibility = View.GONE }
        root.addView(visual, LinearLayout.LayoutParams(-1, 0, 0f))

        speechBubble = TextView(this).apply {
            textSize = 18f
            setTextColor(Color.rgb(57, 41, 67))
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = roundedBackground(Color.rgb(255, 248, 252), 26f)
        }
        root.addView(speechBubble, LinearLayout.LayoutParams(-1, 0, 1.05f))

        choices = GridLayout(this).apply {
            columnCount = 3
            rowCount = 2
            alignmentMode = GridLayout.ALIGN_BOUNDS
            setPadding(0, dp(5), 0, dp(4))
        }
        root.addView(choices, LinearLayout.LayoutParams(-1, dp(128)))

        status = TextView(this).apply {
            text = "Sua vez"
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        root.addView(status, LinearLayout.LayoutParams(-1, dp(24)))

        root.addView(Button(this).apply {
            text = "★  FALAR COM A FADA  ★"
            textSize = 18f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            background = roundedBackground(Color.rgb(118, 75, 164), 30f)
            setOnClickListener { startListening() }
        }, LinearLayout.LayoutParams(-1, dp(62)))
        return root
    }

    private fun renderReply(reply: ConversationReply) {
        speechBubble.text = reply.text
        fairy.setMood(reply.mood)
        waitingForMovement = reply.waitForMovement
        renderChoices(reply.choices)
        reply.parentAlert?.let { events.record("emotion_alert", it, "child=${profile.name}") }
        status.text = if (reply.waitForMovement) "A fadinha fica aqui esperando você" else "Sua vez"
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
            val button = Button(this).apply {
                text = label
                textSize = if (label.length <= 2) 30f else 14f
                isAllCaps = false
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
                background = roundedBackground(choiceColor(label), 24f)
                setOnClickListener {
                    if (label == "CONVERSAR") {
                        startListening()
                        return@setOnClickListener
                    }
                    events.record("choice", label)
                    engine.onChoice(label).also { reply -> renderReply(reply); speakReply(reply) }
                }
            }
            choices.addView(button, GridLayout.LayoutParams(GridLayout.spec(index / 3, 1f), GridLayout.spec(index % 3, 1f)).apply {
                width = 0
                height = if (visible.size > 3) dp(58) else dp(74)
                setMargins(dp(4), dp(4), dp(4), dp(4))
            })
        }
    }

    private fun handleSpoken(text: String) {
        events.record("speech", text)
        engine.reply(text).also { renderReply(it); speakReply(it) }
    }

    private fun startListening() {
        if (waitingForMovement) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 10)
            return
        }
        voice.stop()
        if (!speech.startListening()) status.text = "Só um instantinho..."
    }

    private fun renderInteractionState(state: InteractionState) {
        when (state) {
            InteractionState.IDLE -> status.text = "Sua vez"
            InteractionState.SPEAKING -> { status.text = "A fadinha está falando"; fairy.setMood(RobotMood.SPEAKING) }
            InteractionState.WAITING -> status.text = "Já vou te ouvir"
            InteractionState.LISTENING -> { status.text = "A fadinha está ouvindo você"; fairy.setMood(RobotMood.LISTENING) }
            InteractionState.PROCESSING -> { status.text = "Pensando no que você falou"; fairy.setMood(RobotMood.THINKING) }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!waitingForMovement || event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
        val total = abs(event.values[0]) + abs(event.values[1]) + abs(event.values[2])
        if (baselineAcceleration == 0f) baselineAcceleration = total
        val now = System.currentTimeMillis()
        if (abs(total - baselineAcceleration) > 5.5f && now - lastMovementAt > 2500) {
            lastMovementAt = now
            waitingForMovement = false
            events.record("routine", "returned_from_bathroom")
            engine.onMovementDetected().also { renderReply(it); speakReply(it) }
        }
        baselineAcceleration = total
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    override fun onResume() { super.onResume(); if (::updater.isInitialized) updater.onResume() }

    private fun choiceColor(label: String) = when (label) {
        "A", "FELIZ", "ANIMADA" -> Color.rgb(244, 170, 73)
        "C", "SIM" -> Color.rgb(72, 185, 133)
        "P", "NÃO", "TRISTE" -> Color.rgb(78, 150, 226)
        "BRAVA" -> Color.rgb(232, 105, 105)
        "MEDO" -> Color.rgb(151, 104, 211)
        "CANSADA" -> Color.rgb(111, 130, 157)
        else -> Color.rgb(142, 91, 190)
    }

    private fun roundedBackground(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius.toInt()).toFloat()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        sensorManager?.unregisterListener(this)
        if (::speech.isInitialized) speech.destroy()
        if (::voice.isInitialized) voice.shutdown()
        super.onDestroy()
    }
}
