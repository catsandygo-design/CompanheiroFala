package br.com.companheirofala

import java.util.Locale

enum class PlayMode { HOME, FEELINGS, LETTERS, ROUTINE }

data class ConversationReply(
    val text: String,
    val mood: RobotMood = RobotMood.HAPPY,
    val keepListening: Boolean = false,
    val scene: VisualScene = VisualScene.NONE,
    val choices: List<String> = emptyList(),
    val waitForMovement: Boolean = false,
    val parentAlert: String? = null
)

class ConversationEngine {
    private val childName = "Gabi"
    private var mode = PlayMode.HOME
    private var routineStep = ""

    fun start(mode: PlayMode = PlayMode.HOME): ConversationReply {
        this.mode = mode
        return when (mode) {
            PlayMode.HOME -> ConversationReply(
                "Oi, Gabi! Eu estava esperando você. Como você está se sentindo hoje?",
                scene = VisualScene.FEELINGS,
                choices = listOf("FELIZ", "TRISTE", "BRAVA")
            )
            PlayMode.FEELINGS -> ConversationReply(
                "Como você está se sentindo? Escolhe uma carinha.",
                scene = VisualScene.FEELINGS,
                choices = listOf("FELIZ", "TRISTE", "BRAVA")
            )
            PlayMode.LETTERS -> letterQuestion()
            PlayMode.ROUTINE -> ConversationReply(
                "Gabi, você já comeu?",
                scene = VisualScene.FOOD,
                choices = listOf("SIM", "NÃO")
            )
        }
    }

    fun reply(raw: String): ConversationReply {
        val text = normalize(raw)
        if (containsAny(text, "triste", "chateada")) return feeling("TRISTE")
        if (containsAny(text, "feliz", "legal", "bem")) return feeling("FELIZ")
        if (containsAny(text, "brava", "raiva")) return feeling("BRAVA")
        if (containsAny(text, "letra", "abc", "cavalo")) return start(PlayMode.LETTERS)
        if (containsAny(text, "comi", "comer", "dente")) {
            mode = PlayMode.ROUTINE
            routineStep = "comida"
            return start(PlayMode.ROUTINE)
        }
        if (containsAny(text, "água", "agua", "sede")) {
            mode = PlayMode.ROUTINE
            routineStep = "agua"
            return ConversationReply("Você quer água?", scene = VisualScene.WATER, choices = listOf("SIM", "NÃO"))
        }
        if (containsAny(text, "xixi", "banheiro")) {
            mode = PlayMode.ROUTINE
            routineStep = "xixi"
            return ConversationReply("Quer ir fazer xixi?", scene = VisualScene.TOILET, choices = listOf("SIM", "NÃO"))
        }
        return ConversationReply(
            "Eu ouvi você, $childName. O que você quer fazer agora?",
            choices = listOf("CARINHAS", "ABC", "ROTINA")
        )
    }

    fun onChoice(choice: String): ConversationReply {
        val c = choice.uppercase(Locale.forLanguageTag("pt-BR"))
        if (c in listOf("FELIZ", "TRISTE", "BRAVA")) return feeling(c)
        if (c == "CARINHAS") return start(PlayMode.FEELINGS)
        if (c == "ABC" || c == "DE NOVO") return start(PlayMode.LETTERS)
        if (c == "ROTINA") {
            routineStep = "comida"
            return start(PlayMode.ROUTINE)
        }
        if (c == "BRINCAR") {
            mode = PlayMode.HOME
            return ConversationReply("Escolhe uma brincadeira!", choices = listOf("CARINHAS", "ABC", "ROTINA"))
        }
        if (mode == PlayMode.LETTERS && c in listOf("A", "C", "P")) return letterChoice(c)
        if (mode == PlayMode.ROUTINE) return routineChoice(c)
        return reply(c)
    }

    private fun feeling(value: String): ConversationReply = when (value) {
        "FELIZ" -> ConversationReply(
            text = "Que bom! Eu fico feliz com você. Quer brincar?",
            mood = RobotMood.HAPPY,
            scene = VisualScene.HAPPY_FACE,
            choices = listOf("BRINCAR", "ABC")
        )
        "TRISTE" -> ConversationReply(
            text = "Entendi, Gabi. Você está triste. Quer me contar o que aconteceu?",
            mood = RobotMood.CONFUSED,
            scene = VisualScene.SAD_FACE,
            choices = listOf("CONVERSAR", "BRINCAR"),
            parentAlert = "Gabi marcou que está triste. Vale conversar com ela para entender o que aconteceu."
        )
        else -> ConversationReply(
            text = "Entendi. Você está brava. Vamos respirar devagar comigo e depois você pode me contar.",
            mood = RobotMood.THINKING,
            scene = VisualScene.ANGRY_FACE,
            choices = listOf("CONVERSAR", "BRINCAR"),
            parentAlert = "Gabi marcou que está brava. Vale verificar como ela está e o que aconteceu."
        )
    }

    private fun letterQuestion() = ConversationReply(
        "Olha, Gabi: esse é um cavalo. Caa-va-lo. Com qual letra começa cavalo?",
        scene = VisualScene.HORSE,
        choices = listOf("A", "C", "P")
    )

    private fun letterChoice(choice: String): ConversationReply {
        return if (choice == "C") {
            ConversationReply(
                "Isso! C de cavalo! Muito bem!",
                mood = RobotMood.HAPPY,
                scene = VisualScene.HORSE,
                choices = listOf("DE NOVO", "BRINCAR")
            )
        } else {
            ConversationReply(
                "Quase! Cavalo começa com C. C de cavalo. Tenta tocar no C.",
                mood = RobotMood.THINKING,
                scene = VisualScene.HORSE,
                choices = listOf("A", "C", "P")
            )
        }
    }

    private fun routineChoice(c: String): ConversationReply = when (routineStep) {
        "comida" -> if (isYes(c)) {
            routineStep = "escovar"
            ConversationReply(
                "Depois de comer, vamos cuidar dos dentinhos. Hora de escovar!",
                scene = VisualScene.TOOTHBRUSH,
                choices = listOf("JÁ ESCOVEI", "VOU ESCOVAR")
            )
        } else {
            ConversationReply("Tudo bem. Quando você comer, eu te lembro de cuidar dos dentinhos.", choices = listOf("BRINCAR"))
        }
        "escovar" -> ConversationReply("Muito bem por cuidar dos dentinhos!", scene = VisualScene.TOOTHBRUSH, choices = listOf("BRINCAR", "ABC"))
        "agua" -> if (isYes(c)) {
            ConversationReply("Então pede para a Lety ou para a Alice te dar água, combinado?", scene = VisualScene.WATER, choices = listOf("BRINCAR"))
        } else {
            ConversationReply("Tudo bem. Quando sentir sede, me chama.", choices = listOf("BRINCAR"))
        }
        "xixi" -> if (isYes(c)) {
            routineStep = "voltando"
            ConversationReply(
                "Pode deixar o celular na mesa e ir fazer xixi. Eu espero você voltar.",
                scene = VisualScene.TOILET,
                waitForMovement = true
            )
        } else {
            ConversationReply("Tudo bem. Quando der vontade, pode me avisar.", choices = listOf("BRINCAR"))
        }
        "maos" -> if (isYes(c)) {
            ConversationReply("Muito bem! Mãozinha limpa. Agora podemos brincar!", scene = VisualScene.HANDS, choices = listOf("ABC", "BRINCAR"))
        } else {
            onHandsNotWashed()
        }
        else -> ConversationReply("Vamos brincar?", choices = listOf("CARINHAS", "ABC", "ROTINA"))
    }

    fun onMovementDetected(): ConversationReply {
        routineStep = "maos"
        return ConversationReply(
            "Você voltou! Lavou as mãozinhas com água e sabão?",
            scene = VisualScene.HANDS,
            choices = listOf("SIM", "NÃO")
        )
    }

    fun onHandsNotWashed(): ConversationReply = ConversationReply(
        "Vamos lavar. As bactérias são pequenininhas e podem fazer dodói. Água e sabão mandam elas embora!",
        scene = VisualScene.BACTERIA,
        choices = listOf("VOU LAVAR", "BRINCAR")
    )

    private fun normalize(input: String) = input.lowercase(Locale.forLanguageTag("pt-BR")).trim()
    private fun isYes(text: String) = containsAny(text.lowercase(), "sim", "já", "ja", "quero", "ok", "vou")
    private fun containsAny(text: String, vararg terms: String) = terms.any { text.contains(it) }
}
