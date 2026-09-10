package br.com.companheirofala.core.conversation

import android.content.Context

/** Persiste somente metadados mínimos da sessão; a janela de falas fica em memória. */
class ConversationStateRepository(context: Context) {
    private val prefs = context.getSharedPreferences("conversation_state", Context.MODE_PRIVATE)
    fun save(state: ConversationState) = prefs.edit()
        .putString("environment", state.currentEnvironment.name)
        .putString("topic", state.currentTopic)
        .putLong("last_interaction", state.lastInteractionAt)
        .apply()
    fun clear() = prefs.edit().clear().apply()
}

class ConversationStateManager(private val repository: ConversationStateRepository, private val timeoutMs: Long = 5 * 60_000L) {
    var state = ConversationState(); private set
    fun expireIfNeeded(now: Long = System.currentTimeMillis()) {
        if (now - state.lastInteractionAt > timeoutMs) state = ConversationState(currentEnvironment = state.currentEnvironment)
    }
    fun update(transform: (ConversationState) -> ConversationState) { state = transform(state).copy(lastInteractionAt = System.currentTimeMillis()); repository.save(state) }
    fun addMessage(message: ConversationMessage) = update { it.copy(recentMessages = (it.recentMessages + message).takeLast(8), lastUserUtterance = if (message.fromChild) message.text else it.lastUserUtterance, lastAssistantResponse = if (!message.fromChild) message.text else it.lastAssistantResponse) }
    fun clearSuspectContext() { state = ConversationState(currentEnvironment = state.currentEnvironment); repository.save(state) }
}
