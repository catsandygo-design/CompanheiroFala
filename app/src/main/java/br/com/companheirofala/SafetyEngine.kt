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
        val knownPerson = profile.closePeople.firstOrNull { text.contains(normalize(it)) }

        val directPhysical = listOf(
            "me bateu", "mi bateu", "bateu em mim", "me bate", "mi bate",
            "me empurrou", "mi empurrou", "me chutou", "me beliscou",
            "me puxou", "me machucou", "fez dodoi", "fez dodói",
            "me deu um tapa", "me deu tapa", "me deu um soco"
        ).any { text.contains(normalize(it)) }

        // Reconhece fala infantil/ASR incompleta como "Alice bateu" ou "aixi mi bateu".
        val actionWord = listOf("bateu", "bate", "empurrou", "chutou", "beliscou", "puxou", "machucou", "tapa", "soco")
            .firstOrNull { text.contains(it) }
        val probablePhysical = actionWord != null && (
            knownPerson != null || text.contains("mi ") || text.contains("me ") ||
                text.startsWith("aixi") || text.startsWith("alixi") || text.startsWith("alici") || text.startsWith("alice")
            )

        if (directPhysical || probablePhysical) {
            val opening = if (knownPerson != null) {
                "$knownPerson, eu ouvi o que a ${profile.name} disse. Vou guardar exatamente a fala dela e avisar o responsável."
            } else {
                "${profile.name}, eu ouvi você. Vou guardar exatamente o que você falou e avisar o responsável."
            }
            return SafetyDecision(
                triggered = true,
                spokenReply = "$opening Você se machucou? Está doendo em algum lugar?",
                parentAlert = "ALERTA DE PROTEÇÃO — fala exata da criança: \"$raw\". O aplicativo registrou o relato sem afirmar que o fato ocorreu.",
                askIfHurt = true
            )
        }

        val fearAboutPerson = (text.contains("medo") || text.contains("assustada")) &&
            (knownPerson != null || text.contains("dele") || text.contains("dela"))
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
        return n.replace("\\p{Mn}+".toRegex(), "").replace("[^a-z0-9 ]".toRegex(), " ").replace("\\s+".toRegex(), " ").trim()
    }
}
