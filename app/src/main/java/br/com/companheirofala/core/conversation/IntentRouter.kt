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
        // Palavras como "cavalo", "unicórnio" ou "cor" fazem parte da conversa normal.
        // Só ativamos o fluxo determinístico quando a criança pede claramente uma brincadeira.
        has(text, "quero brincar", "vamos brincar", "quero jogar", "vamos jogar", "adivinha", "jogo da memoria", "jogo de memória") -> IntentType.GAME
        has(text, "brincar de animais", "jogo de animal") -> IntentType.ANIMAL
        has(text, "brincar com letras", "jogo das letras") -> IntentType.LETTER_GAME
        has(text, "brincar com cores", "jogo das cores") -> IntentType.COLOR_GAME
        has(text, "conta uma historia", "conte uma história", "quero uma historinha") -> IntentType.STORY
        // Toda fala que não é uma rotina, jogo explícito ou situação de segurança vai ao LLM.
        else -> IntentType.GENERAL_CHAT
    }
    private fun has(text: String, vararg terms: String) = terms.any(text::contains)
}
