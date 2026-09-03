package br.com.companheirofala

import java.util.Locale
import kotlin.random.Random

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

private enum class PendingTurn {
    NONE,
    FEELING_REASON,
    SAD_REASON,
    ANGRY_REASON,
    FEAR_REASON,
    FALL_HURT,
    HURT_WHERE,
    SAFETY_HURT,
    SAFETY_WHERE,
    MORE_ABOUT_EVENT
}

class ConversationEngine(private val profile: ChildProfile = ChildProfile.gabi()) {
    private var mode = PlayMode.HOME
    private var routineStep = ""
    private var pending = PendingTurn.NONE
    private var currentEmotion: String? = null
    private var lastPrompt = ""
    private var lastChildUtterance = ""
    private val history = ArrayDeque<String>()

    fun start(mode: PlayMode = PlayMode.HOME): ConversationReply {
        this.mode = mode
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
        val original = raw.trim()
        val text = normalize(original)
        if (text.isBlank()) return repeatRequest()
        remember("criança: $original")
        lastChildUtterance = original

        // Proteção vem antes de qualquer brincadeira ou atividade.
        detectSafety(text, original)?.let { return it }

        // Uma resposta nunca é tratada isoladamente: primeiro resolvemos a pergunta pendente.
        handlePending(text, original)?.let { return it }

        return when {
            containsAny(text, "triste", "chateada") -> feeling("TRISTE")
            containsAny(text, "feliz", "bem", "contente") -> feeling("FELIZ")
            containsAny(text, "brava", "raiva", "nervosa") -> feeling("BRAVA")
            containsAny(text, "medo", "assustada", "assustado") -> feeling("MEDO")
            containsAny(text, "cansada", "sono") -> feeling("CANSADA")
            containsAny(text, "animada", "empolgada") -> feeling("ANIMADA")
            containsAny(text, "caí", "cai", "caindo", "tropecei") -> fallResponse(original)
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
            else -> contextualFallback(original)
        }
    }

    fun onChoice(choice: String): ConversationReply {
        val c = choice.uppercase(Locale.forLanguageTag("pt-BR"))
        if (c in setOf("FELIZ", "TRISTE", "BRAVA", "MEDO", "CANSADA", "ANIMADA")) return feeling(c)
        if (c == "CARINHAS") return start(PlayMode.FEELINGS)
        if (c == "ABC" || c == "DE NOVO") return start(PlayMode.LETTERS)
        if (c == "ROTINA") { routineStep = "comida"; return start(PlayMode.ROUTINE) }
        if (c == "BRINCAR") { pending = PendingTurn.NONE; return ConversationReply("Tá bom. Escolhe o que você quer fazer comigo.", RobotMood.CURIOUS, choices = listOf("CARINHAS", "ABC", "ROTINA")) }
        if (c == "CONVERSAR") return ConversationReply("Pode me contar. Eu estou ouvindo você.", RobotMood.CURIOUS, keepListening = true)
        if (mode == PlayMode.LETTERS && c in setOf("A", "C", "P")) return letterChoice(c)
        if (mode == PlayMode.ROUTINE) return routineChoice(c)
        return reply(c)
    }

    private fun feelingsQuestion(): ConversationReply {
        mode = PlayMode.FEELINGS
        pending = PendingTurn.NONE
        return ConversationReply(
            "Oi, ${profile.name}! Como você está se sentindo agora? Escolhe uma carinha ou me conta.",
            mood = RobotMood.CURIOUS,
            scene = VisualScene.FEELINGS,
            choices = listOf("FELIZ", "TRISTE", "BRAVA", "MEDO", "CANSADA", "ANIMADA")
        )
    }

    private fun feeling(value: String): ConversationReply {
        mode = PlayMode.FEELINGS
        currentEmotion = value
        return when (value) {
            "FELIZ" -> {
                pending = PendingTurn.FEELING_REASON
                emotionalReply("Que bom! O que deixou você feliz?", RobotMood.HAPPY, VisualScene.HAPPY_FACE, true)
            }
            "TRISTE" -> {
                pending = PendingTurn.SAD_REASON
                emotionalReply(
                    "Entendi, ${profile.name}. Você está triste. O que aconteceu? Pode me contar.",
                    RobotMood.SAD, VisualScene.SAD_FACE, true,
                    "${profile.name} marcou que está triste."
                )
            }
            "BRAVA" -> {
                pending = PendingTurn.ANGRY_REASON
                emotionalReply(
                    "Entendi. Você está brava. O que aconteceu?",
                    RobotMood.CONFUSED, VisualScene.ANGRY_FACE, true,
                    "${profile.name} marcou que está brava."
                )
            }
            "MEDO" -> {
                pending = PendingTurn.FEAR_REASON
                emotionalReply(
                    "Eu ouvi você. Do que você está com medo? Pode me contar.",
                    RobotMood.SAD, VisualScene.SCARED_FACE, true,
                    "${profile.name} marcou que está com medo."
                )
            }
            "CANSADA" -> {
                pending = PendingTurn.NONE
                emotionalReply("Você está cansada. Quer descansar um pouquinho ou me contar como foi seu dia?", RobotMood.CURIOUS, VisualScene.TIRED_FACE, true)
            }
            else -> {
                pending = PendingTurn.FEELING_REASON
                emotionalReply("Você está animada! O que aconteceu de legal?", RobotMood.SURPRISED, VisualScene.EXCITED_FACE, true)
            }
        }
    }

    private fun handlePending(text: String, original: String): ConversationReply? = when (pending) {
        PendingTurn.NONE -> null
        PendingTurn.FEELING_REASON -> {
            pending = PendingTurn.MORE_ABOUT_EVENT
            ConversationReply("Que legal você me contar isso. Quer me contar mais?", RobotMood.HAPPY, keepListening = true, scene = emotionScene())
        }
        PendingTurn.SAD_REASON, PendingTurn.ANGRY_REASON, PendingTurn.FEAR_REASON -> {
            if (containsAny(text, "caí", "cai", "tropecei")) fallResponse(original)
            else {
                pending = PendingTurn.MORE_ABOUT_EVENT
                ConversationReply(
                    "Entendi. Obrigada por me contar, ${profile.name}. Quer me contar mais sobre isso?",
                    mood = if (currentEmotion == "TRISTE" || currentEmotion == "MEDO") RobotMood.SAD else RobotMood.CURIOUS,
                    keepListening = true,
                    scene = emotionScene(),
                    parentAlert = if (currentEmotion in setOf("TRISTE", "MEDO")) "${profile.name} contou: \"$original\"" else null
                )
            }
        }
        PendingTurn.FALL_HURT -> when {
            isYes(text) -> {
                pending = PendingTurn.HURT_WHERE
                ConversationReply("Poxa. Onde está doendo? Pode me mostrar ou falar.", RobotMood.SAD, keepListening = true,
                    parentAlert = "${profile.name} disse que caiu e respondeu que se machucou.")
            }
            isNo(text) -> {
                pending = PendingTurn.NONE
                ConversationReply("Que bom que você não se machucou. Se começar a doer, chama um adulto, tá?", RobotMood.HAPPY, choices = listOf("BRINCAR", "CARINHAS"))
            }
            else -> {
                pending = PendingTurn.FALL_HURT
                ConversationReply("Eu quero entender direitinho. Você se machucou quando caiu?", RobotMood.CURIOUS, keepListening = true, choices = listOf("SIM", "NÃO"))
            }
        }
        PendingTurn.HURT_WHERE -> {
            pending = PendingTurn.NONE
            ConversationReply(
                "Entendi. Você disse que está doendo $original. Chama a Lety ou a Alice para olhar com você, combinado?",
                RobotMood.SAD,
                parentAlert = "${profile.name} relatou uma queda e disse que está doendo: \"$original\"."
            )
        }
        PendingTurn.SAFETY_HURT -> when {
            isYes(text) -> {
                pending = PendingTurn.SAFETY_WHERE
                ConversationReply("Tá bom, ${profile.name}. Onde está doendo?", RobotMood.SAD, keepListening = true,
                    parentAlert = "Após um relato de segurança, ${profile.name} respondeu que se machucou.")
            }
            isNo(text) -> {
                pending = PendingTurn.NONE
                ConversationReply("Entendi. Eu já guardei o que você me contou. Fica pertinho de um adulto em quem você confia, tá?", RobotMood.CURIOUS,
                    parentAlert = "${profile.name} respondeu que não se machucou após o relato: \"$lastChildUtterance\".")
            }
            else -> {
                pending = PendingTurn.SAFETY_HURT
                ConversationReply("Você se machucou? Está doendo em algum lugar?", RobotMood.SAD, keepListening = true, choices = listOf("SIM", "NÃO"))
            }
        }
        PendingTurn.SAFETY_WHERE -> {
            pending = PendingTurn.NONE
            ConversationReply(
                "Entendi. Obrigada por me contar. Fica perto da Lety, da Alice ou de outro adulto em quem você confia enquanto eu deixo isso registrado.",
                RobotMood.SAD,
                parentAlert = "${profile.name} indicou dor após um relato de segurança: \"$original\"."
            )
        }
        PendingTurn.MORE_ABOUT_EVENT -> {
            if (isNo(text)) {
                pending = PendingTurn.NONE
                ConversationReply("Tudo bem. Obrigada por me contar. Eu fico aqui com você.", RobotMood.HAPPY, choices = listOf("BRINCAR", "CARINHAS"))
            } else {
                pending = PendingTurn.MORE_ABOUT_EVENT
                ConversationReply("Estou ouvindo. E depois, o que aconteceu?", RobotMood.CURIOUS, keepListening = true, scene = emotionScene())
            }
        }
    }

    private fun detectSafety(text: String, original: String): ConversationReply? {
        val physical = containsAny(text,
            "me bateu", "me bate", "me machucou", "me empurrou", "me chutou", "me beliscou", "me puxou", "fez dodói em mim", "fez dodoi em mim")
        if (physical) {
            pending = PendingTurn.SAFETY_HURT
            val possiblePerson = extractPersonBeforeAction(original)
            val firstLine = if (!possiblePerson.isNullOrBlank()) {
                "$possiblePerson! A ${profile.name} disse que você machucou ela. Eu vou deixar isso registrado para o responsável."
            } else {
                "${profile.name}, eu ouvi o que você falou e vou deixar isso registrado para o responsável."
            }
            return ConversationReply(
                "$firstLine ${profile.name}, você se machucou? Está doendo em algum lugar?",
                RobotMood.SAD,
                keepListening = true,
                choices = listOf("SIM", "NÃO"),
                parentAlert = "ALERTA DE PROTEÇÃO — ${profile.name} disse exatamente: \"$original\". O aplicativo não confirmou o fato; registrou a fala da criança."
            )
        }
        if (containsAny(text, "estou com medo dele", "estou com medo dela", "tenho medo dele", "tenho medo dela")) {
            pending = PendingTurn.MORE_ABOUT_EVENT
            return ConversationReply(
                "Entendi, ${profile.name}. Obrigada por me contar. Fica perto de um adulto em quem você confia. Você quer me contar o que aconteceu?",
                RobotMood.SAD, keepListening = true,
                parentAlert = "ALERTA DE PROTEÇÃO — ${profile.name} disse exatamente: \"$original\"."
            )
        }
        return null
    }

    private fun fallResponse(original: String): ConversationReply {
        pending = PendingTurn.FALL_HURT
        return ConversationReply(
            "Poxa, ${profile.name}, você caiu. Você se machucou? Está doendo?",
            RobotMood.SAD,
            keepListening = true,
            choices = listOf("SIM", "NÃO"),
            parentAlert = "${profile.name} contou: \"$original\"."
        )
    }

    private fun contextualFallback(original: String): ConversationReply {
        return if (mode == PlayMode.FEELINGS && currentEmotion != null) {
            pending = PendingTurn.MORE_ABOUT_EVENT
            ConversationReply(
                "Entendi. Você disse: $original. E depois, o que aconteceu?",
                RobotMood.CURIOUS,
                keepListening = true,
                scene = emotionScene()
            )
        } else {
            ConversationReply(
                varied(
                    "Eu ouvi você. Me conta mais um pouquinho.",
                    "Entendi. E o que aconteceu depois?",
                    "Tá bom. Pode continuar, eu estou ouvindo."
                ),
                RobotMood.CURIOUS,
                keepListening = true
            )
        }
    }

    private fun emotionScene() = when (currentEmotion) {
        "FELIZ" -> VisualScene.HAPPY_FACE
        "TRISTE" -> VisualScene.SAD_FACE
        "BRAVA" -> VisualScene.ANGRY_FACE
        "MEDO" -> VisualScene.SCARED_FACE
        "CANSADA" -> VisualScene.TIRED_FACE
        "ANIMADA" -> VisualScene.EXCITED_FACE
        else -> VisualScene.NONE
    }

    private fun emotionalReply(text: String, mood: RobotMood, scene: VisualScene, listen: Boolean, alert: String? = null) =
        ConversationReply(text = text, mood = mood, keepListening = listen, scene = scene, choices = listOf("CONVERSAR", "BRINCAR"), parentAlert = alert)

    private fun letterQuestion(): ConversationReply {
        mode = PlayMode.LETTERS; pending = PendingTurn.NONE
        return ConversationReply("Olha, ${profile.name}. Esse é um cavalo. Caa-va-lo. Com qual letra começa cavalo?", RobotMood.CURIOUS, scene = VisualScene.HORSE, choices = listOf("A", "C", "P"))
    }

    private fun letterChoice(choice: String): ConversationReply = if (choice == "C") {
        ConversationReply("${praise()} C de cavalo!", RobotMood.PROUD, scene = VisualScene.HORSE, choices = listOf("DE NOVO", "BRINCAR"))
    } else {
        ConversationReply("Quase. Cavalo começa com C. C de cavalo. Tenta tocar no C.", RobotMood.CURIOUS, scene = VisualScene.HORSE, choices = listOf("A", "C", "P"))
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
        else -> ConversationReply("Vamos escolher outra coisa?", choices = listOf("CARINHAS", "ABC", "ROTINA"))
    }

    fun onMovementDetected(): ConversationReply {
        routineStep = "maos"
        return ConversationReply("Você voltou! Lavou as mãozinhas com água e sabão?", RobotMood.CURIOUS, scene = VisualScene.HANDS, choices = listOf("SIM", "NÃO"))
    }

    fun onHandsNotWashed() = ConversationReply("Vamos lavar. Essas sujeirinhas são pequenininhas, e água com sabão manda elas embora!", RobotMood.CURIOUS, scene = VisualScene.BACTERIA, choices = listOf("VOU LAVAR", "BRINCAR"))

    private fun extractPersonBeforeAction(original: String): String? {
        val lower = normalize(original)
        val actions = listOf(" me bateu", " me machucou", " me empurrou", " me chutou", " me beliscou", " me puxou")
        val idx = actions.map { lower.indexOf(it) }.filter { it > 0 }.minOrNull() ?: return null
        val prefix = original.substring(0, idx).trim().removePrefix("a ").removePrefix("o ").trim()
        if (prefix.length !in 2..30) return null
        return prefix.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.forLanguageTag("pt-BR")) else it.toString() }
    }

    private fun remember(item: String) { history.addLast(item); while (history.size > 12) history.removeFirst() }
    private fun repeatRequest(): ConversationReply = ConversationReply(varied("Não consegui entender. Pode falar de novo?", "Pode repetir para mim?", "Eu não ouvi direitinho. Fala mais uma vez?"), RobotMood.CURIOUS, keepListening = true)
    private fun praise() = varied("Isso!", "Muito bem!", "Você conseguiu!", "Boa!", "Acertou!", "Que legal!")
    private fun varied(vararg values: String): String { val options = values.filter { it != lastPrompt }; val source = if (options.isNotEmpty()) options else values.toList(); return source[Random.nextInt(source.size)].also { lastPrompt = it } }
    private fun normalize(input: String) = input.lowercase(Locale.forLanguageTag("pt-BR")).trim()
    private fun isYes(text: String) = containsAny(text, "sim", "aham", "uhum", "machuquei", "doeu", "dói", "doi", "está", "esta")
    private fun isNo(text: String) = containsAny(text, "não", "nao", "não machuquei", "nao machuquei", "não doeu", "nao doeu")
    private fun containsAny(text: String, vararg terms: String) = terms.any { text.contains(it) }
}