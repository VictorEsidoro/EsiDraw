package io.github.victoresidoro.esidraw.domain.engine

import kotlin.random.Random

/**
 * Emparelhamento bipartido (quem tira × quem é tirado) pelo algoritmo de Kuhn (caminhos de
 * aumento). A matriz [allowed] diz se `allowed[giver][receiver]` é possível.
 *
 * O sorteio é possível se, e somente se, existe um emparelhamento perfeito (teorema de Hall).
 */
internal object Matching {

    class Result(
        /** `receiverMatch[r]` = giver que tira r, ou -1. */
        val receiverMatch: IntArray,
        val size: Int,
    ) {
        fun isPerfect(n: Int) = size == n

        /** `giverMatch[g]` = receiver tirado por g, ou -1. */
        fun giverMatch(n: Int): IntArray {
            val out = IntArray(n) { -1 }
            receiverMatch.forEachIndexed { r, g -> if (g >= 0) out[g] = r }
            return out
        }
    }

    /** Conjunto de Hall violado: [givers] só têm [receivers] para escolher, e há menos receivers. */
    class HallViolation(val givers: List<Int>, val receivers: List<Int>)

    fun maximum(allowed: Array<BooleanArray>, random: Random? = null): Result {
        val n = allowed.size
        val receiverMatch = IntArray(n) { -1 }
        val giverOrder = if (random != null) (0 until n).shuffled(random) else (0 until n).toList()
        // Com sorteio, cada giver embaralha a ordem em que tenta os receivers.
        val receiverOrder = Array(n) { g ->
            if (random != null) (0 until n).shuffled(random) else (0 until n).toList()
        }
        var size = 0
        for (g in giverOrder) {
            val visited = BooleanArray(n)
            if (augment(g, allowed, receiverOrder, receiverMatch, visited)) size++
        }
        return Result(receiverMatch, size)
    }

    private fun augment(
        giver: Int,
        allowed: Array<BooleanArray>,
        order: Array<List<Int>>,
        receiverMatch: IntArray,
        visited: BooleanArray,
    ): Boolean {
        for (r in order[giver]) {
            if (!allowed[giver][r] || visited[r]) continue
            visited[r] = true
            if (receiverMatch[r] == -1 || augment(receiverMatch[r], allowed, order, receiverMatch, visited)) {
                receiverMatch[r] = giver
                return true
            }
        }
        return false
    }

    /**
     * Dado um emparelhamento máximo imperfeito, acha o conjunto de Hall: parte de TODOS os givers sem
     * par e segue caminhos alternados. Os givers alcançados têm, juntos, menos receivers possíveis do
     * que a quantidade de givers; a diferença é o número de givers sem par.
     */
    fun hallViolation(allowed: Array<BooleanArray>, result: Result): HallViolation? {
        val n = allowed.size
        val matched = BooleanArray(n)
        result.receiverMatch.forEach { if (it >= 0) matched[it] = true }
        val unmatched = (0 until n).filter { !matched[it] }
        if (unmatched.isEmpty()) return null

        val seenGivers = BooleanArray(n)
        val seenReceivers = BooleanArray(n)
        val queue = ArrayDeque<Int>()
        for (start in unmatched) {
            seenGivers[start] = true
            queue.add(start)
        }
        while (queue.isNotEmpty()) {
            val g = queue.removeFirst()
            for (r in 0 until n) {
                if (!allowed[g][r] || seenReceivers[r]) continue
                seenReceivers[r] = true
                val next = result.receiverMatch[r]
                if (next >= 0 && !seenGivers[next]) {
                    seenGivers[next] = true
                    queue.add(next)
                }
            }
        }
        return HallViolation(
            givers = (0 until n).filter { seenGivers[it] },
            receivers = (0 until n).filter { seenReceivers[it] },
        )
    }
}
