package br.com.companheirofala.core.conversation

import java.text.Normalizer
import java.util.Locale

data class NormalizedSpeech(val rawTranscript: String, val normalizedTranscript: String, val replacements: List<String>)

class SpeechTextNormalizer(private val aliases: Map<String, String> = defaultAliases) {
    fun normalize(raw: String): NormalizedSpeech {
        var text = fold(raw)
        val replacements = mutableListOf<String>()
        aliases.forEach { (from, to) -> if (text.contains(from)) { text = text.replace(from, to); replacements += "$from:$to" } }
        return NormalizedSpeech(raw.trim(), text.replace(Regex("\\s+"), " ").trim(), replacements)
    }
    private fun fold(value: String): String = Normalizer.normalize(value.lowercase(Locale.forLanguageTag("pt-BR")), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
    companion object {
        val defaultAliases = linkedMapOf("municornio" to "unicórnio", "unicornio" to "unicórnio", "cavau" to "cavalo", "quelo" to "quero", "aixi" to "alice", "alixi" to "alice", "ruin" to "ruim")
    }
}
