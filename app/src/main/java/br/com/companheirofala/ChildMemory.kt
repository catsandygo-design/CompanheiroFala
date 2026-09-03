package br.com.companheirofala

import android.content.Context
import java.text.Normalizer
import java.util.Locale

/** Memória local da conversa. Não envia a fala da criança para nenhum servidor. */
interface ChildMemory {
    fun save(key: String, value: String)
    fun read(key: String): String?
    fun rememberUtterance(value: String)
}

class SessionChildMemory : ChildMemory {
    private val facts = mutableMapOf<String, String>()
    override fun save(key: String, value: String) { facts[key] = value }
    override fun read(key: String) = facts[key]
    override fun rememberUtterance(value: String) { facts["last_utterance"] = value }
}

class LocalChildMemory(context: Context) : ChildMemory {
    private val prefs = context.getSharedPreferences("lumi_child_memory", Context.MODE_PRIVATE)
    override fun save(key: String, value: String) = prefs.edit().putString(key, value.take(80)).apply()
    override fun read(key: String) = prefs.getString(key, null)
    override fun rememberUtterance(value: String) = save("last_utterance", value)
}

fun memoryKey(value: String): String {
    val normalized = Normalizer.normalize(value.lowercase(Locale.forLanguageTag("pt-BR")), Normalizer.Form.NFD)
    return normalized.replace("\\p{Mn}+".toRegex(), "").replace("[^a-z0-9]".toRegex(), "_")
}
