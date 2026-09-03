package br.com.companheirofala

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
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
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.GridLayout
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

class MainActivity : Activity(), SensorEventListener {
    private lateinit var fairy: ImageView
    private lateinit var visual: ChildVisualView
    private lateinit var speechBubble: TextView
    private lateinit var choices: GridLayout
    private lateinit var status: TextView
    private lateinit var updater: AppUpdater
    private lateinit var voice: VoiceEngine
    private lateinit var speech: SpeechEngine
    private lateinit var events: ParentEventRepository
    private lateinit var tracker: DevelopmentTracker
    private lateinit var music: LocalMusicEngine
    private lateinit var vocabularyBoard: ImageView
    private lateinit var guessImage: ImageView

    private val profile = ChildProfile.gabi()
    private val engine = ConversationEngine(profile)
    private val handler = Handler(Looper.getMainLooper())
    private var waitingForMovement = false
    private var autoListenAfterSpeech = false
    private var baselineAcceleration = 0f
    private var lastMovementAt = 0L
    private var lastInteractionAt = System.currentTimeMillis()
    private var sensorManager: SensorManager? = null
    private var fairyIdleAnimation: AnimatorSet? = null

    private val proactivePrompt = object : Runnable {
        override fun run() {
            val quietFor = System.currentTimeMillis() - lastInteractionAt
            if (quietFor >= 90_000L && !waitingForMovement) {
                val prompt = ConversationReply(
                    "${profile.name}, eu estou aqui. Quer brincar de animais, ouvir uma história ou beber um pouquinho de água?",
                    RobotMood.CURIOUS,
                    scene = VisualScene.HAPPY_FACE,
                    choices = listOf("ANIMAIS", "HISTÓRIA", "ÁGUA", "ABC", "CARINHAS", "INÍCIO")
                )
                renderReply(prompt)
                speakReply(prompt)
                lastInteractionAt = System.currentTimeMillis()
            }
            handler.postDelayed(this, 30_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildScreen())
        startFairyIdleAnimation()
        updater = AppUpdater(this)
        events = ParentEventRepository(this)
        tracker = DevelopmentTracker(this)
        music = LocalMusicEngine()

        speech = SpeechEngine(
            context = this,
            onState = { state -> runOnUiThread { renderInteractionState(state) } },
            onLevel = { level -> runOnUiThread { setFairyVoiceLevel(level) } },
            onResult = { text -> runOnUiThread { handleSpoken(text) } },
            onFailure = { runOnUiThread { status.text = "Não ouvi direitinho. Toca na estrela e fala de novo." } }
        )
        voice = VoiceEngine(
            context = this,
            onStart = { runOnUiThread { speech.markSpeaking(); setFairyMood(RobotMood.SPEAKING) } },
            onDone = {
                runOnUiThread {
                    speech.markIdle()
                    setFairyMood(RobotMood.HAPPY)
                    if (autoListenAfterSpeech && !waitingForMovement) {
                        autoListenAfterSpeech = false
                        // Pausa antes de ouvir de novo: dá tempo para a criança
                        // processar a pergunta sem a sensação de pressa.
                        status.postDelayed({ startListening() }, 1200)
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
        handler.postDelayed(proactivePrompt, 30_000L)
        status.postDelayed({ updater.checkAndUpdate { message -> status.text = message } }, 1400)
    }

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(12))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(47, 33, 83), Color.rgb(116, 73, 159), Color.rgb(250, 186, 202))
            )
            setOnApplyWindowInsetsListener { v, insets ->
                val bottom = insets.getInsets(WindowInsets.Type.navigationBars()).bottom
                v.setPadding(dp(10), dp(8), dp(10), dp(12) + bottom)
                insets
            }
        }

        root.addView(TextView(this).apply {
            text = "✦ LUMI E GABI ✦"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(-1, dp(30)))

        val fairyStage = FrameLayout(this).apply {
            background = roundedBackground(Color.argb(72, 255, 255, 255), 36f)
            setPadding(dp(10), dp(4), dp(10), dp(4))
        }
        fairy = ImageView(this).apply {
            setImageResource(R.drawable.fairy_pet)
            scaleType = ImageView.ScaleType.CENTER_CROP
            adjustViewBounds = false
            contentDescription = "Lumi, a fadinha companheira"
            clipToOutline = false
            setOnClickListener { touchInteraction(); startListening() }
        }
        fairyStage.addView(fairy, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        vocabularyBoard = ImageView(this).apply {
            setImageResource(R.drawable.vocabulary_board)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Figuras de palavras infantis"
            visibility = View.GONE
            setOnClickListener { touchInteraction(); startListening() }
        }
        fairyStage.addView(vocabularyBoard, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        guessImage = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Figura do jogo de adivinha"
            visibility = View.GONE
        }
        fairyStage.addView(guessImage, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        root.addView(fairyStage, LinearLayout.LayoutParams(-1, dp(238)).apply { bottomMargin = dp(8) })

        visual = ChildVisualView(this).apply { visibility = View.GONE }
        root.addView(visual, LinearLayout.LayoutParams(-1, dp(126)).apply { bottomMargin = dp(7) })

        speechBubble = TextView(this).apply {
            textSize = 18f
            setTextColor(Color.rgb(57, 41, 67))
            gravity = Gravity.CENTER
            minHeight = dp(80)
            maxLines = 3
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = roundedBackground(Color.rgb(255, 251, 255), 28f)
        }
        root.addView(speechBubble, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) })

        choices = GridLayout(this).apply {
            columnCount = 3
            rowCount = 2
            alignmentMode = GridLayout.ALIGN_BOUNDS
            setPadding(0, dp(3), 0, dp(3))
        }
        root.addView(choices, LinearLayout.LayoutParams(-1, dp(148)))

        status = TextView(this).apply {
            text = "Toque na Lumi ou no microfone e fale"
            textSize = 13f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        root.addView(status, LinearLayout.LayoutParams(-1, dp(22)))

        root.addView(Button(this).apply {
            text = "🎙  FALAR COM A LUMI  🎙"
            textSize = 18f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            background = roundedBackground(Color.rgb(246, 113, 160), 30f)
            setOnClickListener { touchInteraction(); startListening() }
        }, LinearLayout.LayoutParams(-1, dp(58)))

        return root
    }

    private fun renderReply(reply: ConversationReply) {
        speechBubble.text = reply.text
        setFairyMood(reply.mood)
        visual.showScene(reply.scene)
        val showVocabulary = reply.text.startsWith("Olha as figuras")
        val showGuess = reply.imageKey != null
        vocabularyBoard.visibility = if (showVocabulary) View.VISIBLE else View.GONE
        guessImage.visibility = if (showGuess) View.VISIBLE else View.GONE
        if (showGuess) guessImage.setImageResource(resources.getIdentifier("word_${reply.imageKey}", "drawable", packageName))
        fairy.visibility = if (showVocabulary || showGuess) View.GONE else View.VISIBLE
        visual.visibility = if (reply.scene == VisualScene.NONE || showVocabulary || showGuess) View.GONE else View.VISIBLE
        waitingForMovement = reply.waitForMovement
        renderChoices(reply.choices)
        reply.parentAlert?.let { events.record("parent_alert", it, "child=${profile.name}") }
        if (reply.playTune && ::music.isInitialized) music.play()
        status.text = if (reply.waitForMovement) "A fadinha espera você voltar" else "Sua vez"
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
            val card = choiceCard(label)
            choices.addView(card, GridLayout.LayoutParams(GridLayout.spec(index / 3, 1f), GridLayout.spec(index % 3, 1f)).apply {
                width = 0
                height = if (visible.size > 3) dp(66) else dp(86)
                setMargins(dp(3), dp(3), dp(3), dp(3))
            })
        }
    }

    private fun choiceCard(label: String): View {
        val wordAsset = wordImageKey(label)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            contentDescription = label
            background = roundedBackground(choiceColor(label), 24f)
            setPadding(dp(3), dp(2), dp(3), dp(2))
            if (wordAsset != null) {
                addView(ImageView(this@MainActivity).apply {
                    setImageResource(resources.getIdentifier("word_$wordAsset", "drawable", packageName))
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }, LinearLayout.LayoutParams(-1, 0, 1f))
            } else {
                addView(TextView(this@MainActivity).apply {
                    text = choiceSymbol(label)
                    textSize = 24f
                    gravity = Gravity.CENTER
                }, LinearLayout.LayoutParams(-1, 0, 1f))
            }
            addView(TextView(this@MainActivity).apply {
                text = label
                textSize = 10f
                maxLines = 1
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
            }, LinearLayout.LayoutParams(-1, dp(17)))
            setOnClickListener {
                touchInteraction()
                tracker.recordChoice(label)
                events.record("choice", label)
                engine.onChoice(label).also { reply -> renderReply(reply); speakReply(reply) }
            }
        }
    }

    private fun wordImageKey(label: String) = when (label.uppercase()) {
        "CAVALO" -> "horse"; "GATO" -> "cat"; "CACHORRO" -> "dog"; "SAPO" -> "frog"
        "GALINHA" -> "chicken"; "URSO" -> "bear"; "MACACO" -> "monkey"; "PEIXE" -> "fish"
        "MORANGO" -> "strawberry"; "LARANJA" -> "orange"; "ESCOVA" -> "toothbrush"; "CARRO" -> "car"
        "BICICLETA" -> "bicycle"; "COLEGUINHA" -> "friend"; "COPO" -> "cup"; "COLHER" -> "spoon"
        else -> null
    }

    private fun handleSpoken(text: String) {
        touchInteraction()
        tracker.recordSpeech(text)
        events.record("speech", text)
        engine.reply(text).also { renderReply(it); speakReply(it) }
    }

    private fun touchInteraction() { lastInteractionAt = System.currentTimeMillis() }

    private fun startListening() {
        if (waitingForMovement) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 10)
            return
        }
        autoListenAfterSpeech = false
        voice.stop()
        speech.markIdle()
        status.postDelayed({
            if (!speech.startListening()) {
                speech.markIdle()
                status.text = "Toca de novo e pode falar."
            }
        }, 180)
    }

    private fun setFairyMood(mood: RobotMood) {
        when (mood) {
            RobotMood.LISTENING -> fairy.animate().alpha(1f).scaleX(1.025f).scaleY(1.025f).setDuration(160).start()
            RobotMood.SPEAKING -> fairy.animate().alpha(1f).scaleX(1.012f).scaleY(1.012f).setDuration(160).start()
            RobotMood.SAD -> fairy.animate().alpha(.93f).scaleX(1f).scaleY(1f).setDuration(180).start()
            else -> fairy.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).start()
        }
    }

    private fun setFairyVoiceLevel(level: Float) {
        val scale = if (level > 7f) 1.018f else 1f
        fairy.animate().scaleX(scale).scaleY(scale).setDuration(80).start()
    }

    private fun renderInteractionState(state: InteractionState) {
        status.text = when (state) {
            InteractionState.IDLE -> "Sua vez"
            InteractionState.SPEAKING -> "A fadinha está falando"
            InteractionState.WAITING -> "Pode falar"
            InteractionState.LISTENING -> "Estou te ouvindo"
            InteractionState.PROCESSING -> "Entendendo o que você falou"
        }
        when (state) {
            InteractionState.LISTENING -> setFairyMood(RobotMood.LISTENING)
            InteractionState.PROCESSING -> setFairyMood(RobotMood.THINKING)
            InteractionState.SPEAKING -> setFairyMood(RobotMood.SPEAKING)
            else -> Unit
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
            touchInteraction()
            events.record("routine", "returned_from_bathroom")
            engine.onMovementDetected().also { renderReply(it); speakReply(it) }
        }
        baselineAcceleration = total
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    override fun onResume() { super.onResume(); if (::updater.isInitialized) updater.onResume() }

    private fun choiceColor(label: String) = when (label) {
        "A", "FELIZ", "ANIMADA", "HISTÓRIA" -> Color.rgb(244, 170, 73)
        "C", "SIM", "ANIMAIS", "CAVALO" -> Color.rgb(72, 185, 133)
        "P", "NÃO", "TRISTE", "ABC" -> Color.rgb(78, 150, 226)
        "IMAGENS" -> Color.rgb(238, 121, 171)
        "BRAVA" -> Color.rgb(232, 105, 105)
        "MEDO", "CARINHAS" -> Color.rgb(151, 104, 211)
        else -> Color.rgb(142, 91, 190)
    }

    private fun startFairyIdleAnimation() {
        val floatUpAndDown = ObjectAnimator.ofFloat(fairy, View.TRANSLATION_Y, 0f, -dp(7).toFloat(), 0f).apply {
            duration = 2100L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
        }
        val gentleWiggle = ObjectAnimator.ofFloat(fairy, View.ROTATION, -1.1f, 1.1f, -1.1f).apply {
            duration = 3100L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        }
        fairyIdleAnimation = AnimatorSet().apply {
            playTogether(floatUpAndDown, gentleWiggle)
            start()
        }
    }

    private fun choiceSymbol(label: String) = when (label) {
        "BRINCAR" -> "🎲"
        "ADIVINHA" -> "🔎"
        "ANIMAIS" -> "🐴"
        "ABC", "LETRAS" -> "🔤"
        "HISTÓRIA", "HISTORIA" -> "📖"
        "CARINHAS" -> "😀"
        "ROTINA" -> "🌈"
        "IMAGENS", "PALAVRAS" -> "🖼️"
        "MÚSICA", "MUSICA" -> "🎵"
        "INÍCIO", "INICIO" -> "🏠"
        "FELIZ" -> "😀"
        "TRISTE" -> "😢"
        "BRAVA" -> "😠"
        "MEDO" -> "😨"
        "CANSADA" -> "😴"
        "ANIMADA" -> "🤩"
        "SIM", "CONSEGUI" -> "✅"
        "NÃO", "NAO" -> "❌"
        "CAVALO" -> "🐴"
        "GATO" -> "🐱"
        "CACHORRO" -> "🐶"
        "A" -> "🍎"
        "C" -> "🐴"
        "P" -> "🍞"
        "ÁGUA", "AGUA" -> "💧"
        "BANHEIRO" -> "🚽"
        "CONTAR", "CONTAR MAIS", "QUERO CONTAR" -> "💬"
        "DE NOVO" -> "🔁"
        "COISA BOA" -> "🌟"
        "COISA DIFÍCIL" -> "🧩"
        "BRINCADEIRA" -> "🛝"
        "AZUL" -> "🔵"
        "VERMELHO" -> "🔴"
        "AMARELO" -> "🟡"
        "FADA" -> "🧚"
        "CASA" -> "🏠"
        "PENSAR JUNTAS" -> "💡"
        "CONVERSAR" -> "💬"
        "IR ATÉ A LUZ" -> "🔦"
        "CHAMAR A FADA" -> "🧚"
        "FAMILIA" -> "👨‍👩‍👧"
        "CHAMAR ADULTO" -> "🧑"
        else -> "✨"
    }

    private fun roundedBackground(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius.toInt()).toFloat()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        fairyIdleAnimation?.cancel()
        handler.removeCallbacks(proactivePrompt)
        sensorManager?.unregisterListener(this)
        if (::speech.isInitialized) speech.destroy()
        if (::voice.isInitialized) voice.shutdown()
        if (::music.isInitialized) music.release()
        super.onDestroy()
    }
}
