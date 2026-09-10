package br.com.companheirofala.core.conversation

import br.com.companheirofala.core.safety.CurrentEnvironment
import br.com.companheirofala.core.safety.SafetyCategory
import java.util.UUID

enum class IntentType { TOILET, WATER, FOOD, SLEEP, HYGIENE, EMOTION, SAFETY, GAME, ANIMAL, LETTER_GAME, COLOR_GAME, STORY, GENERAL_CHAT, UNKNOWN }
enum class PendingQuestion { NONE, SAFETY_BODY_PART, SAFETY_ENVIRONMENT, ENVIRONMENT, GAME_ANSWER, STORY_CHOICE }
enum class LLMProvider { LOCAL, REMOTE, NONE }

data class ConversationMessage(val text: String, val normalizedText: String, val fromChild: Boolean, val timestamp: Long = System.currentTimeMillis())
data class GameState(val activeGame: String? = null, val pendingAnswer: String? = null)
data class RoutineState(val activeRoutine: String? = null)
data class SafetyState(val category: SafetyCategory? = null, val transcript: String? = null, val bodyPart: String? = null)

data class ConversationState(
    val sessionId: String = UUID.randomUUID().toString(),
    val recentMessages: List<ConversationMessage> = emptyList(),
    val currentTopic: String? = null,
    val currentIntent: IntentType = IntentType.UNKNOWN,
    val pendingQuestion: PendingQuestion = PendingQuestion.NONE,
    val currentEnvironment: CurrentEnvironment = CurrentEnvironment.UNKNOWN,
    val safetyState: SafetyState = SafetyState(),
    val gameState: GameState = GameState(),
    val routineState: RoutineState = RoutineState(),
    val emotionState: String? = null,
    val lastUserUtterance: String? = null,
    val lastAssistantResponse: String? = null,
    val lastInteractionAt: Long = System.currentTimeMillis()
)

data class LLMResult(val text: String, val confidence: Float, val provider: LLMProvider, val latencyMs: Long, val success: Boolean, val error: String? = null, val invoked: Boolean = false)
data class OrchestratedReply(val text: String, val keepListening: Boolean = false, val choices: List<String> = emptyList(), val parentAlert: String? = null)
