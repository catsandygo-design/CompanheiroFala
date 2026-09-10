package br.com.companheirofala.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import br.com.companheirofala.core.ai.LlamaCppLocalProvider
import br.com.companheirofala.core.conversation.ConversationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Debug-only ADB entry point. It never routes or falls back: it calls llama.cpp directly. */
class AdbLocalLlmTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val pending = goAsync()
        val prompt = intent.getStringExtra(EXTRA_PROMPT)
            ?: "Responda em português do Brasil, em uma frase curta: qual animal faz miau?"
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val provider = LlamaCppLocalProvider(context.applicationContext)
                val startedAt = System.currentTimeMillis()
                if (!provider.loadModel()) {
                    Log.e(TAG, "LOAD_FAILED error=${provider.lastError}")
                    return@launch
                }
                val result = provider.generate(prompt, ConversationState())
                if (!result.invoked) {
                    Log.e(TAG, "GENERATE_FAILED error=${result.error} latency_ms=${result.latencyMs}")
                    return@launch
                }
                Log.i(TAG, "LOAD_OK prompt=$prompt response=${result.text} latency_ms=${System.currentTimeMillis() - startedAt}")
            } catch (error: Throwable) {
                Log.e(TAG, "UNEXPECTED_FAILURE", error)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "br.com.companheirofala.action.TEST_LOCAL_LLAMA"
        const val EXTRA_PROMPT = "prompt"
        private const val TAG = "LocalLlmAdbTest"
    }
}
