package br.com.companheirofala

import java.util.Locale

enum class PlayMode { CHAT, LETTERS, ROUTINE }

data class ConversationReply(
    val text: String,
    val mood: RobotMood = RobotMood.HAPPY,
    val keepListening: Boolean = true,
    val scene: VisualScene = VisualScene.NONE,
    val choices: List<String> = emptyList(),
    val waitForMovement: Boolean = false
)

class ConversationEngine {
    private var childName = "Gabi"
    private var mode = PlayMode.CHAT
    private var routineStep = ""
    private var letterAnswer = "A"

    fun start(mode: PlayMode): ConversationReply {
        this.mode = mode
        return when (mode) {
            PlayMode.CHAT -> ConversationReply("Oi, Gabi! Quer brincar comigo?", choices = listOf("SIM", "NÃO"))
            PlayMode.LETTERS -> startLetterGame()
            PlayMode.ROUTINE -> ConversationReply("Opa, Gabi! Você já comeu?", choices = listOf("SIM", "NÃO"))
        }
    }

    fun reply(raw: String): ConversationReply {
        val text = normalize(raw)
        if (text.contains("meu nome")) extractName(text)
        if (mode == PlayMode.LETTERS) return letterReply(text)
        if (mode == PlayMode.ROUTINE) return routineReply(text)

        if (containsAny(text, "letra", "abc", "aprender")) return start(PlayMode.LETTERS)
        if (containsAny(text, "comi", "almocei", "jantei", "lanchei")) {
            mode = PlayMode.ROUTINE
            routineStep = "ate"
            return ConversationReply("Opa, $childName, você já comeu?", choices = listOf("SIM", "NÃO"))
        }
        if (containsAny(text, "água", "agua", "sede")) {
            routineStep = "agua"
            return ConversationReply("Opa, $childName! Você quer água?", scene = VisualScene.WATER, choices = listOf("SIM", "NÃO"))
        }
        if (containsAny(text, "xixi", "banheiro")) {
            routineStep = "xixi"
            return ConversationReply("Quer ir fazer xixi?", scene = VisualScene.TOILET, choices = listOf("SIM", "NÃO"))
        }
        if (containsAny(text, "cavalo")) {
            return ConversationReply("Olha o cavalo! Agora aperta na letra A.", scene = VisualScene.HORSE, choices = listOf("A", "B", "C"))
        }
        if (isYes(text)) return ConversationReply("Que legal! Então vamos brincar. Você quer letras ou animais?", choices = listOf("ABC", "ANIMAIS"))
        return ConversationReply("Entendi, $childName. Quer brincar de letras comigo?", choices = listOf("SIM", "NÃO"))
    }

    fun onChoice(choice: String): ConversationReply {
        val c = choice.uppercase(Locale("pt", "BR"))
        if (c == "ABC") return start(PlayMode.LETTERS)
        if (c == "ANIMAIS") {
            mode = PlayMode.LETTERS
            letterAnswer = "A"
            return ConversationReply("Que animal é esse? Cavalo! Muito bem. Agora aperta na letra A.", scene = VisualScene.HORSE, choices = listOf("A","B","C"))
        }
        if (mode == PlayMode.LETTERS && c in listOf("A","B","C")) return letterChoice(c)
        return reply(c)
    }

    private fun startLetterGame(): ConversationReply {
        mode = PlayMode.LETTERS
        letterAnswer = "A"
        return ConversationReply("Gabi, olha o cavalo. Cavalo começa com qual letra? Aperta no A.", scene = VisualScene.HORSE, choices = listOf("A", "B", "C"))
    }

    private fun letterReply(text: String): ConversationReply {
        val spoken = listOf("a","b","c").firstOrNull { text == it || text.contains("letra $it") }
        return if (spoken != null) letterChoice(spoken.uppercase()) else ConversationReply("Olha as letras. Aperta no A.", scene = VisualScene.HORSE, choices = listOf("A","B","C"))
    }

    private fun letterChoice(choice: String): ConversationReply {
        return if (choice == letterAnswer) {
            letterAnswer = "B"
            ConversationReply("Acertou! A de amor. Muito bem! Agora aperta no B.", RobotMood.HAPPY, scene = VisualScene.LETTER_A, choices = listOf("A","B","C"))
        } else if (letterAnswer == "B" && choice == "B") {
            letterAnswer = "C"
            ConversationReply("Isso! B de bola. Agora vamos no C.", RobotMood.HAPPY, scene = VisualScene.LETTER_B, choices = listOf("A","B","C"))
        } else if (letterAnswer == "C" && choice == "C") {
            letterAnswer = "A"
            ConversationReply("Muito bem! C de cavalo! Quer brincar de novo?", RobotMood.HAPPY, scene = VisualScene.HORSE, choices = listOf("SIM","NÃO"))
        } else {
            ConversationReply("Quase! Olha com calma. Aperta no $letterAnswer.", RobotMood.THINKING, scene = VisualScene.HORSE, choices = listOf("A","B","C"))
        }
    }

    private fun routineReply(text: String): ConversationReply {
        return when (routineStep) {
            "ate" -> if (isYes(text)) {
                routineStep = "escovar"
                ConversationReply("Então é hora de escovar os dentinhos!", scene = VisualScene.TOOTHBRUSH, choices = listOf("JÁ ESCOVEI", "VOU ESCOVAR"))
            } else ConversationReply("Tudo bem. Quando comer, a gente lembra dos dentinhos.", choices = listOf("OK"))
            "agua" -> if (isYes(text)) ConversationReply("Então pede para a Lety ou para a Alice te dar água.", scene = VisualScene.WATER) else ConversationReply("Tudo bem. Se der sede, me chama.")
            "xixi" -> if (isYes(text)) {
                routineStep = "voltando_xixi"
                ConversationReply("Então deixa o celular na mesa e vai fazer xixi. Eu fico aqui esperando você voltar.", scene = VisualScene.TOILET, keepListening = false, waitForMovement = true)
            } else ConversationReply("Tudo bem. Quando der vontade, não precisa segurar.")
            "maos" -> if (isYes(text)) ConversationReply("Boa! Mãozinha limpa. Agora vamos brincar!", scene = VisualScene.HANDS, choices = listOf("ABC","ANIMAIS"))
            else -> ConversationReply("Vamos continuar brincando!", choices = listOf("ABC","ANIMAIS"))
        }
    }

    fun onMovementDetected(): ConversationReply {
        routineStep = "maos"
        return ConversationReply("Opa, Gabi! Você voltou. Lavou a mãozinha?", scene = VisualScene.HANDS, choices = listOf("SIM", "NÃO"))
    }

    fun onHandsNotWashed(): ConversationReply {
        routineStep = "maos"
        return ConversationReply("Então precisa lavar as mãozinhas. Senão as bactérias podem fazer dodói. Olha elas correndo!", scene = VisualScene.BACTERIA, choices = listOf("VOU LAVAR"))
    }

    private fun normalize(input: String) = input.lowercase(Locale("pt", "BR")).trim()
    private fun isYes(text: String) = containsAny(text, "sim", "já", "ja", "quero", "uhum")
    private fun containsAny(text: String, vararg terms: String) = terms.any { text.contains(it) }
    private fun extractName(text: String) {
        val p = listOf("meu nome é ", "meu nome e ").firstOrNull { text.contains(it) } ?: return
        val value = text.substringAfter(p).split(" ").firstOrNull().orEmpty()
        if (value.length >= 2) childName = value.replaceFirstChar { it.uppercase() }
    }
}