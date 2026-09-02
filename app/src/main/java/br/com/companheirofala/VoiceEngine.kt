package br.com.companheirofala

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class VoiceEngine(
    context: Context,
    private val onStart: () -> Unit,
    private val onDone: () -> Unit,
    private val onError: () -> Unit
) : TextToSpeech.OnInitListener {

    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var lastText = ""
    private var lastAt = 0L

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            onError()
            return
        }
        ready = true
        configureVoice()
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = onStart()
            override fun onDone(utteranceId: String?) = onDone()
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = onError()
        })
    }

    private fun configureVoice() {
        val locale = Locale.forLanguageTag("pt-BR")
        tts.language = locale
        val candidates = tts.voices.orEmpty().filter {
            it.locale.language == "pt" && it.locale.country.equals("BR", true)
        }
        val chosen = candidates.sortedWith(
            compareByDescending<android.speech.tts.Voice> { !it.isNetworkConnectionRequired }
                .thenByDescending { it.quality }
        ).firstOrNull()
        if (chosen != null) tts.voice = chosen
        tts.setSpeechRate(0.92f)
        tts.setPitch(1.02f)
    }

    fun speak(text: String): Boolean {
        if (!ready || text.isBlank()) return false
        val now = System.currentTimeMillis()
        if (text == lastText && now - lastAt < 3500) return false
        lastText = text
        lastAt = now
        tts.stop()
        return tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "companion_voice") == TextToSpeech.SUCCESS
    }

    fun stop() = tts.stop()
    fun shutdown() = tts.shutdown()
}
