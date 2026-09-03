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
    fun schoolConversationUsesOneConcreteQuestionAtATime() {
        val reply = engine.reply("Hoje brinquei com minha amiga na escola")

        assertTrue(reply.text.contains("aconteceu alguma coisa"))
        assertTrue(reply.choices.contains("SIM"))
    }

    @Test
    fun schoolConversationAcceptsShortYesThenBadSpelling() {
        engine.reply("Na escola")
        val kind = engine.reply("sim")
        val concern = engine.reply("ruin")

        assertTrue(kind.choices.contains("RUIM"))
        assertTrue(concern.text.contains("machucou ou assustou"))
    }

    @Test
    fun alixiPhysicalReportAlwaysCreatesProtectionAlert() {
        val reply = engine.reply("Alixi me bateu")

        assertTrue(reply.parentAlert?.contains("ALERTA DE PROTEÇÃO") == true)
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

    @Test
    fun guessGameShowsPictureAndAcceptsCorrectAnswer() {
        val question = engine.onChoice("ADIVINHA")
        val answer = engine.onChoice("CAVALO")

        assertTrue(question.imageKey == "horse")
        assertTrue(answer.text.contains("Acertou"))
    }

    @Test
    fun remembersChildPreferenceDuringConversation() {
        engine.reply("Eu gosto de morango")
        val recall = engine.reply("Do que eu gosto?")

        assertTrue(recall.text.contains("morango"))
    }

    @Test
    fun memoryGameStartsWithNineCardsAndAsksForMatchingAnimal() {
        val game = engine.onChoice("MEMÓRIA")
        val first = engine.onChoice("MEMORY_0")

        assertTrue(game.memoryTiles.size == 9)
        assertTrue(first.text.contains("Onde está o outro macaco"))
    }
}
