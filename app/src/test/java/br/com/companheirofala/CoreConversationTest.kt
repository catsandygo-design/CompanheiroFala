package br.com.companheirofala

import br.com.companheirofala.core.conversation.LoopGuard
import br.com.companheirofala.core.conversation.IntentRouter
import br.com.companheirofala.core.conversation.IntentType
import br.com.companheirofala.core.conversation.ConversationState
import br.com.companheirofala.core.conversation.SpeechTextNormalizer
import br.com.companheirofala.core.safety.ChildSafetyEngine
import br.com.companheirofala.core.safety.SafetyCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreConversationTest {
    @Test fun normalizesChildSpeechWithoutChangingRawTranscript() {
        val result = SpeechTextNormalizer().normalize("Municornio e cavau")
        assertEquals("Municornio e cavau", result.rawTranscript)
        assertTrue(result.normalizedTranscript.contains("unicórnio"))
        assertTrue(result.normalizedTranscript.contains("cavalo"))
    }

    @Test fun aggressionHasDeterministicPriorityCategory() {
        assertEquals(SafetyCategory.AGGRESSION, ChildSafetyEngine().inspect("Alice bateu")?.category)
    }

    @Test fun fearInterruptsAnyOtherContext() {
        assertEquals(SafetyCategory.FEAR, ChildSafetyEngine().inspect("estou com medo")?.category)
    }

    @Test fun loopGuardOnlyTripsOnThirdEqualReply() {
        val guard = LoopGuard()
        assertFalse(guard.record("não entendi")); assertFalse(guard.record("não entendi")); assertTrue(guard.record("não entendi"))
    }

    @Test fun openChildConversationUsesRemoteChatRoute() {
        assertEquals(IntentType.GENERAL_CHAT, IntentRouter().route("mamãe brigou comigo", ConversationState()))
        assertEquals(IntentType.GENERAL_CHAT, IntentRouter().route("o cavalo parece um jacaré", ConversationState()))
    }
}
