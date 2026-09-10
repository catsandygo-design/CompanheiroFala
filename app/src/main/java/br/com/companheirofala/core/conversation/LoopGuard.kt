package br.com.companheirofala.core.conversation

class LoopGuard {
    private val replies = ArrayDeque<String>()
    fun record(text: String): Boolean {
        replies.addLast(text.trim().lowercase()); while (replies.size > 3) replies.removeFirst()
        return replies.size == 3 && replies.distinct().size == 1
    }
    fun reset() = replies.clear()
}

class FallbackHandler {
    fun reply(state: ConversationState): String = when {
        state.safetyState.category != null -> "Pode responder bem devagar. Eu estou aqui com você."
        state.gameState.activeGame != null -> "Quer tentar outra vez?"
        state.pendingQuestion != PendingQuestion.NONE -> "Pode me responder mais uma vez?"
        state.currentTopic == "animal" -> "Você está falando do cavalo?"
        else -> "Não entendi. Pode falar de novo?"
    }
}
