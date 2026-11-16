package io.github.victoresidoro.esidraw.domain.engine

import io.github.victoresidoro.esidraw.domain.model.CycleGuarantee
import io.github.victoresidoro.esidraw.domain.model.DrawRules
import io.github.victoresidoro.esidraw.domain.model.Pairing
import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom

/** Resultado de editar um sorteio que já existe. */
sealed interface EditResult {
    /**
     * [assignments] é o sorteio inteiro depois da edição (quem tira quem). [changedGivers] são só as
     * pessoas cujo resultado mudou (ou que entraram agora): são as únicas que precisam de mensagem nova.
     */
    data class Success(
        val assignments: Map<Long, Long>,
        val changedGivers: Set<Long>,
        val cycleGuarantee: CycleGuarantee,
    ) : EditResult

    data class Failure(val reason: String) : EditResult
}

/**
 * Mexe num sorteio pronto mudando o mínimo de pessoas, em vez de refazer tudo.
 *
 * - [insert]: encaixa uma pessoa nova no meio de uma seta "A tira B" (vira A→nova→B). Só A recebe
 *   resultado novo; B e todo o resto continuam como estavam. Se nenhuma seta serve, tenta mexer em
 *   duas pessoas antigas.
 * - [remove]: quem tirava a pessoa que saiu passa a tirar quem ela tinha tirado. Se isso for bloqueado,
 *   tenta trocar o resultado de uma segunda pessoa.
 *
 * Respeita bloqueios e pares forçados e nunca desfaz um par forçado. Entre as opções válidas, prefere
 * a que mantém a melhor garantia contra ciclos curtos e depois a que mexe em menos gente; o desempate
 * é aleatório ([SecureRandom]).
 */
class DrawEditor(
    private val random: Random = SecureRandom().asKotlinRandom(),
) {

    /** [current] é o sorteio atual (só de quem já estava nele); [newId] é quem entra agora. */
    fun insert(rules: DrawRules, current: Map<Long, Long>, newId: Long): EditResult {
        require(newId !in current && newId !in current.values) { "A pessoa já está no sorteio" }
        if (current.size < 2) {
            return EditResult.Failure("O sorteio atual tem menos de 2 pessoas. Refaça o sorteio.")
        }
        val ctx = Context(rules, current, newId)
        val candidates = ArrayList<Candidate>()

        // 1) Uma seta A→B vira A→nova→B: muda só A.
        for ((a, b) in current) {
            if (!ctx.removable(a, b) || !ctx.allowed(a, newId) || !ctx.allowed(newId, b)) continue
            candidates += Candidate(current - a + (a to newId) + (newId to b), setOf(a, newId))
        }

        // 2) G→X e Y→R viram G→nova, nova→R e Y→X: muda G e Y.
        for ((g, x) in current) {
            if (!ctx.removable(g, x) || !ctx.allowed(g, newId)) continue
            for ((y, r) in current) {
                if (r == x || y == g || !ctx.removable(y, r)) continue
                if (!ctx.allowed(newId, r) || !ctx.allowed(y, x)) continue
                candidates += Candidate(
                    current - g - y + (g to newId) + (y to x) + (newId to r),
                    setOf(g, y, newId),
                )
            }
        }

        return pick(candidates, current)
            ?: EditResult.Failure("Nenhum encaixe respeita as regras sem refazer o sorteio.")
    }

    /**
     * [current] é o sorteio antes da saída (inclui [removedId]). [rules] já não deve citar [removedId]
     * (as regras dele saem junto com ele).
     */
    fun remove(rules: DrawRules, current: Map<Long, Long>, removedId: Long): EditResult {
        val receiver = current[removedId] ?: return EditResult.Failure("A pessoa não está no sorteio.")
        val giver = current.entries.firstOrNull { it.value == removedId }?.key
            ?: return EditResult.Failure("O sorteio está incompleto. Refaça o sorteio.")
        val rest = current - removedId
        if (rest.size < 2) {
            return EditResult.Failure("Sobrariam menos de 2 pessoas. Refaça o sorteio.")
        }

        // Dupla A↔B: com a saída de B, A fica sem ninguém; encaixa A de novo no resto.
        if (giver == receiver) {
            val others = rest - giver
            if (others.size < 2) return EditResult.Failure("Sobrariam menos de 2 pessoas. Refaça o sorteio.")
            return insert(rules, others, giver)
        }

        val ctx = Context(rules, rest)
        val candidates = ArrayList<Candidate>()

        // 1) Quem tirava a pessoa passa a tirar quem ela tirou: muda só esse participante.
        if (ctx.allowed(giver, receiver)) {
            candidates += Candidate(rest + (giver to receiver), setOf(giver))
        }

        // 2) Se não puder, troca o resultado de mais uma pessoa A→B: giver→B e A→receiver.
        for ((a, b) in rest) {
            if (a == giver || b == receiver || !ctx.removable(a, b)) continue
            if (!ctx.allowed(giver, b) || !ctx.allowed(a, receiver)) continue
            candidates += Candidate(rest - a + (giver to b) + (a to receiver), setOf(giver, a))
        }

        return pick(candidates, rest)
            ?: EditResult.Failure("Nenhum ajuste respeita as regras sem refazer o sorteio.")
    }

    // ---------------------------------------------------------------------------------------------

    private class Candidate(val assignments: Map<Long, Long>, val changed: Set<Long>)

    private fun pick(candidates: List<Candidate>, base: Map<Long, Long>): EditResult? {
        if (candidates.isEmpty()) return null
        val scored = candidates.map { Triple(it, guarantee(it.assignments), it.changed.size) }
        val bestRank = scored.maxOf { it.second.ordinal.let { o -> -o } }
        val byGuarantee = scored.filter { -it.second.ordinal == bestRank }
        val fewest = byGuarantee.minOf { it.third }
        val finalists = byGuarantee.filter { it.third == fewest }
        val (chosen, level, _) = finalists[random.nextInt(finalists.size)]
        check(isPermutation(chosen.assignments)) { "Edição gerou um sorteio inválido ($base)" }
        return EditResult.Success(chosen.assignments, chosen.changed, level)
    }

    private fun isPermutation(assignments: Map<Long, Long>): Boolean =
        assignments.keys == assignments.values.toSet() &&
            assignments.values.size == assignments.values.toSet().size &&
            assignments.none { (g, r) -> g == r }

    /** Regras já filtradas para as pessoas que continuam no sorteio. */
    private class Context(rules: DrawRules, current: Map<Long, Long>, extraId: Long? = null) {
        private val ids = current.keys + current.values + listOfNotNull(extraId)
        private val blocks = rules.blocks.filter { it.giverId in ids && it.receiverId in ids }.toSet()
        private val forced = rules.forced.filter { it.giverId in ids && it.receiverId in ids }.toSet()
        private val forcedByGiver = forced.associate { it.giverId to it.receiverId }
        private val forcedByReceiver = forced.associate { it.receiverId to it.giverId }

        /** Pode [giver] tirar [receiver]? Respeita bloqueios e pares forçados. */
        fun allowed(giver: Long, receiver: Long): Boolean {
            if (giver == receiver || Pairing(giver, receiver) in blocks) return false
            forcedByGiver[giver]?.let { if (it != receiver) return false }
            forcedByReceiver[receiver]?.let { if (it != giver) return false }
            return true
        }

        /** Dá para desfazer a seta [giver]→[receiver]? Par forçado nunca se desfaz. */
        fun removable(giver: Long, receiver: Long): Boolean = Pairing(giver, receiver) !in forced
    }

    companion object {
        /** Maior = melhor. Usa a mesma escala do sorteio completo. */
        fun guarantee(assignments: Map<Long, Long>): CycleGuarantee {
            if (assignments.isEmpty()) return CycleGuarantee.NONE
            val visited = HashSet<Long>()
            var cycles = 0
            for (start in assignments.keys) {
                if (start in visited) continue
                cycles++
                var node = start
                while (visited.add(node)) node = assignments.getValue(node)
            }
            if (cycles == 1) return CycleGuarantee.SINGLE_CYCLE
            val mutual = assignments.any { (a, b) -> assignments[b] == a }
            return if (mutual) CycleGuarantee.NONE else CycleGuarantee.NO_MUTUAL_PAIRS
        }
    }
}
