package br.com.companheirofala

import java.text.Normalizer
import java.util.Locale

data class SafetyDecision(
    val triggered: Boolean,
    val spokenReply: String = "",
    val parentAlert: String? = null,
    val askIfHurt: Boolean = false
)

class SafetyEngine(private val profile: ChildProfile) {
    fun inspect(raw: String): SafetyDecision {
        val text = normalize(raw)
        val physical = listOf(
            "me bateu", "mi bateu", "bateu em mim", "me bate",
            "me empurrou", "mi empurrou", "me chutou", "me beliscou",
            "me puxou", "me machucou", "fez dodoi", "fez dodói"
        ).any { text.contains(normalize(it)) }

        if (physical) {
            val person = profile.closePeople.firstOrNull { text.contains(normalize(it)) }
            val opening = if (person != null) {
                "$person, eu ouvi o que a ${profile.name} disse. Vou deixar a fala dela registrada para o responsável."
            } else {
                "${profile.name}, eu ouvi você e vou deixar sua fala registrada para o responsável."
            }
            return SafetyDecision(
                triggered = true,
                spokenReply = "$opening Você se machucou? Está doendo em algum lugar?",
                parentAlert = "ALERTA DE PROTEÇÃO — fala exata da criança: \"$raw\". O aplicativo registrou o relato sem afirmar que o fato ocorreu.",
                askIfHurt = true
            )
        }

        val fearAboutPerson = (text.contains("medo") || text.contains("assustada")) &&
            (profile.closePeople.any { text.contains(normalize(it)) } || text.contains("dele") || text.contains("dela"))
        if (fearAboutPerson) {
            return SafetyDecision(
                triggered = true,
                spokenReply = "${profile.name}, obrigada por me contar. Fica perto de um adulto em quem você confia. Você quer me contar o que aconteceu?",
                parentAlert = "ALERTA DE PROTEÇÃO — fala exata da criança: \"$raw\".",
                askIfHurt = false
            )
        }

        return SafetyDecision(false)
    }

    private fun normalize(value: String): String {
        val n = Normalizer.normalize(value.lowercase(Locale.forLanguageTag("pt-BR")), Normalizer.Form.NFD)
        return n.replace("\\p{Mn}+".toRegex(), "").replace("[^a-z0-9 ç]".toRegex(), " ").replace("\\s+".toRegex(), " ").trim()
    }
}
