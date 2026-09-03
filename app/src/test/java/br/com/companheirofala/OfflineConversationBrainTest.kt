package br.com.companheirofala

import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineConversationBrainTest {
    private val engine = ConversationEngine(ChildProfile.gabi())

    @Test
    fun respondsWarmlyToAffectionWithoutNetwork() {
        val reply = engine.reply("Eu gosto de você, fadinha")

        assertTrue(reply.text.contains("carinho"))
        assertTrue(reply.keepListening)
    }

    @Test
    fun keepsSchoolConversationInContext() {
        val reply = engine.reply("Hoje brinquei com minha amiga na escola")

        assertTrue(reply.text.contains("escola"))
        assertTrue(reply.choices.contains("BRINCADEIRA"))
    }

    @Test
    fun answersSimpleLearningQuestionLocally() {
        val reply = engine.reply("Por que chove?")

        assertTrue(reply.text.contains("nuvens"))
    }

    @Test
    fun safetyReportAlwaysWinsOverLocalConversation() {
        val reply = engine.reply("Minha mãe me bateu")

        assertTrue(reply.parentAlert?.contains("ALERTA DE PROTEÇÃO") == true)
    }
}
