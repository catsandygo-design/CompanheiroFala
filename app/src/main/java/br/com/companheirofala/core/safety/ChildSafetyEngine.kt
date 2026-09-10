package br.com.companheirofala.core.safety

import java.util.UUID

enum class SafetyCategory { AGGRESSION, FEAR, INJURY, LOST, UNKNOWN_PERSON, ABUSE_DISCLOSURE, ALONE, EMERGENCY, PAIN, SADNESS }
enum class CurrentEnvironment { UNKNOWN, HOME, SCHOOL, OUTSIDE, RELATIVE_HOME }
enum class SafetySeverity { LOW, MEDIUM, HIGH }

data class TrustedPerson(val id: String, val name: String, val relationship: String, val allowedEnvironments: Set<CurrentEnvironment>, val priority: Int, val phone: String = "", val notificationEnabled: Boolean = false)
data class SafetyAssessment(val category: SafetyCategory, val severity: SafetySeverity, val firstQuestion: String)
data class SafetyEvent(val id: String = UUID.randomUUID().toString(), val timestamp: Long = System.currentTimeMillis(), val category: SafetyCategory, val transcript: String, val environment: CurrentEnvironment, val severity: SafetySeverity, val actionsTaken: List<String>, val notificationSent: Boolean = false)

class ChildSafetyEngine {
    fun inspect(text: String): SafetyAssessment? = when {
        contains(text, "me bateu", "bateu", "empurrou", "chutou", "beliscou", "machucou", "tapa", "soco") -> SafetyAssessment(SafetyCategory.AGGRESSION, SafetySeverity.HIGH, "Entendi. Onde ela bateu?")
        contains(text, "medo", "assustada", "assustado") -> SafetyAssessment(SafetyCategory.FEAR, SafetySeverity.MEDIUM, "Eu estou aqui com você. Você está em casa ou na escola?")
        contains(text, "doendo", "doi", "dor", "barriga doi") -> SafetyAssessment(SafetyCategory.PAIN, SafetySeverity.MEDIUM, "Onde está doendo?")
        contains(text, "me perdi", "nao sei onde", "cadê minha mae", "cade minha mae") -> SafetyAssessment(SafetyCategory.LOST, SafetySeverity.HIGH, "Você está em um lugar seguro agora?")
        contains(text, "homem aqui", "pessoa estranha", "desconhecido") -> SafetyAssessment(SafetyCategory.UNKNOWN_PERSON, SafetySeverity.HIGH, "Você conhece essa pessoa?")
        contains(text, "sozinha", "sozinho") -> SafetyAssessment(SafetyCategory.ALONE, SafetySeverity.HIGH, "Você está em casa, na escola ou fora?")
        contains(text, "socorro", "emergencia", "emergência") -> SafetyAssessment(SafetyCategory.EMERGENCY, SafetySeverity.HIGH, "Eu vou avisar seu responsável. Você está em casa, na escola ou fora?")
        contains(text, "triste", "chorando") -> SafetyAssessment(SafetyCategory.SADNESS, SafetySeverity.LOW, "Sinto muito. Quer me contar o que aconteceu?")
        else -> null
    }
    fun trustedPerson(environment: CurrentEnvironment, people: List<TrustedPerson>): TrustedPerson? = people.filter { environment in it.allowedEnvironments }.minByOrNull { it.priority }
    private fun contains(text: String, vararg terms: String) = terms.any(text::contains)
}
