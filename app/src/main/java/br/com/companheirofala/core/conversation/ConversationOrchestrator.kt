package br.com.companheirofala.core.conversation

import android.content.Context
import br.com.companheirofala.ChildProfile
import br.com.companheirofala.ConversationEngine
import br.com.companheirofala.ConversationReply
import br.com.companheirofala.RobotMood
import br.com.companheirofala.VisualScene
import br.com.companheirofala.core.ai.LocalLLMProvider
import br.com.companheirofala.core.ai.LlamaCppLocalProvider
import br.com.companheirofala.core.ai.RemoteLLMProvider
import br.com.companheirofala.core.ai.VercelRemoteLLMProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import br.com.companheirofala.core.safety.ChildSafetyEngine
import br.com.companheirofala.core.safety.CurrentEnvironment
import br.com.companheirofala.core.safety.LocalNotificationService
import br.com.companheirofala.core.safety.NotificationService
import br.com.companheirofala.core.safety.SafetyEvent
import br.com.companheirofala.core.safety.SafetyEventRepository
import br.com.companheirofala.core.safety.TrustedPeopleRepository

/** Centraliza contexto e prioridade; ConversationEngine fica como adaptador temporário dos fluxos visuais. */
class ConversationOrchestrator(
    context: Context,
    private val legacy: ConversationEngine = ConversationEngine(ChildProfile.gabi()),
    private val localLlm: LocalLLMProvider = LlamaCppLocalProvider(context),
    private val remoteLlm: RemoteLLMProvider = VercelRemoteLLMProvider(),
    private val safety: ChildSafetyEngine = ChildSafetyEngine(),
    private val notificationService: NotificationService = LocalNotificationService()
) {
    private val stateManager = ConversationStateManager(ConversationStateRepository(context))
    private val normalizer = SpeechTextNormalizer()
    private val router = IntentRouter()
    private val fallback = FallbackHandler()
    private val loopGuard = LoopGuard()
    private val events = SafetyEventRepository(context)
    private val trustedPeople = TrustedPeopleRepository(context)
    private val diagnostics = AiDiagnostics()
    val state: ConversationState get() = stateManager.state
    fun diagnosticsText() = diagnostics.text()

    fun start(): ConversationReply = recordAssistant(legacy.start())

    fun onChoice(choice: String): ConversationReply = recordAssistant(legacy.onChoice(choice)).also { reply ->
        stateManager.update { state -> state.copy(gameState = GameState(if (reply.choices.any { it == "CAVALO" }) "animal" else null, null)) }
    }

    fun onMovementDetected(): ConversationReply = recordAssistant(legacy.onMovementDetected())

    suspend fun loadLocalModel(): Boolean = withContext(Dispatchers.Default) { localLlm.loadModel() }

    /** Diagnóstico direto: não passa por STT, roteador ou fallback. */
    suspend fun testLocalAi(prompt: String): LLMResult = withContext(Dispatchers.Default) {
        localLlm.generate(prompt, state)
    }

    fun unloadLocalModel() = localLlm.unloadModel()

    suspend fun reply(raw: String): ConversationReply {
        stateManager.expireIfNeeded()
        val normalized = normalizer.normalize(raw)
        val local = localLlm as? LlamaCppLocalProvider
        val turn = diagnostics.begin(raw, normalized.normalizedTranscript, state.pendingQuestion, localLlm.isAvailable(), localLlm.isAvailable(), local?.modelName() ?: "Desconhecido")
        if (normalized.normalizedTranscript.isBlank()) return finish(turn, "FALLBACK", fallbackReply(), true)
        stateManager.addMessage(ConversationMessage(normalized.rawTranscript, normalized.normalizedTranscript, true))

        // Segurança sempre interrompe jogo, rotina e conversa livre.
        safety.inspect(normalized.normalizedTranscript)?.let { assessment -> return finish(turn, "SAFETY", beginSafety(normalized, assessment.category, assessment.severity, assessment.firstQuestion)) }
        handlePending(normalized)?.let { return finish(turn, "PENDING", it) }
        handleGameAnswer(normalized)?.let { return finish(turn, "GAME", it) }

        val intent = router.route(normalized.normalizedTranscript, state)
        stateManager.update { it.copy(currentIntent = intent) }
        when (intent) {
            IntentType.TOILET, IntentType.WATER, IntentType.FOOD, IntentType.SLEEP, IntentType.HYGIENE -> return finish(turn, "ROUTINE", adaptLegacy(normalized.normalizedTranscript, intent))
            IntentType.GAME, IntentType.ANIMAL, IntentType.LETTER_GAME, IntentType.COLOR_GAME, IntentType.STORY -> return finish(turn, "GAME", adaptLegacy(normalized.normalizedTranscript, intent))
            IntentType.EMOTION -> return finish(turn, "EMOTION", adaptLegacy(normalized.normalizedTranscript, intent))
            IntentType.GENERAL_CHAT -> {
                val reply = generateWithProviders(normalized.normalizedTranscript, turn)
                return reply
            }
            else -> Unit
        }
        // Providers são deliberadamente opcionais: o app não aguarda rede nem modelo para ser útil.
        return finish(turn, "FALLBACK", fallbackReply(), true)
    }

    private suspend fun generateWithProviders(text: String, turn: AiDiagnosticTurn): ConversationReply {
        // O GGUF/JNI permanece disponível apenas para teste offline explícito. A conversa normal
        // nunca tenta carregá-lo nem executa inferência nativa no processo do aplicativo.
        val prompt = remotePrompt(text)
        turn.route = "REMOTE_LLM"; turn.prompt = prompt
        val remote = if (remoteLlm.isEnabled()) withContext(Dispatchers.IO) { remoteLlm.generate(prompt, state) } else null
        if (remote != null) { turn.llmCalled = remote.invoked; turn.llmResponse = remote.text; turn.latencyMs = remote.latencyMs; turn.error = remote.error.orEmpty() }
        if (remote?.success == true && remote.confidence >= .5f && remote.text.isNotBlank()) return finish(turn, "REMOTE_LLM", recordAssistant(ConversationReply(remote.text, RobotMood.HAPPY, choices = listOf("CONVERSAR", "BRINCAR", "INÍCIO"))))
        return finish(turn, "FALLBACK", fallbackReply(), true)
    }

    private fun finish(turn: AiDiagnosticTurn, route: String, reply: ConversationReply, fallbackUsed: Boolean = false): ConversationReply { turn.route = route; turn.fallbackUsed = fallbackUsed; diagnostics.complete(turn, reply.text); return reply }

    private fun remotePrompt(userText: String): String {
        val history = state.recentMessages.takeLast(6).joinToString("\n") { message ->
            "${if (message.fromChild) "Criança" else "Lumi"}: ${message.text}"
        }
        return """
            Você é Lumi, uma companheira carinhosa para uma criança de cinco anos.
            Responda em português brasileiro, com segurança e em uma frase curta.
            Conversa recente:
            $history
            Criança: $userText
            Lumi:
        """.trimIndent()
    }

    private fun beginSafety(speech: NormalizedSpeech, category: br.com.companheirofala.core.safety.SafetyCategory, severity: br.com.companheirofala.core.safety.SafetySeverity, question: String): ConversationReply {
        val event = SafetyEvent(category = category, transcript = speech.rawTranscript, environment = state.currentEnvironment, severity = severity, actionsTaken = listOf("recorded", "asked_minimum_question"))
        events.record(event)
        stateManager.update { it.copy(currentIntent = IntentType.SAFETY, currentTopic = "safety", pendingQuestion = if (category == br.com.companheirofala.core.safety.SafetyCategory.AGGRESSION || category == br.com.companheirofala.core.safety.SafetyCategory.PAIN) PendingQuestion.SAFETY_BODY_PART else PendingQuestion.SAFETY_ENVIRONMENT, safetyState = SafetyState(category, speech.rawTranscript), gameState = GameState()) }
        return recordAssistant(ConversationReply(question, RobotMood.SAD, true, VisualScene.SAD_FACE, listOf("BRAÇO", "PERNA", "ESCOLA", "CASA"), parentAlert = "Evento de segurança ${category.name}: ${speech.rawTranscript}"))
    }

    private fun handlePending(speech: NormalizedSpeech): ConversationReply? = when (state.pendingQuestion) {
        PendingQuestion.SAFETY_BODY_PART -> {
            stateManager.update { it.copy(pendingQuestion = PendingQuestion.SAFETY_ENVIRONMENT, safetyState = it.safetyState.copy(bodyPart = speech.rawTranscript)) }
            recordAssistant(ConversationReply("Obrigada por me contar. Você está na escola ou em casa?", RobotMood.SAD, true, VisualScene.SAD_FACE, listOf("ESCOLA", "CASA")))
        }
        PendingQuestion.SAFETY_ENVIRONMENT, PendingQuestion.ENVIRONMENT -> {
            val environment = environmentOf(speech.normalizedTranscript) ?: return recordAssistant(ConversationReply("Você está na escola ou em casa?", RobotMood.CURIOUS, true, choices = listOf("ESCOLA", "CASA")))
            stateManager.update { it.copy(currentEnvironment = environment, pendingQuestion = PendingQuestion.NONE) }
            val person = safety.trustedPerson(environment, trustedPeople.people())
            val message = if (person != null) "Você consegue chamar ${person.name}, ${person.relationship}? Fica pertinho dela." else "Eu guardei sua fala. Quando puder, chame uma pessoa que seu responsável cadastrou para ajudar você."
            recordAssistant(ConversationReply(message, RobotMood.SAD, choices = listOf("INÍCIO", "CONVERSAR")))
        }
        else -> null
    }

    private fun handleGameAnswer(speech: NormalizedSpeech): ConversationReply? {
        val game = state.gameState.activeGame ?: return null
        if (game == "animal" && speech.normalizedTranscript.contains("cavalo")) return recordAssistant(legacy.onChoice("CAVALO"))
        return null
    }

    private fun adaptLegacy(text: String, intent: IntentType): ConversationReply {
        val reply = legacy.reply(text)
        val game = when {
            reply.choices.any { it == "CAVALO" } -> "animal"
            reply.choices.any { it in setOf("A", "C", "P") } -> "letters"
            else -> null
        }
        val routine = when (intent) { IntentType.TOILET -> "toilet"; IntentType.WATER -> "water"; IntentType.FOOD -> "food"; IntentType.HYGIENE -> "hygiene"; else -> null }
        stateManager.update { it.copy(currentTopic = if (game == "animal") "animal" else it.currentTopic, gameState = GameState(game, if (game != null) "answer" else null), routineState = RoutineState(routine)) }
        return recordAssistant(reply)
    }

    private fun fallbackReply(): ConversationReply = recordAssistant(ConversationReply(fallback.reply(state), RobotMood.CURIOUS, choices = listOf("BRINCAR", "ANIMAIS", "INÍCIO")))
    private fun recordAssistant(reply: ConversationReply): ConversationReply {
        stateManager.addMessage(ConversationMessage(reply.text, reply.text, false))
        if (loopGuard.record(reply.text)) { stateManager.clearSuspectContext(); loopGuard.reset(); return ConversationReply("Vamos parar um pouquinho e escolher outra coisa juntas.", RobotMood.HAPPY, choices = listOf("ANIMAIS", "HISTÓRIA", "INÍCIO")) }
        return reply
    }
    private fun environmentOf(text: String) = when { text.contains("escola") -> CurrentEnvironment.SCHOOL; text.contains("casa") -> CurrentEnvironment.HOME; text.contains("fora") -> CurrentEnvironment.OUTSIDE; else -> null }
}
