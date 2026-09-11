package br.com.companheirofala

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
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
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.GridLayout
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.roundToInt
import br.com.companheirofala.core.conversation.ConversationOrchestrator
import br.com.companheirofala.core.ai.ModelFileManager
import br.com.companheirofala.core.ai.LlamaCppLocalProvider
import br.com.companheirofala.core.conversation.ConversationState
import br.com.companheirofala.core.conversation.LLMResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : Activity(), SensorEventListener {
    private lateinit var fairy: ImageView
    private lateinit var visual: ChildVisualView
    private lateinit var speechBubble: TextView
    private lateinit var choices: LinearLayout
    private lateinit var status: TextView
    private lateinit var updater: AppUpdater
    private lateinit var voice: VoiceEngine
    private lateinit var speech: SpeechEngine
    private lateinit var events: ParentEventRepository
    private lateinit var tracker: DevelopmentTracker
    private lateinit var music: LocalMusicEngine
    private lateinit var vocabularyBoard: ImageView
    private lateinit var guessImage: ImageView
    private lateinit var memoryGrid: GridLayout

    private val profile = ChildProfile.gabi()
    private lateinit var orchestrator: ConversationOrchestrator
    private lateinit var modelFiles: ModelFileManager
    private lateinit var localLlm: LlamaCppLocalProvider
    private var settingsModelText: TextView? = null
    private var settingsStatusText: TextView? = null
    private var settingsTestText: TextView? = null
    private var lastLocalTest: LLMResult? = null
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
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
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        // A tela inicial reproduz a arte aprovada; o motor de conversa continua o mesmo.
        setContentView(buildReferenceScreen())
        startFairyIdleAnimation()
        updater = AppUpdater(this)
        events = ParentEventRepository(this)
        tracker = DevelopmentTracker(this)
        music = LocalMusicEngine()
        modelFiles = ModelFileManager(this)
        localLlm = LlamaCppLocalProvider(this)
        orchestrator = ConversationOrchestrator(this, ConversationEngine(profile, LocalChildMemory(this)), localLlm = localLlm)

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

        val intro = orchestrator.start()
        renderReply(intro)
        speakReply(intro)
        handler.postDelayed(proactivePrompt, 30_000L)
        status.postDelayed({ updater.checkAndUpdate { message -> status.text = message } }, 1400)
        // O GGUF/JNI continua acessível em Configurações para teste offline explícito, mas não
        // é carregado ao iniciar: a conversa principal usa o provedor remoto e não disputa RAM.
    }

    private fun buildReferenceScreen(): View {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(126, 196, 255))
            clipChildren = false
        }
        root.addView(ImageView(this).apply {
            setImageResource(R.drawable.lumi_home_reference)
            scaleType = ImageView.ScaleType.FIT_XY
            contentDescription = "Tela inicial da Lumi"
        }, FrameLayout.LayoutParams(-1, -1))

        // Views de estado do aplicativo ficam fora da composição visual inicial. Elas preservam
        // as rotas de voz, escolhas e jogos sem alterar a arte aprovada.
        fairy = ImageView(this).apply {
            setImageResource(R.drawable.fairy_pet)
            alpha = 0f
            contentDescription = "Lumi"
        }
        visual = ChildVisualView(this).apply { visibility = View.GONE }
        speechBubble = TextView(this).apply { visibility = View.GONE }
        choices = LinearLayout(this).apply { visibility = View.GONE }
        vocabularyBoard = ImageView(this).apply { visibility = View.GONE }
        guessImage = ImageView(this).apply { visibility = View.GONE }
        memoryGrid = GridLayout(this).apply { visibility = View.GONE }
        status = TextView(this).apply { visibility = View.GONE }
        listOf(fairy, visual, speechBubble, choices, vocabularyBoard, guessImage, memoryGrid, status).forEach { view ->
            root.addView(view, FrameLayout.LayoutParams(dp(1), dp(1)))
        }

        val microphoneGlow = View(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                setStroke(dp(4), Color.argb(165, 255, 255, 255))
                cornerRadius = dp(70).toFloat()
            }
            isClickable = false
        }
        placeOnReference(root, microphoneGlow, .355f, .730f, .29f, .165f)
        val pulseX = ObjectAnimator.ofFloat(microphoneGlow, View.SCALE_X, 1f, 1.08f, 1f)
        val pulseY = ObjectAnimator.ofFloat(microphoneGlow, View.SCALE_Y, 1f, 1.08f, 1f)
        val pulseAlpha = ObjectAnimator.ofFloat(microphoneGlow, View.ALPHA, .35f, 1f, .35f)
        listOf(pulseX, pulseY, pulseAlpha).forEach { animator ->
            animator.duration = 1600L
            animator.repeatCount = ValueAnimator.INFINITE
        }
        AnimatorSet().apply { playTogether(pulseX, pulseY, pulseAlpha); start() }

        addReferenceTapArea(root, .020f, .060f, .440f, .275f, "Falar com a Lumi") {
            touchInteraction(); animateTap(microphoneGlow); startListening()
        }
        addReferenceTapArea(root, .035f, .338f, .295f, .185f, "Água") { handleSpoken("Quero água") }
        addReferenceTapArea(root, .350f, .338f, .295f, .185f, "Banheiro") { handleSpoken("Quero ir ao banheiro") }
        addReferenceTapArea(root, .665f, .338f, .295f, .185f, "Escovar os dentes") { handleSpoken("Quero escovar os dentes") }
        addReferenceTapArea(root, .035f, .525f, .295f, .185f, "Brincar") { handleSpoken("Quero brincar") }
        addReferenceTapArea(root, .350f, .525f, .295f, .185f, "Dormir") { handleSpoken("Estou com sono") }
        addReferenceTapArea(root, .665f, .525f, .295f, .185f, "Como estou me sentindo") { handleSpoken("Quero falar sobre como estou me sentindo") }
        addReferenceTapArea(root, .345f, .725f, .310f, .165f, "Fale com a Lumi") {
            touchInteraction(); animateTap(microphoneGlow); startListening()
        }
        addReferenceTapArea(root, .785f, .000f, .205f, .075f, "Configurações do responsável") { showParentAiSettings() }
        addReferenceTapArea(root, .755f, .905f, .240f, .095f, "Configurações") { showParentAiSettings() }
        return root
    }

    private fun addReferenceTapArea(parent: FrameLayout, left: Float, top: Float, width: Float, height: Float, description: String, onTap: () -> Unit) {
        val area = View(this).apply {
            contentDescription = description
            isClickable = true
            isFocusable = true
            setOnClickListener { onTap() }
        }
        placeOnReference(parent, area, left, top, width, height)
    }

    private fun placeOnReference(parent: FrameLayout, child: View, left: Float, top: Float, width: Float, height: Float) {
        parent.addView(child, FrameLayout.LayoutParams(1, 1))
        fun updatePosition() {
            if (parent.width == 0 || parent.height == 0) return
            child.layoutParams = FrameLayout.LayoutParams(
                (parent.width * width).roundToInt(),
                (parent.height * height).roundToInt()
            ).apply {
                leftMargin = (parent.width * left).roundToInt()
                topMargin = (parent.height * top).roundToInt()
            }
        }
        parent.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updatePosition() }
        parent.post { updatePosition() }
    }

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), 0)
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(96, 182, 255), Color.rgb(211, 244, 255), Color.rgb(255, 247, 207))
            )
            setOnApplyWindowInsetsListener { v, insets ->
                val bottom = insets.getInsets(WindowInsets.Type.navigationBars()).bottom
                v.setPadding(dp(12), dp(8), dp(12), bottom)
                insets
            }
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(14))
        }
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@MainActivity).apply {
                text = "✦ LUMI E GABI ✦"
                textSize = 19f
                setTextColor(Color.rgb(74, 47, 133))
                gravity = Gravity.CENTER_VERTICAL
                setTypeface(typeface, Typeface.BOLD)
            }, LinearLayout.LayoutParams(0, dp(42), 1f))
            addView(Button(this@MainActivity).apply {
                text = "⚙"
                textSize = 18f
                isAllCaps = false
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
                contentDescription = "Configurações do responsável"
                background = roundedBackground(Color.rgb(131, 82, 204), 24f)
                setOnClickListener { showParentAiSettings() }
            }, LinearLayout.LayoutParams(dp(52), dp(40)))
        }, LinearLayout.LayoutParams(-1, dp(44)))

        val fairyStage = FrameLayout(this).apply {
            background = roundedBackground(Color.argb(235, 255, 255, 255), 38f)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        fairy = ImageView(this).apply {
            setImageResource(R.drawable.fairy_pet)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = false
            contentDescription = "Lumi, a fadinha companheira"
            clipToOutline = false
            setOnClickListener { touchInteraction(); startListening() }
        }
        fairyStage.addView(fairy, FrameLayout.LayoutParams(dp(185), -1, Gravity.START or Gravity.CENTER_VERTICAL))
        fairyStage.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = roundedBackground(Color.rgb(255, 251, 255), 30f)
            setPadding(dp(13), dp(10), dp(13), dp(10))
            addView(TextView(this@MainActivity).apply {
                text = "Oi, Gabi! ✨"
                textSize = 25f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(104, 54, 181))
                setTypeface(typeface, Typeface.BOLD)
            })
            addView(TextView(this@MainActivity).apply {
                text = "Vamos brincar e aprender?"
                textSize = 15f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(103, 86, 145))
            })
        }, FrameLayout.LayoutParams(-1, dp(116), Gravity.END or Gravity.CENTER_VERTICAL).apply { leftMargin = dp(142); rightMargin = dp(4) })
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
        memoryGrid = GridLayout(this).apply {
            columnCount = 3
            rowCount = 3
            visibility = View.GONE
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        fairyStage.addView(memoryGrid, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        content.addView(fairyStage, LinearLayout.LayoutParams(-1, dp(208)).apply { bottomMargin = dp(8) })

        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBackground(Color.argb(235, 255, 255, 255), 28f)
            setPadding(dp(15), dp(8), dp(15), dp(8))
            addView(TextView(this@MainActivity).apply { text = "⭐"; textSize = 30f }, LinearLayout.LayoutParams(dp(42), dp(44)))
            addView(TextView(this@MainActivity).apply {
                text = "Hoje você já brilhou!\nContinue assim, Gabi!"
                textSize = 13f
                setTextColor(Color.rgb(85, 63, 132))
                setTypeface(typeface, Typeface.BOLD)
            }, LinearLayout.LayoutParams(0, dp(44), 1f))
            addView(TextView(this@MainActivity).apply {
                text = "3/6 ⭐"
                textSize = 14f
                setTextColor(Color.rgb(115, 70, 181))
                setTypeface(typeface, Typeface.BOLD)
            })
        }, LinearLayout.LayoutParams(-1, dp(62)).apply { bottomMargin = dp(8) })

        visual = ChildVisualView(this).apply { visibility = View.GONE }
        content.addView(visual, LinearLayout.LayoutParams(-1, dp(126)).apply { bottomMargin = dp(7) })

        speechBubble = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.rgb(57, 41, 67))
            gravity = Gravity.CENTER
            minHeight = dp(70)
            maxLines = 3
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = roundedBackground(Color.rgb(255, 255, 255), 26f)
        }
        content.addView(speechBubble, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) })

        choices = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        content.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(choices)
        }, LinearLayout.LayoutParams(-1, dp(82)))

        status = TextView(this).apply {
            text = "Toque e fale com a Lumi"
            textSize = 13f
            setTextColor(Color.rgb(83, 63, 122))
            gravity = Gravity.CENTER
        }
        content.addView(status, LinearLayout.LayoutParams(-1, dp(24)))

        val quickGrid = GridLayout(this).apply { columnCount = 3; rowCount = 2; setPadding(dp(2), dp(2), dp(2), dp(2)) }
        listOf(
            Triple("💧", "Água", "Quero água"), Triple("🚽", "Banheiro", "Quero ir ao banheiro"),
            Triple("🪥", "Escovar", "Quero escovar os dentes"), Triple("🧸", "Brincar", "Quero brincar"),
            Triple("🌙", "Dormir", "Estou com sono"), Triple("😊", "Como estou?", "Quero falar sobre como estou me sentindo")
        ).forEachIndexed { index, item ->
            quickGrid.addView(quickActionCard(item.first, item.second, item.third, quickCardColor(index)), GridLayout.LayoutParams(
                GridLayout.spec(index / 3, 1f), GridLayout.spec(index % 3, 1f)
            ).apply { width = 0; height = dp(114); setMargins(dp(4), dp(4), dp(4), dp(4)) })
        }
        content.addView(quickGrid, LinearLayout.LayoutParams(-1, dp(244)).apply { bottomMargin = dp(8) })

        val microphone = TextView(this).apply {
            text = "🎙"
            textSize = 48f
            gravity = Gravity.CENTER
            contentDescription = "Falar com a Lumi"
            background = roundedBackground(Color.rgb(246, 82, 145), 56f)
            elevation = dp(8).toFloat()
            setOnClickListener { touchInteraction(); animateTap(this); startListening() }
        }
        content.addView(FrameLayout(this).apply {
            addView(microphone, FrameLayout.LayoutParams(dp(98), dp(98), Gravity.CENTER))
            addView(TextView(this@MainActivity).apply {
                text = "Fale com a Lumi 💗"
                textSize = 16f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(96, 54, 145))
                setTypeface(typeface, Typeface.BOLD)
            }, FrameLayout.LayoutParams(-1, dp(28), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        }, LinearLayout.LayoutParams(-1, dp(132)))

        root.addView(ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(content)
        }, LinearLayout.LayoutParams(-1, 0, 1f))

        root.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.HORIZONTAL
            background = roundedBackground(Color.argb(245, 255, 255, 255), 30f)
            listOf("⌂\nInício", "★\nConquistas", "♥\nAmigos", "⚙\nConfigurações").forEachIndexed { index, label ->
                addView(TextView(this@MainActivity).apply {
                    text = label
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setTextColor(if (index == 0) Color.rgb(124, 70, 196) else Color.rgb(109, 103, 128))
                    setTypeface(typeface, Typeface.BOLD)
                    setOnClickListener { if (index == 3) showParentAiSettings() else animateTap(this) }
                }, LinearLayout.LayoutParams(0, -1, 1f))
            }
        }, LinearLayout.LayoutParams(-1, dp(68)))

        return root
    }

    private fun quickActionCard(icon: String, label: String, utterance: String, color: Int): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        contentDescription = label
        background = roundedBackground(color, 28f)
        elevation = dp(5).toFloat()
        setPadding(dp(4), dp(5), dp(4), dp(5))
        val iconView = TextView(this@MainActivity).apply { text = icon; textSize = 36f; gravity = Gravity.CENTER }
        addView(iconView, LinearLayout.LayoutParams(-1, 0, 1f))
        addView(TextView(this@MainActivity).apply {
            text = label
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(-1, dp(28)))
        ObjectAnimator.ofFloat(iconView, View.TRANSLATION_Y, 0f, -dp(4).toFloat(), 0f).apply {
            duration = 1800L + (label.length * 100L)
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
        setOnClickListener { animateTap(this); handleSpoken(utterance) }
    }

    private fun quickCardColor(index: Int) = when (index) {
        0 -> Color.rgb(70, 182, 232); 1 -> Color.rgb(125, 195, 84); 2 -> Color.rgb(244, 111, 170)
        3 -> Color.rgb(247, 169, 59); 4 -> Color.rgb(132, 92, 211); else -> Color.rgb(55, 185, 174)
    }

    private fun animateTap(view: View) {
        view.animate().scaleX(.93f).scaleY(.93f).setDuration(90).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
        }.start()
    }

    private fun renderReply(reply: ConversationReply) {
        speechBubble.text = reply.text
        setFairyMood(reply.mood)
        visual.showScene(reply.scene)
        val showVocabulary = reply.text.startsWith("Olha as figuras")
        val showGuess = reply.imageKey != null
        val showMemory = reply.memoryTiles.isNotEmpty()
        vocabularyBoard.visibility = if (showVocabulary) View.VISIBLE else View.GONE
        guessImage.visibility = if (showGuess) View.VISIBLE else View.GONE
        if (showGuess) guessImage.setImageResource(resources.getIdentifier("word_${reply.imageKey}", "drawable", packageName))
        memoryGrid.visibility = if (showMemory) View.VISIBLE else View.GONE
        if (showMemory) renderMemoryGrid(reply.memoryTiles)
        fairy.visibility = if (showVocabulary || showGuess || showMemory) View.GONE else View.VISIBLE
        visual.visibility = if (reply.scene == VisualScene.NONE || showVocabulary || showGuess || showMemory) View.GONE else View.VISIBLE
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
            choices.addView(card, LinearLayout.LayoutParams(dp(104), -1).apply {
                setMargins(dp(5), 0, dp(5), 0)
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
            elevation = dp(4).toFloat()
            setPadding(dp(4), dp(4), dp(4), dp(4))
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
                orchestrator.onChoice(label).also { reply -> renderReply(reply); speakReply(reply) }
            }
        }
    }

    private fun renderMemoryGrid(tiles: List<MemoryTile>) {
        memoryGrid.removeAllViews()
        tiles.forEachIndexed { index, tile ->
            val faceUp = tile.revealed || tile.matched
            val card = ImageView(this).apply {
                contentDescription = if (faceUp) "Bichinho revelado" else "Carta escondida"
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = roundedBackground(if (faceUp) Color.WHITE else Color.rgb(123, 78, 184), 22f)
                setPadding(dp(3), dp(3), dp(3), dp(3))
                if (faceUp) {
                    val resource = if (tile.imageKey == "fairy") R.drawable.fairy_pet else resources.getIdentifier("word_${tile.imageKey}", "drawable", packageName)
                    setImageResource(resource)
                } else setImageResource(android.R.drawable.ic_menu_help)
                setOnClickListener {
                    touchInteraction()
                    orchestrator.onChoice("MEMORY_$index").also { reply -> renderReply(reply); speakReply(reply) }
                }
            }
            memoryGrid.addView(card, GridLayout.LayoutParams(GridLayout.spec(index / 3, 1f), GridLayout.spec(index % 3, 1f)).apply {
                width = 0
                height = 0
                setMargins(dp(4), dp(4), dp(4), dp(4))
            })
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
        // A conversa principal é remota e assíncrona. O antigo watchdog de 12 segundos
        // respondia "repete" e descartava a resposta real que ainda estava chegando.
        // Não há inferência JNI nesta rota para cancelar.
        status.text = "Lumi está pensando..."
        activityScope.launch {
            try {
                val reply = orchestrator.reply(text)
                renderReply(reply)
                speakReply(reply)
            } catch (error: Exception) {
                status.text = "Não consegui falar com a Lumi agora. Tente de novo."
                events.record("conversation_error", error.message ?: error.javaClass.simpleName)
            }
        }
    }

    private fun showParentAiSettings() {
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(8), dp(22), dp(8)) }
        fun heading(value: String) = TextView(this).apply { text = value; textSize = 18f; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.rgb(72, 52, 104)); setPadding(0, dp(12), 0, dp(6)) }
        fun body() = TextView(this).apply { textSize = 14f; setTextColor(Color.DKGRAY); setPadding(0, dp(4), 0, dp(8)) }
        content.addView(heading("IA LOCAL"))
        settingsModelText = body(); content.addView(settingsModelText)
        content.addView(Button(this).apply { text = "SELECIONAR MODELO GGUF"; isAllCaps = false; setOnClickListener { openGgufPicker() } })
        content.addView(heading("Status"))
        settingsStatusText = body(); content.addView(settingsStatusText)
        content.addView(Button(this).apply { text = "TESTAR IA LOCAL"; isAllCaps = false; setOnClickListener { testLocalAiDirectly() } })
        content.addView(Button(this).apply { text = "DIAGNÓSTICO IA"; isAllCaps = false; setOnClickListener { showAiDiagnostics() } })
        settingsTestText = body(); content.addView(settingsTestText)
        refreshAiSettings()
        AlertDialog.Builder(this).setTitle("CONFIGURAÇÕES DO RESPONSÁVEL")
            .setView(ScrollView(this).apply { addView(content) })
            .setNegativeButton("FECHAR", null).show()
    }

    private fun openGgufPicker() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/octet-stream", "application/gguf", "*/*"))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }, REQUEST_MODEL_FILE)
    }

    private fun refreshAiSettings(detail: String? = null) {
        val file = modelFiles.selectedModel()
        settingsModelText?.text = if (file == null) "Modelo:\n[Nenhum modelo selecionado]" else "Modelo:\n${file.name}\nTamanho: ${formatBytes(file.length())}\nCaminho interno: ${file.absolutePath}"
        settingsStatusText?.text = detail ?: if (localLlm.isAvailable()) "MODELO CARREGADO" else "IA LOCAL NÃO CARREGADA"
    }

    private fun showAiDiagnostics() {
        val file = modelFiles.selectedModel()
        val test = lastLocalTest
        val details = "STATUS JNI=${if (localLlm.isJniReady()) "CARREGADO" else "ERRO"}\nMODELO=${file?.name ?: "Nenhum"}\nCAMINHO=${file?.absolutePath ?: ""}\nTAMANHO=${formatBytes(file?.length() ?: 0)}\nMODELO CARREGADO=${if (localLlm.isAvailable()) "SIM" else "NÃO"}\nCONTEXTO CRIADO=${if (localLlm.isContextCreated()) "SIM" else "NÃO"}\nTEMPO DE CARREGAMENTO=${localLlm.lastLoadLatencyMs} ms\n\nLOCAL_MODEL_AVAILABLE=${localLlm.isAvailable()}\nLOCAL_MODEL_LOADED=${localLlm.isAvailable()}\nLOCAL_MODEL_NAME=${file?.name ?: "Nenhum"}\nMODEL_SIZE=${file?.length() ?: 0}\nMODEL_PATH=${file?.absolutePath ?: ""}\nLLM_CALLED=${test?.invoked ?: false}\nLLM_RESPONSE=${test?.text ?: ""}\nLLM_LATENCY_MS=${localLlm.lastGenerationLatencyMs}\nTOKENS GERADOS=${localLlm.lastGeneratedTokenCount}\nERROR=${test?.error ?: localLlm.lastError ?: ""}"
        AlertDialog.Builder(this).setTitle("DIAGNÓSTICO IA").setMessage(details)
            .setPositiveButton("Fechar", null)
            .setNeutralButton("TESTAR IA LOCAL") { _, _ -> testLocalAiDirectly() }
            .show()
    }

    private fun testLocalAiDirectly() {
        val prompt = "Responda em português do Brasil, em uma frase curta: qual animal faz miau?"
        settingsTestText?.text = "Procurando modelo..."
        status.text = "Procurando modelo..."
        activityScope.launch {
            settingsTestText?.text = "Carregando IA local..."
            val ready = withContext(Dispatchers.Default) { localLlm.loadModel() }
            val result = if (ready) {
                status.text = "Gerando resposta..."
                withContext(Dispatchers.Default) { localLlm.generate(prompt, ConversationState()) }
            } else LLMResult("", 0f, br.com.companheirofala.core.conversation.LLMProvider.LOCAL, 0, false, localLlm.lastError, false)
            lastLocalTest = result
            status.text = if (result.success) "Teste IA local concluído" else "Erro no teste IA local"
            val file = modelFiles.selectedModel()
            val message = "STATUS JNI: ${if (localLlm.isJniReady()) "CARREGADO" else "ERRO"}\nMODELO: ${file?.name ?: "Nenhum"}\nCAMINHO: ${file?.absolutePath ?: ""}\nTAMANHO: ${formatBytes(file?.length() ?: 0)}\nMODELO CARREGADO: ${if (localLlm.isAvailable()) "SIM" else "NÃO"}\nCONTEXTO CRIADO: ${if (localLlm.isContextCreated()) "SIM" else "NÃO"}\n\nPROMPT:\n$prompt\n\nRESPOSTA:\n${result.text.ifBlank { "(sem resposta)" }}\n\nTEMPO DE CARREGAMENTO:\n${localLlm.lastLoadLatencyMs} ms\n\nTEMPO DE GERAÇÃO:\n${localLlm.lastGenerationLatencyMs} ms\n\nTOKENS GERADOS:\n${localLlm.lastGeneratedTokenCount}\n\nROTA:\nLOCAL_LLM\n\nERRO:\n${result.error ?: localLlm.lastError ?: "nenhum"}"
            settingsTestText?.text = message
            AlertDialog.Builder(this@MainActivity).setTitle("TESTE IA LOCAL").setMessage(message).setPositiveButton("FECHAR", null).show()
        }
    }

    private fun autoLoadSavedModel() {
        if (modelFiles.selectedModel() == null) return
        status.text = "Carregando IA local..."
        activityScope.launch {
            val ready = withContext(Dispatchers.Default) { localLlm.loadModel() }
            val message = if (ready) "IA local carregada" else "IA local não carregou: ${localLlm.lastError ?: "erro desconhecido"}"
            status.text = message
            refreshAiSettings(message)
        }
    }

    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_MODEL_FILE || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        settingsStatusText?.text = "Importando modelo..."
        status.text = "Importando modelo..."
        activityScope.launch {
            val result = withContext(Dispatchers.IO) { modelFiles.import(uri) { progress -> runOnUiThread { settingsStatusText?.text = "Importando modelo... $progress%"; status.text = "Importando modelo... $progress%" } } }
            result.onSuccess { file ->
                status.text = "Carregando ${file.name}..."
                refreshAiSettings("Importado: ${file.name}. Carregando modelo...")
                val ready = withContext(Dispatchers.Default) { localLlm.loadModel() }
                val state = if (ready) "MODELO CARREGADO" else "ERRO AO CARREGAR\n${localLlm.lastError ?: "llama.cpp recusou o GGUF"}"
                status.text = state
                refreshAiSettings(state)
            }.onFailure { error -> val state = "ERRO AO CARREGAR\n${error.message ?: "erro desconhecido"}"; status.text = state; refreshAiSettings(state) }
        }
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L * 1024L -> "%.2f GB".format(bytes.toDouble() / (1024 * 1024 * 1024))
        bytes >= 1024L * 1024L -> "%.2f MB".format(bytes.toDouble() / (1024 * 1024))
        else -> "$bytes bytes"
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
            orchestrator.onMovementDetected().also { renderReply(it); speakReply(it) }
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
        val floatUpAndDown = ObjectAnimator.ofFloat(fairy, View.TRANSLATION_Y, 0f, -dp(12).toFloat(), 0f, -dp(5).toFloat(), 0f).apply {
            duration = 2600L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
        }
        val gentleWiggle = ObjectAnimator.ofFloat(fairy, View.ROTATION, -2.2f, 2.2f, -2.2f).apply {
            duration = 3400L
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
        "MEMÓRIA", "MEMORIA" -> "🧠"
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

    private companion object {
        const val REQUEST_MODEL_FILE = 907
    }

    override fun onDestroy() {
        activityScope.cancel()
        fairyIdleAnimation?.cancel()
        handler.removeCallbacks(proactivePrompt)
        sensorManager?.unregisterListener(this)
        if (::speech.isInitialized) speech.destroy()
        if (::voice.isInitialized) voice.shutdown()
        if (::music.isInitialized) music.release()
        super.onDestroy()
    }
}
