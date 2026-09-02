package br.com.companheirofala

import java.util.Locale
import kotlin.random.Random

enum class PlayMode { HOME, FEELINGS, LETTERS, ROUTINE }
private enum class EmotionalStage { NONE, WAITING_REASON, WAITING_INJURY }

data class ConversationReply(
    val text: String,
    val mood: RobotMood = RobotMood.HAPPY,
    val keepListening: Boolean = false,
    val scene: VisualScene = VisualScene.NONE,
    val choices: List<String> = emptyList(),
    val waitForMovement: Boolean = false,
    val parentAlert: String? = null
)

class ConversationEngine(private val profile: ChildProfile = ChildProfile.gabi()) {
    private var mode = PlayMode.HOME
    private var routineStep = ""
    private var lastPrompt = ""
    private var emotionalStage = EmotionalStage.NONE
    private var currentEmotion: String? = null
    private var currentEmotionScene = VisualScene.FEELINGS

    fun start(mode: PlayMode = PlayMode.HOME): ConversationReply {
        this.mode = mode
        emotionalStage = EmotionalStage.NONE
        return when (mode) {
            PlayMode.HOME, PlayMode.FEELINGS -> feelingsQuestion()
            PlayMode.LETTERS -> letterQuestion()
            PlayMode.ROUTINE -> ConversationReply(
                "${profile.name}, você já comeu?",
                mood = RobotMood.CURIOUS,
                scene = VisualScene.FOOD,
                choices = listOf("SIM", "NÃO")
            )
        }
    }

    fun reply(raw: String): ConversationReply {
        val text = normalize(raw)
        if (text.isBlank()) return repeatRequest()

        if (emotionalStage != EmotionalStage.NONE) return emotionalFollowUp(text)

        return when {
            containsAny(text, "triste", "chateada") -> feeling("TRISTE")
            containsAny(text, "feliz", "bem", "contente") -> feeling("FELIZ")
            containsAny(text, "brava", "raiva", "nervosa") -> feeling("BRAVA")
            containsAny(text, "medo", "assustada") -> feeling("MEDO")
            containsAny(text, "cansada", "sono") -> feeling("CANSADA")
            containsAny(text, "animada", "empolgada") -> feeling("ANIMADA")
            containsAny(text, "letra", "abc", "cavalo") -> start(PlayMode.LETTERS)
            containsAny(text, "comi", "comer", "fome", "dente") -> {
                mode = PlayMode.ROUTINE; routineStep = "comida"; start(PlayMode.ROUTINE)
            }
            containsAny(text, "água", "agua", "sede") -> {
                mode = PlayMode.ROUTINE; routineStep = "agua"
                ConversationReply("${profile.name}, você quer água?", RobotMood.CURIOUS, scene = VisualScene.WATER, choices = listOf("SIM", "NÃO"))
            }
            containsAny(text, "xixi", "banheiro") -> {
                mode = PlayMode.ROUTINE; routineStep = "xixi"
                ConversationReply("Quer ir fazer xixi?", RobotMood.CURIOUS, scene = VisualScene.TOILET, choices = listOf("SIM", "NÃO"))
            }
            else -> ConversationReply(
                varied("Eu ouvi você. Quer me contar mais?", "Entendi. Continua, eu estou ouvindo.", "Tá bom. Me conta mais um pouquinho."),
                mood = RobotMood.CURIOUS,
                keepListening = true,
                scene = VisualScene.FEELINGS,
                choices = listOf("CONVERSAR", "ABC", "ROTINA")
            )
        }
    }

    fun onChoice(choice: String): ConversationReply {
        val c = choice.uppercase(Locale.forLanguageTag("pt-BR"))
        if (c in setOf("FELIZ","TRISTE","BRAVA","MEDO","CANSADA","ANIMADA")) return feeling(c)
        if (c == "CARINHAS") return start(PlayMode.FEELINGS)
        if (c == "ABC" || c == "DE NOVO") return start(PlayMode.LETTERS)
        if (c == "ROTINA") { routineStep = "comida"; return start(PlayMode.ROUTINE) }
        if (c == "BRINCAR") { emotionalStage = EmotionalStage.NONE; return ConversationReply("Escolhe uma brincadeira.", RobotMood.CURIOUS, scene = VisualScene.FEELINGS, choices = listOf("CARINHAS", "ABC", "ROTINA")) }
        if (c == "CONVERSAR") return ConversationReply("Pode falar. Eu estou ouvindo você.", RobotMood.LISTENING, keepListening = true, scene = currentEmotionScene)
        if (mode == PlayMode.LETTERS && c in setOf("A","C","P")) return letterChoice(c)
        if (mode == PlayMode.ROUTINE) return routineChoice(c)
        return reply(c)
    }

    private fun feelingsQuestion(): ConversationReply {
        mode = PlayMode.FEELINGS
        currentEmotion = null
        currentEmotionScene = VisualScene.FEELINGS
        return ConversationReply(
            "Oi, ${profile.name}! Como você está se sentindo agora? Escolhe uma carinha.",
            mood = RobotMood.CURIOUS,
            scene = VisualScene.FEELINGS,
            choices = listOf("FELIZ", "TRISTE", "BRAVA", "MEDO", "CANSADA", "ANIMADA")
        )
    }

    private fun feeling(value: String): ConversationReply {
        mode = PlayMode.FEELINGS
        currentEmotion = value
        emotionalStage = EmotionalStage.WAITING_REASON
        return when (value) {
            "FELIZ" -> emotionalReply("Que bom! O que deixou você feliz?", RobotMood.HAPPY, VisualScene.HAPPY_FACE, null)
            "TRISTE" -> emotionalReply("Entendi. Você está triste. O que aconteceu? Pode me contar.", RobotMood.SAD, VisualScene.SAD_FACE, "${profile.name} marcou que está triste.")
            "BRAVA" -> emotionalReply("Entendi. Você está brava. O que aconteceu?", RobotMood.CONFUSED, VisualScene.ANGRY_FACE, "${profile.name} marcou que está brava.")
            "MEDO" -> emotionalReply("Entendi. Você está com medo. O que aconteceu?", RobotMood.SAD, VisualScene.SCARED_FACE, "${profile.name} marcou que está com medo.")
            "CANSADA" -> emotionalReply("Você está cansada. Me conta como foi seu dia.", RobotMood.CURIOUS, VisualScene.TIRED_FACE, null)
            else -> emotionalReply("Você está animada! O que aconteceu de legal?", RobotMood.SURPRISED, VisualScene.EXCITED_FACE, null)
        }
    }

    private fun emotionalReply(text: String, mood: RobotMood, scene: VisualScene, alert: String?) : ConversationReply {
        currentEmotionScene = scene
        return ConversationReply(text, mood, keepListening = true, scene = scene, choices = listOf("CONVERSAR", "BRINCAR"), parentAlert = alert)
    }

    private fun emotionalFollowUp(text: String): ConversationReply {
        val emotion = currentEmotion ?: ""

        if (emotionalStage == EmotionalStage.WAITING_INJURY) {
            emotionalStage = EmotionalStage.NONE
            return if (isYes(text) || containsAny(text, "doeu", "machuquei", "machucou", "sangue")) {
                ConversationReply(
                    "Poxa. Chama a Lety ou a Alice para olhar onde machucou, tá? Depois você pode voltar e me contar.",
                    RobotMood.SAD,
                    scene = currentEmotionScene,
                    choices = listOf("OK", "CONVERSAR"),
                    parentAlert = "${profile.name} disse que caiu e indicou que se machucou."
                )
            } else {
                ConversationReply("Que bom que você não se machucou. Quer me contar como você caiu?", RobotMood.CURIOUS, keepListening = true, scene = currentEmotionScene, choices = listOf("CONVERSAR", "BRINCAR"))
            }
        }

        if (containsAny(text, "caí", "cai", "caí", "cair", "tropecei", "escorreguei")) {
            emotionalStage = EmotionalStage.WAITING_INJURY
            return ConversationReply(
                "Poxa, você caiu. Você se machucou?",
                RobotMood.SAD,
                keepListening = true,
                scene = currentEmotionScene,
                choices = listOf("SIM", "NÃO"),
                parentAlert = "${profile.name} contou que caiu durante uma conversa sobre como estava se sentindo."
            )
        }

        emotionalStage = EmotionalStage.NONE
        val response = when (emotion) {
            "TRISTE" -> "Entendi. Obrigada por me contar. Isso deixou você triste. Quer me contar mais ou quer ficar um pouquinho comigo?"
            "BRAVA" -> "Entendi. Isso deixou você brava. Quer me contar mais um pouquinho?"
            "MEDO" -> "Entendi. Obrigada por me contar. Você quer chamar a Lety ou a Alice para ficar pertinho de você?"
            "FELIZ" -> "Que legal! Gostei de saber. Quer me contar mais?"
            "CANSADA" -> "Entendi. Parece que seu dia foi cansativo. Quer descansar um pouquinho?"
            else -> "Que legal! Me conta mais, eu quero ouvir."
        }
        return ConversationReply(response, RobotMood.CURIOUS, keepListening = true, scene = currentEmotionScene, choices = listOf("CONVERSAR", "BRINCAR"))
    }

    private fun letterQuestion(): ConversationReply {
        emotionalStage = EmotionalStage.NONE
        mode = PlayMode.LETTERS
        return ConversationReply(
            "Olha, ${profile.name}. Esse é um cavalo. Caa-va-lo. Com qual letra começa cavalo?",
            mood = RobotMood.CURIOUS,
            scene = VisualScene.HORSE,
            choices = listOf("A", "C", "P")
        )
    }

    private fun letterChoice(choice: String): ConversationReply {
        return if (choice == "C") ConversationReply("${praise()} C de cavalo!", RobotMood.PROUD, scene = VisualScene.HORSE, choices = listOf("DE NOVO", "BRINCAR"))
        else ConversationReply("Quase. Cavalo começa com C. C de cavalo. Tenta tocar no C.", RobotMood.CURIOUS, scene = VisualScene.HORSE, choices = listOf("A", "C", "P"))
    }

    private fun routineChoice(c: String): ConversationReply = when (routineStep) {
        "comida" -> if (isYes(c)) {
            routineStep = "escovar"
            ConversationReply("Depois de comer, vamos cuidar dos dentinhos. Hora de escovar!", RobotMood.HAPPY, scene = VisualScene.TOOTHBRUSH, choices = listOf("JÁ ESCOVEI", "VOU ESCOVAR"))
        } else ConversationReply("Tudo bem. Quando você comer, eu posso te lembrar dos dentinhos.", choices = listOf("BRINCAR"))
        "escovar" -> ConversationReply("Boa! Você cuidou dos dentinhos.", RobotMood.PROUD, scene = VisualScene.TOOTHBRUSH, choices = listOf("BRINCAR", "ABC"))
        "agua" -> if (isYes(c)) {
            val people = profile.closePeople.joinToString(" ou ")
            ConversationReply("Então pede para $people te dar água, combinado?", scene = VisualScene.WATER, choices = listOf("BRINCAR"))
        } else ConversationReply("Tudo bem. Quando sentir sede, me chama.", choices = listOf("BRINCAR"))
        "xixi" -> if (isYes(c)) {
            routineStep = "voltando"
            ConversationReply("Pode deixar o celular aqui e ir fazer xixi. Eu espero você voltar.", scene = VisualScene.TOILET, waitForMovement = true)
        } else ConversationReply("Tudo bem. Quando der vontade, pode me avisar.", choices = listOf("BRINCAR"))
        "maos" -> if (isYes(c)) ConversationReply("Boa! Mãozinha limpa. Agora podemos brincar.", RobotMood.PROUD, scene = VisualScene.HANDS, choices = listOf("ABC", "BRINCAR")) else onHandsNotWashed()
        else -> ConversationReply("Vamos escolher outra coisa?", scene = VisualScene.FEELINGS, choices = listOf("CARINHAS", "ABC", "ROTINA"))
    }

    fun onMovementDetected(): ConversationReply {
        routineStep = "maos"
        return ConversationReply("Você voltou! Lavou as mãozinhas com água e sabão?", RobotMood.CURIOUS, scene = VisualScene.HANDS, choices = listOf("SIM", "NÃO"))
    }

    fun onHandsNotWashed() = ConversationReply(
        "Vamos lavar. Essas sujeirinhas são pequenininhas, e água com sabão manda elas embora!",
        RobotMood.CURIOUS, scene = VisualScene.BACTERIA, choices = listOf("VOU LAVAR", "BRINCAR")
    )

    private fun repeatRequest(): ConversationReply {
        val prompt = varied("Não consegui entender. Pode falar de novo?", "Pode repetir para mim?", "Eu não ouvi direitinho. Fala mais uma vez?")
        lastPrompt = prompt
        return ConversationReply(prompt, RobotMood.CURIOUS, keepListening = true, scene = currentEmotionScene)
    }
    private fun praise() = varied("Isso!", "Muito bem!", "Você conseguiu!", "Boa!", "Acertou!", "Que legal!")
    private fun varied(vararg values: String): String {
        val options = values.filter { it != lastPrompt }
        val pool = if (options.isNotEmpty()) options else values.toList()
        val result = pool[Random.nextInt(pool.size)]
        lastPrompt = result
        return result
    }
    private fun normalize(input: String) = input.lowercase(Locale.forLanguageTag("pt-BR")).trim()
    private fun isYes(text: String) = containsAny(text.lowercase(), "sim", "já", "ja", "quero", "ok", "vou")
    private fun containsAny(text: String, vararg terms: String) = terms.any { text.contains(it) }
}
