package br.com.companheirofala

import java.util.Locale
import kotlin.random.Random

data class ConversationReply(
    val text: String,
    val mood: RobotMood = RobotMood.HAPPY
)

class ConversationEngine {
    private var lastTopic: String? = null
    private var childName: String? = null
    private var turnCount = 0

    fun reply(raw: String): ConversationReply {
        turnCount++
        val text = normalize(raw)
        if (text.isBlank()) return ConversationReply("Não consegui ouvir direitinho. Pode falar de novo para mim?", RobotMood.CONFUSED)

        extractName(text)
        val name = childName?.let { ", $it" }.orEmpty()

        if (containsAny(text, "oi", "olá", "ola", "bom dia", "boa tarde", "boa noite")) {
            lastTopic = "saudacao"
            return ConversationReply("Oi$name! Eu gostei de ouvir você. Como foi o seu dia?", RobotMood.HAPPY)
        }

        if (containsAny(text, "triste", "chateado", "chateada", "bravo", "brava", "medo", "assustado", "assustada")) {
            lastTopic = "sentimento"
            return ConversationReply("Entendi$name. Quer me contar o que aconteceu? Eu estou ouvindo.", RobotMood.LISTENING)
        }

        if (containsAny(text, "feliz", "legal", "ótimo", "otimo", "bom", "divertido", "divertida")) {
            lastTopic = "sentimento"
            return ConversationReply("Que bom$name! O que deixou você mais feliz?", RobotMood.HAPPY)
        }

        if (containsAny(text, "dinossauro", "cachorro", "gato", "unicórnio", "unicornio", "animal")) {
            lastTopic = "animal"
            return ConversationReply("Eu também acho animais muito interessantes. Qual deles você mais gosta e por quê?", RobotMood.HAPPY)
        }

        if (containsAny(text, "azul", "vermelho", "verde", "amarelo", "rosa", "roxo", "cor")) {
            lastTopic = "cor"
            return ConversationReply("Essa cor é bonita! Se você pudesse pintar um robô inteiro, que cores colocaria nele?", RobotMood.HAPPY)
        }

        if (containsAny(text, "escola", "professora", "professor", "aula", "colega", "amigo", "amiga")) {
            lastTopic = "escola"
            return ConversationReply("Quero saber mais. Qual foi a parte mais interessante disso hoje?", RobotMood.LISTENING)
        }

        if (containsAny(text, "brincar", "jogar", "jogo", "brinquedo", "desenho", "filme")) {
            lastTopic = "brincadeira"
            return ConversationReply("Parece divertido! Como funciona? Me explica como se eu nunca tivesse brincado disso.", RobotMood.HAPPY)
        }

        if (containsAny(text, "piada", "engraçado", "engracado")) {
            lastTopic = "piada"
            return ConversationReply("Tenho uma: por que o robô foi à escola? Porque queria melhorar a memória! Agora conta uma para mim.", RobotMood.HAPPY)
        }

        if (containsAny(text, "história", "historia")) {
            lastTopic = "historia"
            return ConversationReply("Vamos inventar uma juntos. Era uma vez um robozinho que encontrou uma porta brilhante. O que tinha atrás dela?", RobotMood.THINKING)
        }

        if (containsAny(text, "não sei", "nao sei")) {
            return ConversationReply("Tudo bem. Vamos pensar juntos. Você prefere falar de animais, brincadeiras, cores ou inventar uma história?", RobotMood.THINKING)
        }

        return contextualFallback(text, name)
    }

    private fun contextualFallback(text: String, name: String): ConversationReply {
        val short = text.take(70)
        return when (lastTopic) {
            "sentimento" -> ConversationReply("Obrigado por me contar$name. E como você se sente agora?", RobotMood.LISTENING)
            "animal" -> ConversationReply("Gostei da sua resposta. O que esse animal faria se fosse seu companheiro por um dia?", RobotMood.HAPPY)
            "cor" -> ConversationReply("Boa escolha! E qual seria o nome desse robô colorido?", RobotMood.HAPPY)
            "escola" -> ConversationReply("Entendi. E teve alguma coisa que foi difícil ou muito fácil para você?", RobotMood.THINKING)
            "brincadeira" -> ConversationReply("Agora eu entendi melhor. E quem costuma brincar com você?", RobotMood.HAPPY)
            "historia" -> ConversationReply("Gostei! Então aconteceu: $short. O que aconteceu logo depois?", RobotMood.THINKING)
            else -> {
                val options = listOf(
                    "Entendi$name. Você disse: $short. O que aconteceu depois?",
                    "Que interessante$name. Me conta mais uma coisa sobre isso.",
                    "Estou acompanhando$name. Qual é a parte mais importante disso para você?",
                    "Gostei de ouvir isso$name. Se você pudesse mudar uma coisa nessa história, o que mudaria?"
                )
                ConversationReply(options[Random.nextInt(options.size)], RobotMood.LISTENING)
            }
        }
    }

    private fun normalize(input: String): String {
        val lower = input.lowercase(Locale("pt", "BR")).trim()
        return lower
            .replace("municornio", "unicórnio")
            .replace("municórnio", "unicórnio")
    }

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
