package br.com.companheirofala

import java.text.Normalizer
import java.util.Locale

enum class PlayMode { HOME, FEELINGS, LETTERS, ROUTINE, ANIMALS, STORY, PLAY, GUESS }

data class ConversationReply(
    val text: String,
    val mood: RobotMood = RobotMood.HAPPY,
    val keepListening: Boolean = false,
    val scene: VisualScene = VisualScene.NONE,
    val choices: List<String> = emptyList(),
    val waitForMovement: Boolean = false,
    val parentAlert: String? = null,
    val playTune: Boolean = false,
    val imageKey: String? = null
)

private enum class PendingTurn { NONE, FEELING_REASON, FALL_HURT, HURT_WHERE, SAFETY_HURT, SAFETY_WHERE, SCHOOL_HAPPENED, SCHOOL_KIND, SCHOOL_SAFETY, STORY_CHOICE }

class ConversationEngine(
    private val profile: ChildProfile = ChildProfile.gabi(),
    private val memory: ChildMemory = SessionChildMemory()
) {
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
    private var guessIndex = 0

    private data class GuessRound(val word: String, val clue: String, val options: List<String>, val imageKey: String)
    private val guessRounds = listOf(
        GuessRound("CAVALO", "Tenho crina, casco e faço pocotó.", listOf("CAVALO", "GATO", "CACHORRO"), "horse"),
        GuessRound("GATO", "Tenho bigodes e faço miau.", listOf("GATO", "CACHORRO", "SAPO"), "cat"),
        GuessRound("CACHORRO", "Eu latei: au au!", listOf("CACHORRO", "GALINHA", "PEIXE"), "dog"),
        GuessRound("SAPO", "Eu pulo e faço croc croc.", listOf("SAPO", "URSO", "MACACO"), "frog"),
        GuessRound("MORANGO", "Sou vermelho, docinho e tenho sementinhas.", listOf("MORANGO", "LARANJA", "COLHER"), "strawberry"),
        GuessRound("ESCOVA", "Eu ajudo a deixar os dentes limpinhos.", listOf("ESCOVA", "COPO", "BICICLETA"), "toothbrush"),
        GuessRound("BICICLETA", "Tenho duas rodas e pedalo para passear.", listOf("BICICLETA", "CARRO", "COLHER"), "bicycle"),
        GuessRound("PEIXE", "Eu moro na água e nado.", listOf("PEIXE", "GALINHA", "URSO"), "fish")
    )

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
            PlayMode.GUESS -> guessStart()
        }
    }

    fun reply(raw: String): ConversationReply {
        val original = raw.trim()
        val text = normalize(original)
        if (text.isBlank()) return guidedFallback()
        lastChildUtterance = original
        memory.rememberUtterance(original)

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

        handlePending(text, original)?.let { return it }

        personalMemoryReply(text, original)?.let { return it }

        // Pergunta concreta, uma etapa por vez. "Como foi a escola?" costuma
        // gerar respostas pouco específicas; esta sequência aceita também
        // respostas curtas como "sim", "ruim" e "nada".
        if (isSchoolTalk(text)) return schoolCheckIn()

        // Frases de conversa livre (escola, família, afeto e aprendizagem)
        // têm prioridade sobre atalhos genéricos, como "brincar", mas nunca
        // interrompem uma pergunta que já está em andamento.
        offlineBrain.reply(original)?.let {
            fallbackCount = 0
            return it
        }

        detectCommand(text)?.let {
            pending = PendingTurn.NONE
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
            c == "ADIVINHA" -> start(PlayMode.GUESS)
            c == "ANIMAIS" -> start(PlayMode.ANIMALS)
            c in setOf("ABC", "LETRAS") -> start(PlayMode.LETTERS)
            c == "HISTÓRIA" || c == "HISTORIA" -> start(PlayMode.STORY)
            c == "ROTINA" -> start(PlayMode.ROUTINE)
            c == "CARINHAS" -> start(PlayMode.FEELINGS)
            c == "IMAGENS" || c == "PALAVRAS" -> vocabularyBoard()
            c == "MÚSICA" || c == "MUSICA" -> localSong()
            c == "CONVERSAR" -> ConversationReply("Pode falar comigo. Eu estou te ouvindo.", RobotMood.CURIOUS, keepListening = true, choices = listOf("BRINCAR", "CARINHAS"))
            c == "INÍCIO" || c == "INICIO" -> start(PlayMode.HOME)
            c == "CHAMAR ADULTO" -> ConversationReply("Chama um adulto em quem você confia e fica pertinho dele, tá?", RobotMood.SAD, parentAlert = "A criança tocou em CHAMAR ADULTO após uma interação de proteção.")
            c == "BANHEIRO" -> bathroomPrompt()
            c == "ÁGUA" || c == "AGUA" -> waterPrompt()
            c == "PRÓXIMA" && mode == PlayMode.GUESS -> nextGuess()
            c == "DE NOVO" && mode == PlayMode.GUESS -> guessStart()
            mode == PlayMode.LETTERS && c.length == 1 -> letterChoice(c)
            mode == PlayMode.ANIMALS && c in setOf("CAVALO", "GATO", "CACHORRO") -> animalChoice(c)
            mode == PlayMode.GUESS && c in guessRounds[guessIndex].options -> guessChoice(c)
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
        containsAny(text, "papai chegou", "pai chegou", "papai chego", "pai chego", "meu pai chegou") -> ConversationReply("O papai chegou! Vai lá dar um abraço nele. Depois você pode voltar para me contar.", RobotMood.HAPPY, choices = listOf("INÍCIO", "BRINCAR"))
        containsAny(text, "onde voce", "cade voce", "cadê voce", "onde esta a fada") -> ConversationReply("Eu estou aqui na tela, pertinho de você. Toca em mim quando quiser conversar.", RobotMood.HAPPY, choices = listOf("BRINCAR", "MÚSICA", "IMAGENS"))
        containsAny(text, "dar mel", "mel pra fada", "mel para fada", "toma mel") -> ConversationReply("Que carinho! Obrigada pelo mel. Minha luz ficou ainda mais brilhante!", RobotMood.PROUD, scene = VisualScene.HAPPY_FACE, choices = listOf("BRINCAR", "MÚSICA", "IMAGENS"))
        containsAny(text, "quero brincar", "vamos brincar", "vamos de brincar", "brincar", "brinca", "jogar", "jogo", "quero jogo") -> start(PlayMode.PLAY)
        containsAny(text, "adivinha", "adivinhar", "faz adivinha", "quero adivinhar", "quem e", "quem é") -> start(PlayMode.GUESS)
        containsAny(text, "conta uma historia", "quero historia", "vamos de historia", "historia", "historinha") -> start(PlayMode.STORY)
        containsAny(text, "quero ver animal", "quero bicho", "bichinho", "animal", "animais", "cavalo", "gato", "cachorro") -> start(PlayMode.ANIMALS)
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
        "Oi, ${profile.name}! Quer brincar, ver figuras, ouvir uma musiquinha ou me contar uma coisa?",
        RobotMood.HAPPY,
        choices = listOf("ADIVINHA", "IMAGENS", "MÚSICA", "ANIMAIS", "ABC", "HISTÓRIA")
    )

    private fun vocabularyBoard() = ConversationReply(
        "Olha as figuras! Tem cavalo, gato, cachorro, sapo, galinha, urso, macaco, peixe, morango, laranja, escova, carro, bicicleta, coleguinha, copo e colher. Qual você quer falar?",
        RobotMood.SURPRISED,
        choices = listOf("ANIMAIS", "ABC", "BRINCAR", "MÚSICA", "CARINHAS", "INÍCIO")
    )

    private fun localSong() = ConversationReply(
        "Vou tocar a musiquinha da Lumi! Você pode bater palminhas bem devagar: um, dois, três.",
        RobotMood.HAPPY,
        choices = listOf("DE NOVO", "BRINCAR", "IMAGENS", "INÍCIO"),
        playTune = true
    )

    private fun playMenu() = ConversationReply(
        "Escolhe uma brincadeira! Quer adivinhar figuras, animais, letras ou uma historinha?",
        RobotMood.SURPRISED,
        choices = listOf("ADIVINHA", "ANIMAIS", "ABC", "HISTÓRIA", "CARINHAS", "INÍCIO")
    )

    private fun guessStart(): ConversationReply {
        mode = PlayMode.GUESS
        val round = guessRounds[guessIndex]
        return ConversationReply(
            "Jogo de adivinha! ${round.clue} Quem é?",
            RobotMood.CURIOUS,
            choices = round.options,
            imageKey = round.imageKey
        )
    }

    private fun guessChoice(choice: String): ConversationReply {
        val round = guessRounds[guessIndex]
        return if (choice == round.word) {
            ConversationReply("Acertou! É ${round.word.lowercase()}! Você foi muito bem.", RobotMood.PROUD, choices = listOf("PRÓXIMA", "DE NOVO", "INÍCIO"), imageKey = round.imageKey)
        } else {
            ConversationReply("Quase! Escuta a dica de novo: ${round.clue} Quem é?", RobotMood.CURIOUS, choices = round.options, imageKey = round.imageKey)
        }
    }

    private fun nextGuess(): ConversationReply {
        guessIndex = (guessIndex + 1) % guessRounds.size
        return guessStart()
    }

    /** Aprende preferências e nomes simples a partir de frases espontâneas. */
    private fun personalMemoryReply(text: String, original: String): ConversationReply? {
        val favoriteQuestion = containsAny(text, "do que eu gosto", "qual eu gosto", "meu favorito", "minha favorita")
        if (favoriteQuestion) {
            val favorite = memory.read("favorite")
            return if (favorite != null) ConversationReply("Eu lembro: você gosta de $favorite. Quer me contar mais uma coisa que você gosta?", RobotMood.PROUD, keepListening = true, choices = listOf("BRINCAR", "IMAGENS", "MÚSICA"))
            else ConversationReply("Ainda não sei. Você pode dizer: eu gosto de cavalo, música ou morango.", RobotMood.CURIOUS, keepListening = true, choices = listOf("CAVALO", "MORANGO", "MÚSICA"))
        }

        val favorite = Regex("(?:eu gosto de|eu adoro|meu favorito e|minha favorita e)\\s+(.+)").find(text)?.groupValues?.getOrNull(1)
        if (!favorite.isNullOrBlank() && !containsAny(favorite, "voce", "fadinha", "lumi")) {
            memory.save("favorite", favorite)
            return ConversationReply("Vou lembrar que você gosta de $favorite. Que legal! Quer brincar com isso ou me contar mais?", RobotMood.PROUD, keepListening = true, choices = listOf("BRINCAR", "IMAGENS", "CONVERSAR"))
        }

        val friend = Regex("(?:meu amigo chama|minha amiga chama|meu coleguinha chama|minha coleguinha chama)\\s+(.+)").find(text)?.groupValues?.getOrNull(1)
        if (!friend.isNullOrBlank()) {
            memory.save("friend", friend)
            return ConversationReply("Vou lembrar que ${friend} é seu coleguinha. O que vocês fizeram juntos?", RobotMood.HAPPY, keepListening = true, choices = listOf("BRINQUEI", "CONVERSEI", "QUERO CONTAR"))
        }

        if (containsAny(text, "nome do meu amigo", "nome da minha amiga", "meu coleguinha")) {
            memory.read("friend")?.let { return ConversationReply("Eu lembro que seu coleguinha chama $it.", RobotMood.HAPPY, choices = listOf("BRINCAR", "IMAGENS", "INÍCIO")) }
        }
        return null
    }

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
        PendingTurn.SCHOOL_HAPPENED -> when {
            isYes(text) -> schoolKindQuestion()
            isNo(text) || isNothing(text) -> {
                pending = PendingTurn.NONE
                ConversationReply("Tudo bem. Podemos brincar juntas agora?", RobotMood.HAPPY, choices = listOf("BRINCAR", "HISTÓRIA", "ANIMAIS"))
            }
            isBad(text) -> schoolSafetyQuestion()
            else -> ConversationReply("Na escola aconteceu alguma coisa? Pode tocar no sim ou no não.", RobotMood.CURIOUS, choices = listOf("SIM", "NÃO"))
        }
        PendingTurn.SCHOOL_KIND -> when {
            isBad(text) -> schoolSafetyQuestion()
            isGood(text) -> {
                pending = PendingTurn.NONE
                ConversationReply("Que bom! Você quer me contar a coisa boa ou brincar agora?", RobotMood.HAPPY, choices = listOf("CONTAR", "BRINCAR", "HISTÓRIA"))
            }
            isNothing(text) || isNo(text) -> {
                pending = PendingTurn.NONE
                ConversationReply("Tudo bem. Vamos escolher uma brincadeira tranquila?", RobotMood.HAPPY, choices = listOf("BRINCAR", "ANIMAIS", "HISTÓRIA"))
            }
            else -> ConversationReply("Foi uma coisa boa, ruim ou nada?", RobotMood.CURIOUS, choices = listOf("BOA", "RUIM", "NADA"))
        }
        PendingTurn.SCHOOL_SAFETY -> when {
            isYes(text) -> {
                pending = PendingTurn.NONE
                ConversationReply("Obrigada por contar. Fica perto de um adulto em quem você confia agora.", RobotMood.SAD, choices = listOf("CHAMAR ADULTO", "INÍCIO"), parentAlert = "${profile.name} disse que algo ruim aconteceu na escola e indicou que houve medo ou machucado.")
            }
            isNo(text) -> {
                pending = PendingTurn.NONE
                ConversationReply("Entendi. Mesmo assim, você pode contar para um adulto em quem confia quando quiser.", RobotMood.CURIOUS, choices = listOf("CHAMAR ADULTO", "BRINCAR", "INÍCIO"), parentAlert = "${profile.name} disse que algo ruim aconteceu na escola.")
            }
            else -> ConversationReply("Alguém machucou ou assustou você?", RobotMood.SAD, choices = listOf("SIM", "NÃO"))
        }
        PendingTurn.STORY_CHOICE -> storyChoice(original.uppercase(Locale.forLanguageTag("pt-BR")))
    }

    private fun schoolCheckIn(): ConversationReply {
        pending = PendingTurn.SCHOOL_HAPPENED
        return ConversationReply("Na escola aconteceu alguma coisa?", RobotMood.CURIOUS, keepListening = true, choices = listOf("SIM", "NÃO"))
    }

    private fun schoolKindQuestion(): ConversationReply {
        pending = PendingTurn.SCHOOL_KIND
        return ConversationReply("Foi uma coisa boa, ruim ou nada?", RobotMood.CURIOUS, keepListening = true, choices = listOf("BOA", "RUIM", "NADA"))
    }

    private fun schoolSafetyQuestion(): ConversationReply {
        pending = PendingTurn.SCHOOL_SAFETY
        return ConversationReply("Entendi. Alguém machucou ou assustou você?", RobotMood.SAD, keepListening = true, choices = listOf("SIM", "NÃO"), parentAlert = "${profile.name} disse que aconteceu algo ruim na escola.")
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
    private fun isGood(text: String) = containsAny(text, "boa", "bom", "legal", "feliz", "gostei")
    private fun isBad(text: String) = containsAny(text, "ruim", "ruin", "mal", "chato", "triste", "medo")
    private fun isNothing(text: String) = containsAny(text, "nada", "nenhuma coisa")
    private fun isSchoolTalk(text: String) = containsAny(text, "escola", "professora", "professor", "coleguinha", "coleguinho", "amiguinha", "amiguinho")
    private fun containsAny(text: String, vararg terms: String) = terms.any { text.contains(normalize(it)) }
    private fun normalize(value: String): String {
        val n = Normalizer.normalize(value.lowercase(Locale.forLanguageTag("pt-BR")), Normalizer.Form.NFD)
        return n.replace("\\p{Mn}+".toRegex(), "").replace("[^a-z0-9 ]".toRegex(), " ").replace("\\s+".toRegex(), " ").trim()
    }
}
