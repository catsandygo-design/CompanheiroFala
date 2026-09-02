package br.com.companheirofala

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

enum class InteractionState { IDLE, SPEAKING, WAITING, LISTENING, PROCESSING }

class SpeechEngine(
    context: Context,
    private val onState: (InteractionState) -> Unit,
    private val onLevel: (Float) -> Unit,
    private val onResult: (String) -> Unit,
    private val onFailure: () -> Unit
) {
    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var state = InteractionState.IDLE
    private var lastStartAt = 0L

    fun startListening(): Boolean {
        if (state == InteractionState.SPEAKING || state == InteractionState.LISTENING || state == InteractionState.PROCESSING) return false
        val now = System.currentTimeMillis()
        if (now - lastStartAt < 700) return false
        lastStartAt = now
        ensureRecognizer()
        state = InteractionState.WAITING
        onState(state)
        recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1300L)
        })
        return true
    }

    fun markSpeaking() {
        cancel()
        state = InteractionState.SPEAKING
        onState(state)
    }

    fun markIdle() {
        state = InteractionState.IDLE
        onState(state)
    }

    fun cancel() {
        recognizer?.cancel()
        if (state != InteractionState.SPEAKING) {
            state = InteractionState.IDLE
            onState(state)
        }
    }

    private fun ensureRecognizer() {
        if (recognizer != null) return
        recognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { state = InteractionState.LISTENING; onState(state) }
                override fun onBeginningOfSpeech() { state = InteractionState.LISTENING; onState(state) }
                override fun onRmsChanged(rmsdB: Float) = onLevel(rmsdB)
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() { state = InteractionState.PROCESSING; onState(state); onLevel(0f) }
                override fun onError(error: Int) { state = InteractionState.IDLE; onState(state); onFailure() }
                override fun onResults(results: Bundle?) {
                    state = InteractionState.IDLE
                    onState(state)
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().trim()
                    if (text.isBlank()) onFailure() else onResult(text)
                }
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }
}
