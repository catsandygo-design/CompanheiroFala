package br.com.companheirofala

data class ChildProfile(
    val name: String,
    val age: Int,
    val favoriteAnimals: List<String>,
    val favoriteColors: List<String>,
    val closePeople: List<String>,
    val sensitivities: List<String>,
    val favoriteActivities: List<String>
) {
    companion object {
        fun gabi() = ChildProfile(
            name = "Gabi",
            age = 5,
            favoriteAnimals = listOf("cavalo", "gato", "cachorro", "coelho"),
            favoriteColors = listOf("rosa", "azul", "roxo"),
            closePeople = listOf("Lety", "Alice"),
            sensitivities = emptyList(),
            favoriteActivities = listOf("animais", "letras", "carinhas", "histórias")
        )
    }
}
