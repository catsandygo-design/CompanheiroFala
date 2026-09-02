package br.com.companheirofala

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var face: RobotFaceView
    private lateinit var speechBubble: TextView
    private lateinit var status: TextView
    private lateinit var talkButton: Button
    private lateinit var pauseButton: Button

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var continuousMode = false
    private var destroyed = false
    private val engine = ConversationEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        setContentView(buildScreen())

        showBubble("Oi! Eu sou seu Companheiro Fala. Quer conversar ou brincar comigo?", RobotMood.HAPPY)

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
        } else {
            setupRecognizer()
        }
    }

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(14))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(5, 11, 24), Color.rgb(14, 24, 48))
            )
        }

        val header = TextView(this).apply {
            text = "COMPANHEIRO FALA  •  v0.3"
            textSize = 14f
            letterSpacing = .08f
            setTextColor(Color.rgb(139, 201, 255))
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
        }
        root.addView(header, LinearLayout.LayoutParams(-1, dp(32)))

        face = RobotFaceView(this)
        root.addView(face, LinearLayout.LayoutParams(-1, 0, 4.2f))

        speechBubble = TextView(this).apply {
            textSize = 21f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(18), dp(20), dp(18))
            setTypeface(typeface, Typeface.BOLD)
            background = roundedBackground(Color.rgb(24, 38, 68), 24f)
        }
        root.addView(speechBubble, LinearLayout.LayoutParams(-1, 0, 2.1f).apply {
            topMargin = dp(6)
            bottomMargin = dp(8)
        })

        status = TextView(this).apply {
            text = "Escolha uma brincadeira ou toque em CONVERSAR"
            textSize = 14f
            setTextColor(Color.rgb(178, 197, 226))
            gravity = Gravity.CENTER
        }
        root.addView(status, LinearLayout.LayoutParams(-1, dp(36)))

        val activitiesTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        activitiesTop.addView(activityButton("ANIMAIS") { startActivityMode(PlayMode.ANIMALS) }, weightedButtonParams(true))
        activitiesTop.addView(activityButton("CORES") { startActivityMode(PlayMode.COLORS) }, weightedButtonParams(false))
        root.addView(activitiesTop, LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(7) })

        val activitiesBottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        activitiesBottom.addView(activityButton("HISTÓRIA") { startActivityMode(PlayMode.STORY) }, weightedButtonParams(true))
        activitiesBottom.addView(activityButton("ADIVINHA") { startActivityMode(PlayMode.GUESS) }, weightedButtonParams(false))
        root.addView(activitiesBottom, LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(10) })

        val mainActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        talkButton = Button(this).apply {
            text = "CONVERSAR"
            textSize = 19f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            background = roundedBackground(Color.rgb(37, 112, 255), 24f)
            setOnClickListener {
                continuousMode = true
                val intro = engine.start(PlayMode.CHAT)
                showBubble(intro.text, intro.mood)
                speak(intro.text, true)
            }
        }
        mainActions.addView(talkButton, LinearLayout.LayoutParams(0, dp(64), 2f).apply { marginEnd = dp(7) })

        pauseButton = Button(this).apply {
            text = "PAUSAR"
            textSize = 15f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = roundedBackground(Color.rgb(55, 67, 91), 24f)
            setOnClickListener { pauseConversation() }
        }
        mainActions.addView(pauseButton, LinearLayout.LayoutParams(0, dp(64), 1f).apply { marginStart = dp(7) })
        root.addView(mainActions, LinearLayout.LayoutParams(-1, dp(68)))

        return root
    }

    private fun activityButton(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 15f
        isAllCaps = false
        setTextColor(Color.rgb(225, 239, 255))
        setTypeface(typeface, Typeface.BOLD)
        background = roundedBackground(Color.rgb(31, 51, 84), 18f)
        setOnClickListener { onClick() }
    }

    private fun weightedButtonParams(first: Boolean) = LinearLayout.LayoutParams(0, -1, 1f).apply {
        if (first) marginEnd = dp(5) else marginStart = dp(5)
    }

    private fun startActivityMode(mode: PlayMode) {
        continuousMode = true
        recognizer?.cancel()
        val reply = engine.start(mode)
        showBubble(reply.text, reply.mood)
        status.text = "Depois que eu falar, responda com a sua voz"
        speak(reply.text, true)
    }

    private fun pauseConversation() {
        continuousMode = false
        recognizer?.cancel()
        tts?.stop()
        face.setMood(RobotMood.IDLE)
        showBubble("Pausamos. Quando quiser brincar de novo, é só tocar em um botão.", RobotMood.HAPPY)
        status.text = "Pausado"
        talkButton.isEnabled = true
    }

    private fun setupRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            showBubble("Eu não consegui acessar o reconhecimento de voz neste aparelho.", RobotMood.CONFUSED)
            status.text = "Reconhecimento de voz indisponível"
            return
        }

        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    face.setMood(RobotMood.LISTENING)
                    status.text = "Estou ouvindo você..."
                    talkButton.isEnabled = false
                }

                override fun onBeginningOfSpeech() {
                    face.setMood(RobotMood.LISTENING)
                    status.text = "Pode falar"
                }

                override fun onRmsChanged(rmsdB: Float) {
                    face.setVoiceLevel(rmsdB)
                }

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    face.setMood(RobotMood.THINKING)
                    face.setVoiceLevel(0f)
                    status.text = "Pensando..."
                }

                override fun onError(error: Int) {
                    talkButton.isEnabled = true
                    face.setVoiceLevel(0f)
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> showBubble("Não entendi dessa vez. Toca em CONVERSAR e fala de novo?", RobotMood.CONFUSED)
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> showBubble("Acho que você ficou quietinho. Quando quiser, pode falar comigo.", RobotMood.HAPPY)
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> showBubble("A voz ficou sem conexão por um instante. Vamos tentar outra vez?", RobotMood.CONFUSED)
                        else -> showBubble("Não consegui ouvir direito. Vamos tentar mais uma vez?", RobotMood.CONFUSED)
                    }
                    status.text = "Toque em CONVERSAR para tentar novamente"
                }

                override fun onResults(results: Bundle?) {
                    talkButton.isEnabled = true
                    val raw = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (raw.isBlank()) {
                        showBubble("Não ouvi direitinho. Pode falar mais uma vez?", RobotMood.CONFUSED)
                        return
                    }
                    processUserText(raw)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!partial.isNullOrBlank()) status.text = "Ouvi: ${partial.take(55)}"
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }
    }

    private fun listen() {
        if (destroyed || !continuousMode) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
            return
        }
        if (recognizer == null) setupRecognizer()

        tts?.stop()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1300L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
        }
        recognizer?.startListening(intent)
    }

    private fun processUserText(raw: String) {
        face.setMood(RobotMood.THINKING)
        status.text = "Você disse: ${raw.take(55)}"
        val reply = engine.reply(raw)
        showBubble(reply.text, reply.mood)
        speak(reply.text, reply.keepListening)
    }

    private fun showBubble(text: String, mood: RobotMood) {
        speechBubble.text = text
        face.setMood(mood)
    }

    private fun speak(text: String, listenAfter: Boolean) {
        if (!ttsReady) {
            face.setMood(RobotMood.HAPPY)
            if (listenAfter && continuousMode) speechBubble.postDelayed({ listen() }, 500)
            return
        }
        face.setMood(RobotMood.SPEAKING)
        val id = if (listenAfter) "reply_and_listen" else "reply_only"
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    private fun configureFriendlyVoice() {
        val brazil = Locale("pt", "BR")
        val voices = tts?.voices.orEmpty()
        val ptBr = voices.filter { voice ->
            voice.locale.language == "pt" && (voice.locale.country == "BR" || voice.locale.country.isBlank())
        }
        val chosen = ptBr.maxByOrNull { voice ->
            (if (!voice.isNetworkConnectionRequired) 1000 else 0) + voice.quality
        }
        if (chosen != null) tts?.voice = chosen
        tts?.setLanguage(brazil)
        tts?.setSpeechRate(0.84f)
        tts?.setPitch(1.12f)
    }

    override fun onInit(statusCode: Int) {
        if (statusCode != TextToSpeech.SUCCESS) return
        val result = tts?.setLanguage(Locale("pt", "BR"))
        ttsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
        if (!ttsReady) return

        configureFriendlyVoice()
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                runOnUiThread {
                    face.setMood(RobotMood.SPEAKING)
                    status.text = "Estou falando..."
                }
            }

            override fun onDone(utteranceId: String?) {
                runOnUiThread {
                    face.setMood(RobotMood.HAPPY)
                    if (utteranceId == "reply_and_listen" && continuousMode) {
                        status.text = "Agora é sua vez"
                        speechBubble.postDelayed({ listen() }, 650)
                    } else {
                        status.text = "Pronto"
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                runOnUiThread {
                    face.setMood(RobotMood.CONFUSED)
                    status.text = "Não consegui falar dessa vez"
                }
            }
        })
    }

    private fun roundedBackground(color: Int, radiusDp: Float): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp.toInt()).toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_AUDIO) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                setupRecognizer()
                status.text = "Microfone pronto"
            } else {
                showBubble("Eu preciso do microfone para conversar por voz.", RobotMood.CONFUSED)
                status.text = "Permissão de microfone não concedida"
            }
        }
    }

    override fun onDestroy() {
        destroyed = true
        continuousMode = false
        recognizer?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_AUDIO = 10
    }
}
