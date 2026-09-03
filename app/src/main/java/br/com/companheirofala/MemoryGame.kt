package br.com.companheirofala

data class MemoryTile(val imageKey: String, val revealed: Boolean = false, val matched: Boolean = false)

data class MemoryMove(val tiles: List<MemoryTile>, val message: String, val complete: Boolean = false)

/** Jogo local 3x3: quatro pares de bichinhos e uma estrela bônus da Lumi. */
class MemoryGame {
    private val animals = listOf("monkey", "cat", "dog", "bear", "monkey", "cat", "dog", "bear", "fairy")
    private val tiles = animals.map { MemoryTile(it) }.toMutableList()
    private var firstIndex: Int? = null
    private var lock = false

    fun start() = MemoryMove(tiles.toList(), "Jogo da memória! Toque em um bichinho para virar a carta.")

    fun tap(index: Int): MemoryMove {
        if (index !in tiles.indices || lock || tiles[index].matched || tiles[index].revealed) return MemoryMove(tiles.toList(), "Escolha um bichinho que ainda está escondido.")
        if (tiles[index].imageKey == "fairy") {
            tiles[index] = tiles[index].copy(revealed = true, matched = true)
            return finishOrContinue("Você encontrou a estrela da Lumi! Agora procure os pares.")
        }
        val first = firstIndex
        if (first == null) {
            tiles[index] = tiles[index].copy(revealed = true)
            firstIndex = index
            return MemoryMove(tiles.toList(), "Você achou ${nameOf(tiles[index].imageKey)}. Onde está o outro ${nameOf(tiles[index].imageKey)}?")
        }

        tiles[index] = tiles[index].copy(revealed = true)
        return if (tiles[first].imageKey == tiles[index].imageKey) {
            tiles[first] = tiles[first].copy(matched = true)
            tiles[index] = tiles[index].copy(matched = true)
            firstIndex = null
            finishOrContinue("Acertou o par de ${nameOf(tiles[index].imageKey)}! Muito bem!")
        } else {
            tiles[first] = tiles[first].copy(revealed = false)
            tiles[index] = tiles[index].copy(revealed = false)
            firstIndex = null
            MemoryMove(tiles.toList(), "Esses bichinhos são diferentes. Tenta outro lugar.")
        }
    }

    private fun finishOrContinue(message: String): MemoryMove {
        val complete = tiles.all { it.matched || it.imageKey == "fairy" }
        return MemoryMove(tiles.toList(), if (complete) "Você encontrou todos os pares! Parabéns, Gabi!" else message, complete)
    }

    private fun nameOf(key: String) = when (key) {
        "monkey" -> "macaco"; "cat" -> "gato"; "dog" -> "cachorro"; "bear" -> "urso"; else -> "bichinho"
    }
}
