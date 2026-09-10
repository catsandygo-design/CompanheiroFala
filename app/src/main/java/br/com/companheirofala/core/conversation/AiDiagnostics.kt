package br.com.companheirofala.core.conversation

import android.util.Log

data class AiDiagnosticTurn(
    val sttText: String, val normalizedText: String, var route: String = "", var pending: String = "",
    var localAvailable: Boolean = false, var localLoaded: Boolean = false, var modelName: String = "",
    var llmCalled: Boolean = false, var prompt: String = "", var llmResponse: String = "", var latencyMs: Long = 0,
    var finalResponse: String = "", var fallbackUsed: Boolean = false, var error: String = ""
)

class AiDiagnostics {
    private var last: AiDiagnosticTurn? = null
    private var localCalls = 0; private var fallbacks = 0
    fun begin(raw: String, normalized: String, pending: PendingQuestion, available: Boolean, loaded: Boolean, model: String) = AiDiagnosticTurn(raw, normalized, pending = pending.name, localAvailable = available, localLoaded = loaded, modelName = model).also { last = it }
    fun complete(turn: AiDiagnosticTurn, reply: String) { turn.finalResponse = reply; if (turn.fallbackUsed) fallbacks++; if (turn.route == "LOCAL_LLM" && turn.llmCalled) localCalls++; Log.d("CompanionAI", format(turn)) }
    fun text(): String = last?.let { format(it) + "\n\nLOCAL_LLM_CALLS=$localCalls\nFALLBACK_CALLS=$fallbacks" } ?: "Ainda não há falas diagnosticadas."
    private fun format(t: AiDiagnosticTurn) = "STT_TEXT=${t.sttText}\nNORMALIZED_TEXT=${t.normalizedText}\nROUTE_SELECTED=${t.route}\nPENDING_CONTEXT=${t.pending}\nLOCAL_MODEL_AVAILABLE=${t.localAvailable}\nLOCAL_MODEL_LOADED=${t.localLoaded}\nLOCAL_MODEL_NAME=${t.modelName}\nLLM_CALLED=${t.llmCalled}\nLLM_PROMPT=${t.prompt}\nLLM_RESPONSE=${t.llmResponse}\nLLM_LATENCY_MS=${t.latencyMs}\nFINAL_RESPONSE=${t.finalResponse}\nFALLBACK_USED=${t.fallbackUsed}\nERROR=${t.error}"
}
