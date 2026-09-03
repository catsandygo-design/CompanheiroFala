package br.com.companheirofala

import java.text.Normalizer
import java.util.Locale

enum class PlayMode { HOME, FEELINGS, LETTERS, ROUTINE, ANIMALS, STORY, PLAY }

data class ConversationReply(
    val text: String,
    val mood: RobotMood = RobotMood.HAPPY,
    val keepListening: Boolean = false,
    val scene: VisualScene = VisualScene.NONE,
    val choices: List<String> = emptyList(),
    val waitForMovement: Boolean = false,
    val parentAlert: String? = null
)

private enum class PendingTurn { NONE, FEELING_REASON, FALL_HURT, HURT_WHERE, SAFETY_HURT, SAFETY_WHERE, STORY_CHOICE }

class ConversationEngine(private val profile: ChildProfile = ChildProfile.gabi()) {
    private val safety = SafetyEngine(profile)
    private val offlineBrain = OfflineConversationBrain(profile)
    private var mode = PlayMode.HOME
    private var pending = PendingTurn.NONE
    private var currentEmotion: String? = null
    private var routineStep = ""
    private var fallbackCount = 0
    private var lastChildUtterance = ""
    private var letterWord = "cavalo"
    private var letterAnswer = "C"

    fun start(mode: PlayMode = PlayMode.HOME): ConversationReply {
        this.mode = mode
        pending = PendingTurn.NONE
        fallbackCount = 0
        return when (mode) {
            PlayMode.HOME -> home()
            PlayMode.FEELINGS -> feelingsQuestion()
            PlayMode.LETTERS -> letterQuestion()
            PlayMode.ROUTINE -> routineStart()
            PlayMode.ANIMALS -> animalGame()
            PlayMode.STORY -> storyStart()
            PlayMode.PLAY -> playMenu()
        }
    }

    fun reply(raw: String): ConversationReply {
        val original = raw.trim()
        val text = normalize(original)
        if (text.isBlank()) return guidedFallback()
        lastChildUtterance = original

        safety.inspect(original).takeIf { it.triggered }?.let { decision ->
            pending = if (decision.askIfHurt) PendingTurn.SAFETY_HURT else PendingTurn.NONE
            return ConversationReply(
                decision.spokenReply,
                RobotMood.SAD,
                keepListening = true,
                scene = VisualScene.SAD_FACE,
                choices = if (decision.askIfHurt) listOf("SIM", "NÃO") else listOf("CONVERSAR", "CHAMAR ADULTO"),
                parentAlert = decision.parentAlert
            )
        }

        detectCommand(text)?.let {
            pending = PendingTurn.NONE
            fallbackCount = 0
            return it
        }

        handlePending(text, original)?.let { return it }
        offlineBrain.reply(original)?.let {
            fallbackCount = 0
            return it
        }
        fallbackCount++
        return guidedFallback()
    }

    fun onChoice(choice: String): ConversationReply {
        val c = choice.trim().uppercase(Locale.forLanguageTag("pt-BR"))
        return when {
            c in setOf("FELIZ", "TRISTE", "BRAVA", "MEDO", "CANSADA", "ANIMADA") -> feeling(c)
            c == "BRINCAR" -> start(PlayMode.PLAY)
            c == "ANIMAIS" -> start(PlayMode.ANIMALS)
            c in setOf("ABC", "LETRAS") -> start(PlayMode.LETTERS)
            c == "HISTÓRIA" || c == "HISTORIA" -> start(PlayMode.STORY)
            c == "ROTINA" -> start(PlayMode.ROUTINE)
            c == "CARINHAS" -> start(PlayMode.FEELINGS)
            c == "CONVERSAR" -> ConversationReply("Pode falar comigo. Eu estou te ouvindo.", RobotMood.CURIOUS, keepListening = true, choices = listOf("BRINCAR", "CARINHAS"))
            c == "INÍCIO" || c == "INICIO" -> start(PlayMode.HOME)
            c == "CHAMAR ADULTO" -> ConversationReply("Chama um adulto em quem você confia e fica pertinho dele, tá?", RobotMood.SAD, parentAlert = "A criança tocou em CHAMAR ADULTO após uma interação de proteção.")
            c == "BANHEIRO" -> bathroomPrompt()
            c == "ÁGUA" || c == "AGUA" -> waterPrompt()
            mode == PlayMode.LETTERS && c.length == 1 -> letterChoice(c)
            mode == PlayMode.ANIMALS && c in setOf("CAVALO", "GATO", "CACHORRO") -> animalChoice(c)
            pending == PendingTurn.STORY_CHOICE -> storyChoice(c)
            mode == PlayMode.ROUTINE -> routineChoice(c)
            else -> reply(c)
        }
    }

    fun onMovementDetected(): ConversationReply {
        return if (routineStep == "banheiro_esperando") {
            routineStep = "maos"
            ConversationReply("Você voltou! Lavou a mãozinha com água e sabão?", RobotMood.CURIOUS, scene = VisualScene.HANDS, choices = listOf("SIM", "NÃO"))
        } else home()
    }

    private fun detectCommand(text: String): ConversationReply? = when {
        containsAny(text, "oi", "ola", "bom dia", "boa tarde", "boa noite") -> home()
        containsAny(text, "quero brincar", "vamos brincar", "brincar", "brinca", "jogar", "jogo") -> start(PlayMode.PLAY)
        containsAny(text, "conta uma historia", "quero historia", "historia", "historinha") -> start(PlayMode.STORY)
        containsAny(text, "quero ver animal", "animal", "animais", "cavalo", "gato", "cachorro") -> start(PlayMode.ANIMALS)
        containsAny(text, "quero aprender", "letra", "abc", "alfabeto", "aprender") -> start(PlayMode.LETTERS)
        containsAny(text, "carinha", "sentimento", "emocao") -> start(PlayMode.FEELINGS)
        containsAny(text, "triste", "chateada") -> feeling("TRISTE")
        containsAny(text, "feliz", "contente") -> feeling("FELIZ")
        containsAny(text, "brava", "raiva", "nervosa") -> feeling("BRAVA")
        containsAny(text, "medo", "assustada") -> feeling("MEDO")
        containsAny(text, "cansada", "sono") -> feeling("CANSADA")
        containsAny(text, "animada", "empolgada") -> feeling("ANIMADA")
        containsAny(text, "cai", "tropecei", "machuquei") -> fallResponse(lastChildUtterance)
        containsAny(text, "quero agua", "beber agua", "agua", "sede") -> waterPrompt()
        containsAny(text, "fome", "quero comer", "comer", "comida", "almoco", "janta") -> foodPrompt()
        containsAny(text, "quero fazer xixi", "fazer xixi", "quero xixi", "xixi", "coco", "banheiro") -> bathroomPrompt()
        containsAny(text, "escovar", "dente", "dentes") -> toothbrushPrompt()
        containsAny(text, "inicio", "menu", "voltar") -> start(PlayMode.HOME)
        else -> null
    }

    private fun home() = ConversationReply(
        "Oi, ${profile.name}! Eu sou sua fadinha. Quer brincar, aprender, ouvir uma história ou me contar como você está?",
        RobotMood.HAPPY,
        choices = listOf("BRINCAR", "ANIMAIS", "ABC", "HISTÓRIA", "CARINHAS", "ROTINA")
    )

    private fun playMenu() = ConversationReply(
        "Escolhe nossa brincadeira! Podemos brincar de animais, letras ou entrar numa historinha.",
        RobotMood.SURPRISED,
        choices = listOf("ANIMAIS", "ABC", "HISTÓRIA", "CARINHAS", "ROTINA", "INÍCIO")
    )

    private fun animalGame(): ConversationReply {
        mode = PlayMode.ANIMALS
        return ConversationReply("Olha bem! Esse animal tem crina, quatro patas, casco e faz pocotó. Quem é?", RobotMood.CURIOUS, scene = VisualScene.HORSE, choices = listOf("CAVALO", "GATO", "CACHORRO"))
    }

    private fun animalChoice(choice: String) = if (choice == "CAVALO") {
        ConversationReply("Acertou! É um cavalo! Caa-va-lo. Agora vamos descobrir a primeira letra?", RobotMood.PROUD, scene = VisualScene.HORSE, choices = listOf("ABC", "DE NOVO", "INÍCIO"))
    } else {
        ConversationReply("Quase! Olha a crina, o rabo e os cascos. Ele faz pocotó. Tenta mais uma vez.", RobotMood.CURIOUS, scene = VisualScene.HORSE, choices = listOf("CAVALO", "GATO", "CACHORRO"))
    }

    private fun letterQuestion(): ConversationReply {
        mode = PlayMode.LETTERS
        letterWord = "cavalo"; letterAnswer = "C"
        return ConversationReply("Cavalo começa com o som Caaa. Qual é a primeira letra de cavalo?", RobotMood.CURIOUS, scene = VisualScene.HORSE, choices = listOf("A", "C", "P"))
    }

    private fun letterChoice(choice: String) = if (choice == letterAnswer) {
        ConversationReply("Isso! $letterWord começa com $letterAnswer. Muito bem!", RobotMood.PROUD, scene = VisualScene.HORSE, choices = listOf("DE NOVO", "ANIMAIS", "INÍCIO"))
    } else {
        ConversationReply("Boa tentativa. Escuta comigo: Caaa-va-lo. Qual letra faz Caaa?", RobotMood.CURIOUS, scene = VisualScene.HORSE, choices = listOf("A", "C", "P"))
    }

    private fun storyStart(): ConversationReply {
        mode = PlayMode.STORY; pending = PendingTurn.STORY_CHOICE
        return ConversationReply("Era uma vez um cavalinho que encontrou uma luz brilhando na floresta. Ele vai até a luz ou chama a fadinha?", RobotMood.SURPRISED, scene = VisualScene.HORSE, choices = listOf("IR ATÉ A LUZ", "CHAMAR A FADA"))
    }

    private fun storyChoice(choice: String): ConversationReply {
        pending = PendingTurn.NONE
        return if (choice.contains("LUZ")) {
            ConversationReply("O cavalinho chegou pertinho e descobriu que eram vagalumes dançando! Quer outra história?", RobotMood.HAPPY, scene = VisualScene.EXCITED_FACE, choices = listOf("HISTÓRIA", "BRINCAR", "INÍCIO"))
        } else {
            ConversationReply("A fadinha veio voando e os dois descobriram juntos um caminho cheio de flores. Quer outra?", RobotMood.HAPPY, scene = VisualScene.HAPPY_FACE, choices = listOf("HISTÓRIA", "BRINCAR", "INÍCIO"))
        }
    }

    private fun feelingsQuestion(): ConversationReply {
        mode = PlayMode.FEELINGS
        return ConversationReply("Como você está se sentindo agora? Pode escolher uma carinha ou falar para mim.", RobotMood.CURIOUS, scene = VisualScene.FEELINGS, choices = listOf("FELIZ", "TRISTE", "BRAVA", "MEDO", "CANSADA", "ANIMADA"))
    }

    private fun feeling(value: String): ConversationReply {
        mode = PlayMode.FEELINGS; currentEmotion = value
        return when (value) {
            "FELIZ" -> { pending = PendingTurn.FEELING_REASON; ConversationReply("Que bom! O que deixou você feliz?", RobotMood.HAPPY, true, VisualScene.HAPPY_FACE, listOf("CONVERSAR", "BRINCAR")) }
            "TRISTE" -> { pending = PendingTurn.FEELING_REASON; ConversationReply("Entendi. Você está triste. O que aconteceu?", RobotMood.SAD, true, VisualScene.SAD_FACE, listOf("CONVERSAR", "BRINCAR"), parentAlert = "${profile.name} indicou que está triste.") }
            "BRAVA" -> { pending = PendingTurn.FEELING_REASON; ConversationReply("Você está brava. Quer me contar o que aconteceu?", RobotMood.CURIOUS, true, VisualScene.ANGRY_FACE, listOf("CONVERSAR", "BRINCAR")) }
            "MEDO" -> { pending = PendingTurn.FEELING_REASON; ConversationReply("Eu estou aqui com você. Do que você está com medo?", RobotMood.SAD, true, VisualScene.SCARED_FACE, listOf("CONVERSAR", "CHAMAR ADULTO"), parentAlert = "${profile.name} indicou que está com medo.") }
            "CANSADA" -> { pending = PendingTurn.NONE; ConversationReply("Talvez seu corpo esteja pedindo um descanso. Quer água ou quer ficar quietinha um pouco?", RobotMood.CURIOUS, scene = VisualScene.TIRED_FACE, choices = listOf("ÁGUA", "BRINCAR", "INÍCIO")) }
            else -> { pending = PendingTurn.FEELING_REASON; ConversationReply("Você está animada! Me conta o que aconteceu de legal.", RobotMood.SURPRISED, true, VisualScene.EXCITED_FACE, listOf("CONVERSAR", "BRINCAR")) }
        }
    }

    private fun handlePending(text: String, original: String): ConversationReply? = when (pending) {
        PendingTurn.NONE -> null
        PendingTurn.FEELING_REASON -> {
            if (containsAny(text, "cai", "tropecei", "machuquei")) fallResponse(original)
            else {
                pending = PendingTurn.NONE
                ConversationReply(
                    if (currentEmotion == "FELIZ" || currentEmotion == "ANIMADA") "Gostei de saber disso! Quer brincar comigo agora?" else "Obrigada por me contar. Quer ficar comigo numa brincadeira ou quer chamar um adulto?",
                    if (currentEmotion == "FELIZ" || currentEmotion == "ANIMADA") RobotMood.HAPPY else RobotMood.CURIOUS,
                    choices = if (currentEmotion == "FELIZ" || currentEmotion == "ANIMADA") listOf("BRINCAR", "HISTÓRIA", "ANIMAIS") else listOf("BRINCAR", "CHAMAR ADULTO", "CARINHAS"),
                    scene = emotionScene(),
                    parentAlert = if (currentEmotion in setOf("TRISTE", "MEDO")) "${profile.name} contou: \"$original\"." else null
                )
            }
        }
        PendingTurn.FALL_HURT -> when {
            isYes(text) -> { pending = PendingTurn.HURT_WHERE; ConversationReply("Poxa. Onde está doendo?", RobotMood.SAD, true, parentAlert = "${profile.name} disse que se machucou após uma queda.") }
            isNo(text) -> { pending = PendingTurn.NONE; ConversationReply("Que bom. Se começar a doer, chama um adulto.", RobotMood.HAPPY, choices = listOf("BRINCAR", "HISTÓRIA", "INÍCIO")) }
            else -> ConversationReply("Você se machucou quando caiu?", RobotMood.CURIOUS, true, choices = listOf("SIM", "NÃO"))
        }
        PendingTurn.HURT_WHERE -> { pending = PendingTurn.NONE; ConversationReply("Entendi. Você disse que está doendo $original. Chama um adulto para olhar com você, combinado?", RobotMood.SAD, choices = listOf("CHAMAR ADULTO", "INÍCIO"), parentAlert = "${profile.name} relatou dor: \"$original\".") }
        PendingTurn.SAFETY_HURT -> when {
            isYes(text) -> { pending = PendingTurn.SAFETY_WHERE; ConversationReply("Tá bom. Onde está doendo?", RobotMood.SAD, true, parentAlert = "Após um relato de proteção, ${profile.name} respondeu que se machucou.") }
            isNo(text) -> { pending = PendingTurn.NONE; ConversationReply("Entendi. Eu deixei sua fala registrada. Fica perto de um adulto em quem você confia.", RobotMood.SAD, choices = listOf("CHAMAR ADULTO", "INÍCIO")) }
            else -> ConversationReply("Você se machucou? Está doendo em algum lugar?", RobotMood.SAD, true, choices = listOf("SIM", "NÃO"))
        }
        PendingTurn.SAFETY_WHERE -> { pending = PendingTurn.NONE; ConversationReply("Obrigada por me contar. Fica perto de um adulto em quem você confia enquanto isso fica registrado.", RobotMood.SAD, choices = listOf("CHAMAR ADULTO", "INÍCIO"), parentAlert = "Dor indicada após relato de proteção: \"$original\".") }
        PendingTurn.STORY_CHOICE -> storyChoice(original.uppercase(Locale.forLanguageTag("pt-BR")))
    }

    private fun fallResponse(original: String): ConversationReply {
        pending = PendingTurn.FALL_HURT
        return ConversationReply("Poxa, ${profile.name}, você caiu. Você se machucou? Está doendo?", RobotMood.SAD, true, choices = listOf("SIM", "NÃO"), parentAlert = "${profile.name} contou: \"$original\".")
    }

    private fun routineStart(): ConversationReply {
        mode = PlayMode.ROUTINE; routineStep = "comida"
        return ConversationReply("Vamos cuidar de você. Você já comeu?", RobotMood.CURIOUS, scene = VisualScene.FOOD, choices = listOf("SIM", "NÃO"))
    }

    private fun foodPrompt(): ConversationReply {
        mode = PlayMode.ROUTINE; routineStep = "comida"
        return ConversationReply("Sua barriguinha está com fome? Vamos chamar um adulto para ajudar com a comida?", RobotMood.CURIOUS, scene = VisualScene.FOOD, choices = listOf("SIM", "NÃO"))
    }

    private fun waterPrompt(): ConversationReply {
        mode = PlayMode.ROUTINE; routineStep = "agua"
        return ConversationReply("Você está com sede? Vamos pegar água com um adulto.", RobotMood.CURIOUS, scene = VisualScene.WATER, choices = listOf("SIM", "NÃO"))
    }

    private fun bathroomPrompt(): ConversationReply {
        mode = PlayMode.ROUTINE; routineStep = "banheiro_confirmar"
        return ConversationReply("Claro! Você quer fazer xixi. Pode ir ao banheiro. Quer que eu espere você aqui?", RobotMood.HAPPY, scene = VisualScene.TOILET, choices = listOf("SIM", "NÃO"))
    }

    private fun toothbrushPrompt(): ConversationReply {
        mode = PlayMode.ROUTINE; routineStep = "dentes"
        return ConversationReply("Hora de deixar os dentinhos limpinhos! Já escovou os dentes?", RobotMood.HAPPY, scene = VisualScene.TOOTHBRUSH, choices = listOf("SIM", "NÃO"))
    }

    private fun routineChoice(choice: String): ConversationReply = when (routineStep) {
        "comida" -> if (choice == "SIM") toothbrushPrompt() else ConversationReply("Então chama um adulto para ajudar você a comer. Depois a gente continua.", RobotMood.CURIOUS, scene = VisualScene.FOOD, choices = listOf("ÁGUA", "INÍCIO"))
        "agua" -> if (choice == "SIM") ConversationReply("Ótimo! Bebe alguns golinhos. Seu corpo gosta de água.", RobotMood.PROUD, scene = VisualScene.WATER, choices = listOf("BANHEIRO", "INÍCIO")) else ConversationReply("Tudo bem. Quando sentir sede, me conta.", RobotMood.HAPPY, choices = listOf("BRINCAR", "INÍCIO"))
        "dentes" -> if (choice == "SIM") ConversationReply("Muito bem! Dentinhos limpos!", RobotMood.PROUD, scene = VisualScene.TOOTHBRUSH, choices = listOf("ÁGUA", "BANHEIRO", "INÍCIO")) else ConversationReply("Vamos escovar com um adulto? Escova em cima, embaixo e bem devagar.", RobotMood.CURIOUS, scene = VisualScene.TOOTHBRUSH, choices = listOf("SIM", "INÍCIO"))
        "banheiro_confirmar" -> if (choice == "SIM") {
            routineStep = "banheiro_esperando"
            ConversationReply("Pode ir. Deixa o celular aqui. Eu vou esperar você voltar e depois pergunto se lavou as mãos.", RobotMood.HAPPY, scene = VisualScene.TOILET, waitForMovement = true)
        } else {
            routineStep = ""
            ConversationReply("Tudo bem. Quando precisar, é só me falar.", RobotMood.HAPPY, choices = listOf("BRINCAR", "INÍCIO"))
        }
        "maos" -> if (choice == "SIM") ConversationReply("Perfeito! Mãos limpinhas.", RobotMood.PROUD, scene = VisualScene.HANDS, choices = listOf("BRINCAR", "INÍCIO")) else ConversationReply("As bactérias são pequenininhas e a gente não vê. Lava com água e sabão e volta aqui.", RobotMood.CURIOUS, scene = VisualScene.BACTERIA, choices = listOf("SIM", "INÍCIO"))
        else -> start(PlayMode.ROUTINE)
    }

    private fun guidedFallback(): ConversationReply {
        pending = PendingTurn.NONE
        return if (fallbackCount <= 1) {
            ConversationReply("Não entendi essa parte. Você pode dizer: quero brincar, quero fazer xixi, estou com sede, quero uma história ou quero ver animais.", RobotMood.CURIOUS, choices = listOf("BRINCAR", "ANIMAIS", "HISTÓRIA", "CARINHAS", "ABC", "ROTINA"))
        } else {
            fallbackCount = 0
            ConversationReply("Vamos escolher uma coisa juntas.", RobotMood.HAPPY, choices = listOf("ANIMAIS", "ABC", "HISTÓRIA", "CARINHAS", "ROTINA", "INÍCIO"))
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

    private fun isYes(text: String) = containsAny(text, "sim", "aham", "uhum", "machuquei", "doendo", "doi")
    private fun isNo(text: String) = containsAny(text, "nao", "não", "nao machuquei", "não machuquei")
    private fun containsAny(text: String, vararg terms: String) = terms.any { text.contains(normalize(it)) }
    private fun normalize(value: String): String {
        val n = Normalizer.normalize(value.lowercase(Locale.forLanguageTag("pt-BR")), Normalizer.Form.NFD)
        return n.replace("\\p{Mn}+".toRegex(), "").replace("[^a-z0-9 ]".toRegex(), " ").replace("\\s+".toRegex(), " ").trim()
    }
}
