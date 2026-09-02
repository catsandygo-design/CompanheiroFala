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
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var face: RobotFaceView
    private lateinit var status: TextView
    private lateinit var heard: TextView
    private lateinit var listenButton: Button
    private lateinit var sendButton: Button
    private lateinit var input: EditText
    private lateinit var chatColumn: LinearLayout
    private lateinit var chatScroll: ScrollView

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val engine = ConversationEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        setContentView(buildScreen())
        addBotMessage("Oi! Eu sou seu Companheiro Fala. Pode falar comigo ou escrever aqui embaixo.")

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
        } else {
            setupRecognizer()
        }
    }

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(8, 14, 28), Color.rgb(18, 24, 44))
            )
        }

        val title = TextView(this).apply {
            text = "Companheiro Fala"
            textSize = 26f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        root.addView(title, LinearLayout.LayoutParams(-1, dp(42)))

        face = RobotFaceView(this)
        root.addView(face, LinearLayout.LayoutParams(-1, dp(220)))

        status = TextView(this).apply {
            text = "Pronto para conversar"
            textSize = 18f
            setTextColor(Color.rgb(147, 226, 255))
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(6))
        }
        root.addView(status, LinearLayout.LayoutParams(-1, dp(38)))

        heard = TextView(this).apply {
            text = "Toque em FALAR ou escreva uma mensagem."
            textSize = 15f
            setTextColor(Color.rgb(205, 214, 235))
            gravity = Gravity.CENTER
            setPadding(dp(8), 0, dp(8), dp(10))
        }
        root.addView(heard, LinearLayout.LayoutParams(-1, dp(54)))

        chatScroll = ScrollView(this).apply {
            isFillViewport = true
            background = roundedBackground(Color.rgb(14, 21, 37), 18f)
        }
        chatColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        chatScroll.addView(chatColumn)
        root.addView(chatScroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(8))
        }

        listenButton = Button(this).apply {
            text = "FALAR"
            textSize = 18f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = roundedBackground(Color.rgb(48, 109, 255), 18f)
            setOnClickListener { listen() }
        }
        actions.addView(listenButton, LinearLayout.LayoutParams(0, dp(54), 1f).apply { marginEnd = dp(8) })

        val stopButton = Button(this).apply {
            text = "PARAR"
            textSize = 16f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = roundedBackground(Color.rgb(53, 62, 84), 18f)
            setOnClickListener {
                recognizer?.cancel()
                tts?.stop()
                face.setMood(RobotMood.IDLE)
                status.text = "Parei. Quando quiser, pode falar de novo."
                listenButton.isEnabled = true
            }
        }
        actions.addView(stopButton, LinearLayout.LayoutParams(0, dp(54), 1f).apply { marginStart = dp(8) })
        root.addView(actions, LinearLayout.LayoutParams(-1, dp(72)))

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        input = EditText(this).apply {
            hint = "Escreva para conversar..."
            setHintTextColor(Color.rgb(130, 142, 165))
            setTextColor(Color.WHITE)
            textSize = 16f
            maxLines = 3
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = roundedBackground(Color.rgb(27, 36, 57), 16f)
        }
        composer.addView(input, LinearLayout.LayoutParams(0, dp(56), 1f).apply { marginEnd = dp(8) })

        sendButton = Button(this).apply {
            text = "ENVIAR"
            textSize = 14f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = roundedBackground(Color.rgb(95, 75, 220), 16f)
            setOnClickListener {
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    input.setText("")
                    processUserText(text)
                }
            }
        }
        composer.addView(sendButton, LinearLayout.LayoutParams(dp(96), dp(56)))
        root.addView(composer, LinearLayout.LayoutParams(-1, dp(64)))

        return root
    }

    private fun setupRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "Reconhecimento de voz indisponível neste aparelho"
            face.setMood(RobotMood.CONFUSED)
            return
        }

        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    status.text = "Estou ouvindo..."
                    heard.text = "Pode falar naturalmente."
                    face.setMood(RobotMood.LISTENING)
                    listenButton.isEnabled = false
                }

                override fun onBeginningOfSpeech() {
                    status.text = "Pode falar"
                    face.setMood(RobotMood.LISTENING)
                }

                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    status.text = "Pensando no que você disse..."
                    face.setMood(RobotMood.THINKING)
                }

                override fun onError(error: Int) {
                    listenButton.isEnabled = true
                    face.setMood(RobotMood.CONFUSED)
                    status.text = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "Não consegui entender. Fala mais uma vez?"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Não ouvi nada. Tente novamente."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "O reconhecimento de voz teve problema de conexão."
                        else -> "Não consegui ouvir direito. Tente novamente."
                    }
                }

                override fun onResults(results: Bundle?) {
                    listenButton.isEnabled = true
                    val raw = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (raw.isBlank()) {
                        face.setMood(RobotMood.CONFUSED)
                        status.text = "Não entendi. Pode falar de novo?"
                    } else {
                        processUserText(raw)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!partial.isNullOrBlank()) heard.text = "Ouvindo: $partial"
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }
    }

    private fun listen() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
            return
        }
        if (recognizer == null) setupRecognizer()

        tts?.stop()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        recognizer?.startListening(intent)
    }

    private fun processUserText(raw: String) {
        heard.text = "Você disse: $raw"
        addUserMessage(raw)
        face.setMood(RobotMood.THINKING)
        status.text = "Pensando..."

        chatColumn.postDelayed({
            val reply = engine.reply(raw)
            addBotMessage(reply.text)
            status.text = reply.text
            face.setMood(reply.mood)
            speak(reply.text)
        }, 280)
    }

    private fun speak(text: String) {
        if (!ttsReady) {
            face.setMood(RobotMood.HAPPY)
            return
        }
        face.setMood(RobotMood.SPEAKING)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "companion_reply")
    }

    private fun addUserMessage(text: String) {
        chatColumn.addView(messageBubble("Você", text, Color.rgb(64, 82, 190), Gravity.END))
        scrollChatToBottom()
    }

    private fun addBotMessage(text: String) {
        chatColumn.addView(messageBubble("Companheiro", text, Color.rgb(31, 43, 66), Gravity.START))
        scrollChatToBottom()
    }

    private fun messageBubble(author: String, message: String, color: Int, gravity: Int): View {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            this.gravity = gravity
            setPadding(0, dp(4), 0, dp(4))
        }
        val bubble = TextView(this).apply {
            text = "$author\n$message"
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = roundedBackground(color, 16f)
        }
        wrapper.addView(bubble, LinearLayout.LayoutParams(-2, -2).apply { width = (resources.displayMetrics.widthPixels * .78f).toInt() })
        return wrapper
    }

    private fun scrollChatToBottom() {
        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun roundedBackground(color: Int, radiusDp: Float): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp.toInt()).toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onInit(statusCode: Int) {
        if (statusCode == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("pt", "BR"))
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            tts?.setSpeechRate(0.92f)
            tts?.setPitch(1.05f)
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    runOnUiThread { face.setMood(RobotMood.SPEAKING) }
                }

                override fun onDone(utteranceId: String?) {
                    runOnUiThread {
                        face.setMood(RobotMood.HAPPY)
                        status.text = "Estou ouvindo você."
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    runOnUiThread { face.setMood(RobotMood.IDLE) }
                }
            })
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_AUDIO) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                setupRecognizer()
                status.text = "Microfone pronto. Pode falar comigo."
            } else {
                status.text = "Sem microfone, ainda podemos conversar digitando."
                face.setMood(RobotMood.CONFUSED)
            }
        }
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_AUDIO = 10
    }
}
