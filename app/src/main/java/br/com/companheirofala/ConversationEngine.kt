package br.com.companheirofala

import java.util.Locale
import kotlin.random.Random

enum class PlayMode { CHAT, ANIMALS, COLORS, STORY, GUESS }

data class ConversationReply(
    val text: String,
    val mood: RobotMood = RobotMood.HAPPY,
    val keepListening: Boolean = true
)

class ConversationEngine {
    private var childName: String? = null
    private var mode = PlayMode.CHAT
    private var lastTopic: String? = null
    private var storyStep = 0
    private var guessAnswer: String? = null
    private var turns = 0

    fun start(mode: PlayMode): ConversationReply {
        this.mode = mode
        return when (mode) {
            PlayMode.CHAT -> ConversationReply("Vamos conversar. Me conta uma coisa legal que aconteceu hoje.", RobotMood.LISTENING)
            PlayMode.ANIMALS -> ConversationReply("Vamos brincar de animais! Qual animal você mais gosta?", RobotMood.HAPPY)
            PlayMode.COLORS -> ConversationReply("Brincadeira das cores! Olhe ao seu redor e me diga uma coisa que seja azul.", RobotMood.HAPPY)
            PlayMode.STORY -> {
                storyStep = 0
                ConversationReply("Vamos inventar uma história juntos. Um robozinho encontrou uma caixa brilhante. O que tinha dentro dela?", RobotMood.THINKING)
            }
            PlayMode.GUESS -> newGuess()
        }
    }

    fun reply(raw: String): ConversationReply {
        turns++
        val text = normalize(raw)
        if (text.isBlank()) return ConversationReply("Não ouvi direitinho. Fala mais uma vez para mim?", RobotMood.CONFUSED)

        extractName(text)
        val name = childName?.let { ", $it" }.orEmpty()

        if (containsAny(text, "parar", "chega", "não quero brincar", "nao quero brincar")) {
            mode = PlayMode.CHAT
            return ConversationReply("Tudo bem$name. A gente pode só conversar. Sobre o que você quer falar?", RobotMood.LISTENING)
        }

        if (containsAny(text, "meu nome é", "meu nome e", "eu sou")) {
            return ConversationReply("Que bom te conhecer$name! O que você gosta de fazer para se divertir?", RobotMood.HAPPY)
        }

        return when (mode) {
            PlayMode.ANIMALS -> animalReply(text, name)
            PlayMode.COLORS -> colorReply(text, name)
            PlayMode.STORY -> storyReply(text, name)
            PlayMode.GUESS -> guessReply(text, name)
            PlayMode.CHAT -> chatReply(text, name)
        }
    }

    private fun chatReply(text: String, name: String): ConversationReply {
        if (containsAny(text, "oi", "olá", "ola", "bom dia", "boa tarde", "boa noite")) {
            return ConversationReply("Oi$name! Que bom falar com você. Você quer conversar ou brincar de alguma coisa?", RobotMood.HAPPY)
        }
        if (containsAny(text, "triste", "chateado", "chateada", "bravo", "brava", "medo", "assustado", "assustada")) {
            lastTopic = "sentimento"
            return ConversationReply("Entendi$name. Quer me contar o que aconteceu? Eu vou te ouvir.", RobotMood.LISTENING)
        }
        if (containsAny(text, "feliz", "legal", "ótimo", "otimo", "divertido", "divertida", "ganhei", "consegui")) {
            lastTopic = "feliz"
            return ConversationReply("Que legal$name! Qual foi a melhor parte?", RobotMood.HAPPY)
        }
        if (containsAny(text, "escola", "aula", "professor", "professora", "colega", "amigo", "amiga")) {
            lastTopic = "escola"
            return ConversationReply("Quero saber mais. O que aconteceu na escola hoje?", RobotMood.LISTENING)
        }
        if (containsAny(text, "cachorro", "gato", "dinossauro", "unicórnio", "unicornio", "animal")) {
            lastTopic = "animal"
            return ConversationReply("Eu gosto desse assunto! O que você mais gosta nesse animal?", RobotMood.HAPPY)
        }
        if (containsAny(text, "história", "historia")) {
            mode = PlayMode.STORY
            return start(PlayMode.STORY)
        }
        if (containsAny(text, "adivinha", "adivinhar")) {
            mode = PlayMode.GUESS
            return start(PlayMode.GUESS)
        }
        if (containsAny(text, "cor", "azul", "verde", "vermelho", "amarelo", "rosa", "roxo")) {
            lastTopic = "cor"
            return ConversationReply("Bonita escolha! Qual outra coisa tem essa cor?", RobotMood.HAPPY)
        }
        if (containsAny(text, "não sei", "nao sei")) {
            return ConversationReply("Tudo bem. Escolhe uma: animais, cores, história ou adivinhação?", RobotMood.THINKING)
        }

        return when (lastTopic) {
            "sentimento" -> ConversationReply("Obrigado por me contar$name. O que ajudaria você a se sentir um pouquinho melhor agora?", RobotMood.LISTENING)
            "feliz" -> ConversationReply("Gostei de ouvir isso$name. Você quer contar para alguém essa coisa boa?", RobotMood.HAPPY)
            "escola" -> ConversationReply("E teve alguma parte fácil ou alguma parte difícil?", RobotMood.THINKING)
            "animal" -> ConversationReply("Se esse animal pudesse falar, o que você acha que ele diria para você?", RobotMood.HAPPY)
            "cor" -> ConversationReply("Agora procure uma coisa de outra cor e me diga qual encontrou!", RobotMood.HAPPY)
            else -> {
                val short = text.take(60)
                val options = listOf(
                    "Entendi$name. E o que aconteceu depois?",
                    "Que interessante$name. Me conta mais um pedacinho disso.",
                    "Eu estou acompanhando. Como você se sentiu quando isso aconteceu?",
                    "Gostei de ouvir você. O que foi mais importante nessa história?",
                    "Você me contou: $short. E depois, como terminou?"
                )
                ConversationReply(options.random(), RobotMood.LISTENING)
            }
        }
    }

    private fun animalReply(text: String, name: String): ConversationReply {
        lastTopic = "animal"
        return if (containsAny(text, "cachorro", "gato", "leão", "leao", "elefante", "dinossauro", "unicórnio", "unicornio", "tigre", "macaco", "girafa", "coelho")) {
            val animal = text.split(" ").firstOrNull { it in setOf("cachorro","gato","leão","leao","elefante","dinossauro","unicórnio","unicornio","tigre","macaco","girafa","coelho") } ?: "esse animal"
            ConversationReply("Boa$name! $animal é uma escolha divertida. Que som você acha que ele faz?", RobotMood.HAPPY)
        } else {
            ConversationReply("Gostei da sua resposta$name! Agora me diz: esse animal é grande ou pequeno?", RobotMood.LISTENING)
        }
    }

    private fun colorReply(text: String, name: String): ConversationReply {
        lastTopic = "cor"
        val colors = listOf("azul", "verde", "vermelho", "amarelo", "rosa", "roxo", "laranja", "preto", "branco")
        val found = colors.firstOrNull { text.contains(it) }
        return if (found != null) {
            ConversationReply("Muito bem$name! Você encontrou $found. Agora procure alguma coisa verde e me conte o que é.", RobotMood.HAPPY)
        } else {
            ConversationReply("Legal$name! E qual é a cor dessa coisa?", RobotMood.LISTENING)
        }
    }

    private fun storyReply(text: String, name: String): ConversationReply {
        storyStep++
        val short = text.take(55)
        return when (storyStep % 4) {
            1 -> ConversationReply("Uau$name! Então tinha $short. E de repente apareceu um barulho. Que barulho era?", RobotMood.THINKING)
            2 -> ConversationReply("Gostei! O robozinho ficou curioso. Quem apareceu depois desse barulho?", RobotMood.HAPPY)
            3 -> ConversationReply("Agora ficou emocionante! E o que eles fizeram juntos?", RobotMood.THINKING)
            else -> ConversationReply("Que final legal$name! Quer continuar essa história ou começar outra?", RobotMood.HAPPY)
        }
    }

    private fun newGuess(): ConversationReply {
        val options = listOf(
            "cachorro" to "Eu tenho quatro patas, gosto de brincar e posso fazer au-au. Quem sou eu?",
            "gato" to "Eu tenho bigodes, gosto de subir e faço miau. Quem sou eu?",
            "banana" to "Sou uma fruta amarela e tenho casca. Quem sou eu?",
            "sol" to "Apareço no céu de dia e ilumino tudo. Quem sou eu?"
        )
        val choice = options[Random.nextInt(options.size)]
        guessAnswer = choice.first
        return ConversationReply(choice.second, RobotMood.THINKING)
    }

    private fun guessReply(text: String, name: String): ConversationReply {
        val answer = guessAnswer
        if (answer != null && text.contains(answer)) {
            val next = newGuess()
            return ConversationReply("Acertou$name! Muito bem! Agora outra: ${next.text}", RobotMood.HAPPY)
        }
        return ConversationReply("Quase$name! Quer uma dica? Pense com calma e tenta mais uma vez.", RobotMood.THINKING)
    }

    private fun normalize(input: String): String = input
        .lowercase(Locale("pt", "BR"))
        .trim()
        .replace("municornio", "unicórnio")
        .replace("municórnio", "unicórnio")

    private fun extractName(text: String) {
        val patterns = listOf("meu nome é ", "meu nome e ", "eu sou ")
        for (pattern in patterns) {
            val index = text.indexOf(pattern)
            if (index >= 0) {
                val candidate = text.substring(index + pattern.length)
                    .split(" ")
                    .take(2)
                    .joinToString(" ")
                    .trim()
                if (candidate.length in 2..30) childName = candidate.replaceFirstChar { it.uppercase() }
                return
            }
        }
    }

    private fun containsAny(text: String, vararg terms: String): Boolean = terms.any { text.contains(it) }
}
