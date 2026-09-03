package br.com.companheirofala

import java.text.Normalizer
import java.util.Locale

/**
 * Camada de conversa inteiramente local. Ela não tenta fingir que é uma IA
 * generativa: reconhece assuntos infantis, mantém o último assunto e conduz
 * a criança para uma próxima ação segura e compreensível.
 */
class OfflineConversationBrain(private val profile: ChildProfile) {
    private var lastTopic = Topic.NONE

    fun reply(raw: String): ConversationReply? {
        val text = normalize(raw)
        if (text.isBlank()) return null

        return when {
            matches(text, "obrigada", "obrigado", "valeu") -> answer(
                Topic.FRIENDSHIP,
                "Eu que agradeço por conversar comigo, ${profile.name}! Quer escolher uma brincadeira ou me contar mais uma coisa?",
                RobotMood.HAPPY,
                listOf("BRINCAR", "HISTÓRIA", "CARINHAS")
            )
            matches(text, "eu te amo", "gosto de voce", "gosto da fada", "voce e minha amiga", "voce e meu amigo") -> answer(
                Topic.FRIENDSHIP,
                "Que carinho gostoso, ${profile.name}! Eu gosto de brincar e conversar com você também. O que foi legal no seu dia?",
                RobotMood.HAPPY,
                listOf("CONTAR", "BRINCAR", "HISTÓRIA")
            )
            matches(text, "nao quero", "nao gostei", "que chato", "to chateada", "estou chateada") -> answer(
                Topic.FEELINGS,
                "Tudo bem não gostar de uma coisa. Quer me contar o que deixou isso chato, ou prefere escolher outra brincadeira?",
                RobotMood.CURIOUS,
                listOf("CONTAR", "BRINCAR", "CARINHAS")
            )
            matches(text, "escola", "professora", "professor", "amiguinho", "amiguinha", "coleguinha") -> answer(
                Topic.SCHOOL,
                "Entendi. Na escola aconteceu uma coisa boa, uma coisa difícil ou uma brincadeira que você gostou?",
                RobotMood.CURIOUS,
                listOf("COISA BOA", "COISA DIFÍCIL", "BRINCADEIRA")
            )
            matches(text, "mae", "mamae", "pai", "papai", "vovo", "vovo", "familia") -> answer(
                Topic.FAMILY,
                "Sua família é importante. O que você fez com essa pessoa hoje?",
                RobotMood.CURIOUS,
                listOf("BRINQUEI", "CONVERSEI", "QUERO CONTAR")
            )
            matches(text, "por que", "porque", "como funciona", "o que e") -> localQuestion(text)
            matches(text, "cor", "cores", "vermelho", "azul", "amarelo", "verde") -> colorGame()
            matches(text, "numero", "numeros", "contar", "um dois tres") -> countingGame()
            matches(text, "desenhar", "desenho", "pintar", "pintura") -> answer(
                Topic.CREATIVITY,
                "Vamos imaginar um desenho! Você quer desenhar uma fada, um cavalo ou uma casa cheia de cores?",
                RobotMood.SURPRISED,
                listOf("FADA", "CAVALO", "CASA")
            )
            matches(text, "musica", "musiquinha", "cantar", "canta") -> answer(
                Topic.PLAY,
                "Eu não canto músicas conhecidas, mas podemos inventar uma: bate palminha duas vezes e escolha um animal para nossa música.",
                RobotMood.HAPPY,
                listOf("CAVALO", "GATO", "CACHORRO")
            )
            matches(text, "sim", "aham", "uhum", "ta bom", "legal") -> continueLastTopic()
            matches(text, "nao", "nao sei", "nao quero falar") -> answer(
                Topic.NONE,
                "Tudo bem. Você não precisa contar agora. Podemos brincar, ouvir uma história ou só escolher uma carinha.",
                RobotMood.HAPPY,
                listOf("BRINCAR", "HISTÓRIA", "CARINHAS")
            )
            text.split(" ").size >= 4 -> answer(
                Topic.DAY,
                "Eu estou prestando atenção. Isso parece importante para você. Quer me contar mais uma parte ou escolher uma brincadeira?",
                RobotMood.CURIOUS,
                listOf("CONTAR MAIS", "BRINCAR", "CARINHAS")
            )
            else -> null
        }
    }

    private fun localQuestion(text: String): ConversationReply = when {
        matches(text, "por que chove", "chuva") -> answer(Topic.LEARNING, "A chuva cai das nuvens quando a água do céu fica bem pesadinha. Quer brincar de descobrir o tempo?", RobotMood.CURIOUS, listOf("SOL", "CHUVA", "VENTO"))
        matches(text, "por que dormir", "sono") -> answer(Topic.LEARNING, "Dormir ajuda o corpo e a cabeça a descansarem para brincar e aprender no outro dia.", RobotMood.CURIOUS, listOf("HISTÓRIA", "INÍCIO"))
        matches(text, "por que comer", "comida") -> answer(Topic.LEARNING, "A comida dá energia para o corpo crescer, brincar e pensar. Um adulto pode ajudar a escolher uma comida gostosa.", RobotMood.CURIOUS, listOf("ROTINA", "INÍCIO"))
        else -> answer(Topic.LEARNING, "Boa pergunta! Eu não sei tudo, mas podemos descobrir com um adulto ou brincar de pensar em uma resposta juntas.", RobotMood.CURIOUS, listOf("PENSAR JUNTAS", "BRINCAR", "INÍCIO"))
    }

    private fun colorGame() = answer(
        Topic.LEARNING,
        "Vamos brincar de cores. Procura perto de você uma coisa azul, vermelha ou amarela. Qual cor você encontrou?",
        RobotMood.SURPRISED,
        listOf("AZUL", "VERMELHO", "AMARELO")
    )

    private fun countingGame() = answer(
        Topic.LEARNING,
        "Vamos contar devagar: um, dois, três. Agora você consegue achar três coisinhas perto de você?",
        RobotMood.CURIOUS,
        listOf("CONSEGUI", "ME AJUDA", "DE NOVO")
    )

    private fun continueLastTopic(): ConversationReply = when (lastTopic) {
        Topic.SCHOOL -> answer(Topic.SCHOOL, "Que bom que você quer continuar. Foi uma coisa boa, difícil ou uma brincadeira?", RobotMood.CURIOUS, listOf("COISA BOA", "COISA DIFÍCIL", "BRINCADEIRA"))
        Topic.FAMILY -> answer(Topic.FAMILY, "Que legal. Quer me contar uma coisa que vocês fizeram juntos?", RobotMood.HAPPY, listOf("CONTAR", "BRINCAR", "HISTÓRIA"))
        Topic.LEARNING -> answer(Topic.LEARNING, "Então vamos aprender brincando. Você prefere animais, letras ou cores?", RobotMood.PROUD, listOf("ANIMAIS", "ABC", "CORES"))
        else -> answer(Topic.PLAY, "Ótimo! Escolhe uma aventura: animais, letras ou história.", RobotMood.HAPPY, listOf("ANIMAIS", "ABC", "HISTÓRIA"))
    }

    private fun answer(topic: Topic, text: String, mood: RobotMood, choices: List<String>): ConversationReply {
        lastTopic = topic
        return ConversationReply(text, mood, keepListening = true, choices = choices)
    }

    private fun matches(text: String, vararg phrases: String) = phrases.any { text.contains(normalize(it)) }

    private fun normalize(value: String): String {
        val n = Normalizer.normalize(value.lowercase(Locale.forLanguageTag("pt-BR")), Normalizer.Form.NFD)
        return n.replace("\\p{Mn}+".toRegex(), "").replace("[^a-z0-9 ]".toRegex(), " ").replace("\\s+".toRegex(), " ").trim()
    }

    private enum class Topic { NONE, FRIENDSHIP, FEELINGS, SCHOOL, FAMILY, LEARNING, CREATIVITY, PLAY, DAY }
}
