package br.com.companheirofala.core.conversation

class IntentRouter {
    fun route(text: String, state: ConversationState): IntentType = when {
        state.pendingQuestion != PendingQuestion.NONE -> IntentType.UNKNOWN
        has(text, "quero fazer xixi", "fazer xixi", "quero xixi", "xixi", "banheiro") -> IntentType.TOILET
        has(text, "quero agua", "beber agua", "agua", "sede") -> IntentType.WATER
        has(text, "fome", "quero comer", "comida", "almoco", "janta") -> IntentType.FOOD
        has(text, "dormir", "sono", "cansada") -> IntentType.SLEEP
        has(text, "escovar", "dente", "lavar a mao", "lavar mao") -> IntentType.HYGIENE
        has(text, "triste", "feliz", "brava", "raiva", "medo", "assustada") -> IntentType.EMOTION
        has(text, "animal", "cavalo", "gato", "cachorro", "unicórnio") -> IntentType.ANIMAL
        has(text, "letra", "abc", "alfabeto") -> IntentType.LETTER_GAME
        has(text, "cor", "cores") -> IntentType.COLOR_GAME
        has(text, "historia", "historinha") -> IntentType.STORY
        has(text, "brincar", "jogo", "adivinha", "memoria") -> IntentType.GAME
        text.split(" ").size > 1 -> IntentType.GENERAL_CHAT
        else -> IntentType.UNKNOWN
    }
    private fun has(text: String, vararg terms: String) = terms.any(text::contains)
}
