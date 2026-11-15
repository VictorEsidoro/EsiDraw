package io.github.victoresidoro.esidraw.domain.engine

import io.github.victoresidoro.esidraw.domain.model.CycleGuarantee
import io.github.victoresidoro.esidraw.domain.model.Diagnosis
import io.github.victoresidoro.esidraw.domain.model.DrawOptions
import io.github.victoresidoro.esidraw.domain.model.DrawOutcome
import io.github.victoresidoro.esidraw.domain.model.DrawRules
import io.github.victoresidoro.esidraw.domain.model.Issue
import io.github.victoresidoro.esidraw.domain.model.IssueKind
import io.github.victoresidoro.esidraw.domain.model.Pairing
import io.github.victoresidoro.esidraw.domain.model.Participant
import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom

/**
 * Motor do sorteio de Amigo Oculto.
 *
 * Modelo: cada participante é um vértice de um grafo direcionado; "A tira B" é a aresta A→B. Um
 * sorteio válido é uma escolha de arestas em que todo vértice tem exatamente uma aresta saindo e uma
 * entrando (um emparelhamento perfeito no grafo bipartido quem-tira × quem-é-tirado).
 *
 * 1. [analyze] diz se o sorteio é possível (Hall / Kuhn) e, se não for, explica exatamente por quê.
 * 2. [draw] resolve por backtracking com heurística MRV (sempre escolhe quem tem menos opções) e
 *    poda por consistência. Tenta primeiro um único ciclo hamiltoniano (ninguém descobre o par por
 *    eliminação), depois "sem duplas A↔B" e, por último, qualquer sorteio que respeite as regras.
 *
 * Nunca usa `shuffle()` simples: ele ignora restrições. A aleatoriedade vem só da ordem em que o
 * backtracking tenta as opções, com [SecureRandom] por padrão.
 */
class DrawEngine(
    private val random: Random = SecureRandom().asKotlinRandom(),
) {

    /** `null` se o sorteio é possível; senão, o diagnóstico completo. */
    fun analyze(participants: List<Participant>, rules: DrawRules): Diagnosis? {
        val ids = participants.map { it.id }
        require(ids.distinct().size == ids.size) { "Ids de participantes repetidos" }
        val names = participants.associate { it.id to it.name }
        val clean = rules.restrictedTo(ids.toSet())

        val structural = structuralIssues(clean, names)
        if (structural.isNotEmpty()) return Diagnosis(structural, neutralsNeeded = null)

        val n = ids.size
        if (n < 2) {
            val message = if (n == 0) {
                "Cadastre participantes: um sorteio precisa de pelo menos 2 pessoas."
            } else {
                "Um sorteio precisa de pelo menos 2 pessoas, e só há 1 participante."
            }
            return Diagnosis(
                issues = listOf(Issue(IssueKind.TOO_FEW_PARTICIPANTS, ids, message)),
                neutralsNeeded = neutralsNeeded(participants, clean),
            )
        }

        val allowed = buildAllowed(ids, clean)
        val matching = Matching.maximum(allowed)
        if (matching.isPerfect(n)) return null

        return Diagnosis(
            issues = explain(ids, names, clean, allowed, matching),
            neutralsNeeded = neutralsNeeded(participants, clean),
        )
    }

    fun draw(
        participants: List<Participant>,
        rules: DrawRules,
        options: DrawOptions = DrawOptions(),
    ): DrawOutcome {
        analyze(participants, rules)?.let { return DrawOutcome.Impossible(it) }

        val ids = participants.map { it.id }
        val n = ids.size
        val allowed = buildAllowed(ids, rules.restrictedTo(ids.toSet()))

        val plan = buildList {
            if (options.avoidShortCycles) {
                add(Mode.SINGLE_CYCLE to CycleGuarantee.SINGLE_CYCLE)
                if (n >= 3) add(Mode.NO_MUTUAL to CycleGuarantee.NO_MUTUAL_PAIRS)
            }
            add(Mode.NONE to CycleGuarantee.NONE)
        }
        for ((mode, guarantee) in plan) {
            val solution = Solver(allowed, mode, random).solve() ?: continue
            return DrawOutcome.Success(toAssignments(ids, solution), guarantee)
        }

        // Rede de segurança: como [analyze] passou, existe emparelhamento perfeito, e este caminho
        // o acha sem busca (cobre o caso do backtracking estourar o orçamento de nós).
        val matching = Matching.maximum(allowed, random)
        check(matching.isPerfect(n)) { "Emparelhamento deveria existir após analyze()" }
        return DrawOutcome.Success(toAssignments(ids, matching.giverMatch(n)), CycleGuarantee.NONE)
    }

    // ---------------------------------------------------------------------------------------------
    // Grafo de possibilidades

    private fun DrawRules.restrictedTo(known: Set<Long>) = DrawRules(
        blocks = blocks.filter { it.giverId in known && it.receiverId in known }.toSet(),
        forced = forced.filter { it.giverId in known && it.receiverId in known }.toSet(),
    )

    private fun toAssignments(ids: List<Long>, giverMatch: IntArray): Map<Long, Long> =
        ids.indices.associate { ids[it] to ids[giverMatch[it]] }

    /**
     * `allowed[g][r]` = o participante g (índice) pode tirar r. Um par forçado g→r deixa g só com r
     * e retira r das opções de todos os outros. Pressupõe regras já validadas por [structuralIssues].
     */
    private fun buildAllowed(ids: List<Long>, rules: DrawRules): Array<BooleanArray> {
        val n = ids.size
        val index = ids.withIndex().associate { it.value to it.index }
        val allowed = Array(n) { g -> BooleanArray(n) { r -> g != r } }
        for (f in rules.forced) {
            val g = index.getValue(f.giverId)
            val r = index.getValue(f.receiverId)
            if (g == r) continue
            for (j in 0 until n) allowed[g][j] = j == r
            for (i in 0 until n) if (i != g) allowed[i][r] = false
        }
        for (b in rules.blocks) {
            allowed[index.getValue(b.giverId)][index.getValue(b.receiverId)] = false
        }
        return allowed
    }

    // ---------------------------------------------------------------------------------------------
    // Diagnóstico

    /** Contradições entre as próprias regras: nenhum participante novo as resolve. */
    private fun structuralIssues(rules: DrawRules, names: Map<Long, String>): List<Issue> {
        fun name(id: Long) = names.getValue(id)
        val issues = mutableListOf<Issue>()

        rules.forced.filter { it.giverId == it.receiverId }.forEach {
            issues += Issue(
                IssueKind.SELF_FORCED,
                listOf(it.giverId),
                "${name(it.giverId)} não pode ser forçado(a) a tirar a si mesmo(a).",
            )
        }
        rules.forced.groupBy { it.giverId }
            .filterValues { list -> list.map { it.receiverId }.distinct().size > 1 }
            .forEach { (giver, list) ->
                issues += Issue(
                    IssueKind.FORCED_TWICE_BY_GIVER,
                    listOf(giver),
                    "${name(giver)} tem mais de um par forçado (${joinNames(list.map { name(it.receiverId) })}); " +
                        "cada pessoa só tira uma.",
                )
            }
        rules.forced.filter { it.giverId != it.receiverId }.groupBy { it.receiverId }
            .filterValues { list -> list.map { it.giverId }.distinct().size > 1 }
            .forEach { (receiver, list) ->
                val givers = list.map { it.giverId }.distinct()
                issues += Issue(
                    IssueKind.FORCED_TWICE_ON_RECEIVER,
                    givers,
                    "${joinNames(givers.map(::name))} foram forçados a tirar ${name(receiver)}, " +
                        "mas só uma pessoa pode tirá-lo(a).",
                )
            }
        rules.forced.filter { it in rules.blocks }.forEach {
            issues += Issue(
                IssueKind.FORCED_AND_BLOCKED,
                listOf(it.giverId, it.receiverId),
                "${name(it.giverId)} está bloqueado(a) de tirar ${name(it.receiverId)} e, ao mesmo " +
                    "tempo, forçado(a) a tirar essa pessoa. Remova uma das duas regras.",
            )
        }
        return issues
    }

    private fun explain(
        ids: List<Long>,
        names: Map<Long, String>,
        rules: DrawRules,
        allowed: Array<BooleanArray>,
        matching: Matching.Result,
    ): List<Issue> {
        val n = ids.size
        fun name(i: Int) = names.getValue(ids[i])
        val issues = mutableListOf<Issue>()

        for (g in 0 until n) {
            if (allowed[g].any { it }) continue
            val others = (0 until n).filter { it != g }
            val blocked = others.filter { Pairing(ids[g], ids[it]) in rules.blocks }
            val taken = others.filter { o ->
                rules.forced.any { it.receiverId == ids[o] && it.giverId != ids[g] }
            }
            val reasons = buildList {
                if (blocked.isNotEmpty()) add("bloqueou ${joinNames(blocked.map(::name))}")
                if (taken.isNotEmpty()) {
                    add("${joinNames(taken.map(::name))} já ${if (taken.size > 1) "são" else "é"} par forçado de outra pessoa")
                }
            }
            val culprits = listOf(ids[g]) + taken.flatMap { o ->
                rules.forced.filter { it.receiverId == ids[o] }.map { it.giverId }
            }
            issues += Issue(
                IssueKind.ISOLATED_GIVER,
                culprits.distinct(),
                "${name(g)} não tem ninguém para tirar" +
                    if (reasons.isEmpty()) "." else ": ${reasons.joinToString("; ")}.",
            )
        }

        for (r in 0 until n) {
            if ((0 until n).any { allowed[it][r] }) continue
            val blockers = (0 until n).filter { it != r && Pairing(ids[it], ids[r]) in rules.blocks }
            val forcedElsewhere = (0 until n).filter { g ->
                g != r && rules.forced.any { it.giverId == ids[g] && it.receiverId != ids[r] }
            }
            val reasons = buildList {
                if (blockers.isNotEmpty()) add("bloqueado(a) por ${joinNames(blockers.map(::name))}")
                if (forcedElsewhere.isNotEmpty()) {
                    add("${joinNames(forcedElsewhere.map(::name))} já têm par forçado com outra pessoa")
                }
            }
            issues += Issue(
                IssueKind.ISOLATED_RECEIVER,
                (blockers + forcedElsewhere).map { ids[it] }.distinct().ifEmpty { listOf(ids[r]) },
                "Ninguém pode tirar ${name(r)}" +
                    if (reasons.isEmpty()) "." else ": ${reasons.joinToString("; ")}.",
            )
        }

        if (issues.isEmpty()) {
            val hall = Matching.hallViolation(allowed, matching)
            if (hall != null) {
                val missing = hall.givers.size - hall.receivers.size
                issues += Issue(
                    IssueKind.HALL_VIOLATION,
                    hall.givers.map { ids[it] },
                    "${hall.givers.size} participantes (${joinNames(hall.givers.map(::name))}) só têm " +
                        "${hall.receivers.size} opção(ões) de pessoa para tirar" +
                        (if (hall.receivers.isEmpty()) "" else " (${joinNames(hall.receivers.map(::name))})") +
                        " por causa de bloqueios e pares forçados: faltam $missing opção(ões).",
                )
            }
        }
        return issues
    }

    /**
     * Menor k tal que o sorteio fica possível ao adicionar k participantes sem regras. A resposta é
     * exata (testa k = 1, 2, ...) e não uma estimativa. `null` se nenhum k resolve.
     */
    private fun neutralsNeeded(participants: List<Participant>, rules: DrawRules): Int? {
        val base = participants.map { it.id }
        for (k in 1..(base.size + 2)) {
            // Ids negativos nunca colidem com ids reais (gerados a partir de 1).
            val ids = base + (1..k).map { -it.toLong() }
            val allowed = buildAllowed(ids, rules)
            if (Matching.maximum(allowed).isPerfect(ids.size)) return k
        }
        return null
    }

    private fun joinNames(names: List<String>): String = when (names.size) {
        0 -> ""
        1 -> names[0]
        else -> names.dropLast(1).joinToString(", ") + " e " + names.last()
    }

    // ---------------------------------------------------------------------------------------------
    // Backtracking + MRV

    private enum class Mode { SINGLE_CYCLE, NO_MUTUAL, NONE }

    private class BudgetExceeded : RuntimeException(null, null, false, false)

    /**
     * Resolve o sorteio sobre [allowed] respeitando [mode].
     *
     * - MRV: a cada passo escolhe o participante com menos candidatos restantes (empates sorteados).
     *   Pares forçados têm 1 candidato e entram primeiro naturalmente.
     * - Poda: falha cedo se algum participante ficar sem candidatos ou se alguém não puder mais ser
     *   tirado por ninguém.
     * - Ciclo único: mantém, para cada caminho parcial, cabeça e cauda; uma aresta que fecharia um
     *   ciclo antes de completar todos os vértices é rejeitada.
     */
    private class Solver(
        private val allowed: Array<BooleanArray>,
        private val mode: Mode,
        private val random: Random,
        private val nodeBudget: Int = 20_000,
    ) {
        private val n = allowed.size
        private val assigned = IntArray(n) { -1 }
        private val taken = BooleanArray(n)
        private val headOf = IntArray(n) { it } // válido para quem é cauda de um caminho
        private val tailOf = IntArray(n) { it } // válido para quem é cabeça de um caminho
        private var edges = 0
        private var nodes = 0

        fun solve(): IntArray? = try {
            if (search()) assigned.copyOf() else null
        } catch (_: BudgetExceeded) {
            null
        }

        private fun isCandidate(x: Int, y: Int): Boolean {
            if (!allowed[x][y] || taken[y]) return false
            return when (mode) {
                Mode.NONE -> true
                Mode.NO_MUTUAL -> assigned[y] != x
                // y ser a cabeça do caminho de x fecharia um ciclo; só vale na última aresta.
                Mode.SINGLE_CYCLE -> headOf[x] != y || edges == n - 1
            }
        }

        private fun search(): Boolean {
            if (edges == n) return true
            if (++nodes > nodeBudget) throw BudgetExceeded()

            var best = -1
            var bestCount = Int.MAX_VALUE
            var ties = 0
            val reachable = BooleanArray(n)
            for (x in 0 until n) {
                if (assigned[x] != -1) continue
                var count = 0
                for (y in 0 until n) {
                    if (isCandidate(x, y)) {
                        count++
                        reachable[y] = true
                    }
                }
                if (count == 0) return false
                if (count < bestCount) {
                    best = x; bestCount = count; ties = 1
                } else if (count == bestCount) {
                    ties++
                    if (random.nextInt(ties) == 0) best = x
                }
            }
            for (y in 0 until n) if (!taken[y] && !reachable[y]) return false

            val candidates = (0 until n).filter { isCandidate(best, it) }.shuffled(random)
            for (y in candidates) {
                val head = headOf[best]
                val tail = tailOf[y]
                val oldTailOfHead = tailOf[head]
                val oldHeadOfTail = headOf[tail]

                assigned[best] = y
                taken[y] = true
                edges++
                if (head != y) { // junta os dois caminhos; se head == y, esta aresta fecha o ciclo final
                    tailOf[head] = tail
                    headOf[tail] = head
                }

                if (search()) return true

                tailOf[head] = oldTailOfHead
                headOf[tail] = oldHeadOfTail
                edges--
                taken[y] = false
                assigned[best] = -1
            }
            return false
        }
    }
}
