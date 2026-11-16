package io.github.victoresidoro.esidraw.domain.engine

import io.github.victoresidoro.esidraw.domain.model.CycleGuarantee
import io.github.victoresidoro.esidraw.domain.model.DrawRules
import io.github.victoresidoro.esidraw.domain.model.Pairing

/** Uma conferência do sorteio. [detail] diz quem está errado quando [ok] é falso. */
data class DrawCheck(val label: String, val ok: Boolean, val detail: String? = null)

data class DrawValidation(
    val checks: List<DrawCheck>,
    /** Só preenchido quando o sorteio é uma permutação válida (todos tiram e são tirados uma vez). */
    val cycleGuarantee: CycleGuarantee?,
) {
    val valid: Boolean get() = checks.all { it.ok }
}

/**
 * Confere um sorteio pronto de forma independente do motor que o gerou: se alguém ficou sem par,
 * saiu duas vezes, tirou a si mesmo, ou se uma regra foi quebrada.
 *
 * [pairs] é a lista "quem tira quem" como foi gravada (uma lista, não um mapa, para enxergar
 * repetições); [people] é quem deveria estar no sorteio.
 */
object DrawValidator {

    fun validate(
        pairs: List<Pair<Long, Long>>,
        people: Set<Long>,
        rules: DrawRules? = null,
        nameOf: (Long) -> String = { it.toString() },
    ): DrawValidation {
        val givers = pairs.map { it.first }
        val receivers = pairs.map { it.second }
        val checks = ArrayList<DrawCheck>()

        fun names(ids: Collection<Long>): String {
            val sorted = ids.map(nameOf).sorted()
            return sorted.take(3).joinToString(", ") + if (sorted.size > 3) " e mais ${sorted.size - 3}" else ""
        }

        val withoutPair = people - givers.toSet()
        val giverTwice = givers.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        checks += check(
            "Todos têm alguém para presentear",
            withoutPair.isEmpty() && giverTwice.isEmpty(),
            buildList {
                if (withoutPair.isNotEmpty()) add("sem par: ${names(withoutPair)}")
                if (giverTwice.isNotEmpty()) add("com dois resultados: ${names(giverTwice)}")
            }.joinToString("; "),
        )

        val neverDrawn = people - receivers.toSet()
        checks += check(
            "Todos foram sorteados por alguém",
            neverDrawn.isEmpty(),
            "ninguém tirou: ${names(neverDrawn)}",
        )

        val drawnTwice = receivers.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        checks += check(
            "Ninguém saiu duas vezes",
            drawnTwice.isEmpty(),
            "tirado por mais de uma pessoa: ${names(drawnTwice)}",
        )

        val self = pairs.filter { it.first == it.second }.map { it.first }
        checks += check("Ninguém tirou a si mesmo", self.isEmpty(), "tiraram a si mesmos: ${names(self)}")

        val strangers = (givers + receivers).toSet() - people
        checks += check(
            "Só participantes da lista",
            strangers.isEmpty(),
            "fora da lista: ${names(strangers)}",
        )

        if (rules != null) {
            val set = pairs.map { Pairing(it.first, it.second) }.toSet()
            val broken = rules.blocks.filter { it in set }
            checks += check(
                "Bloqueios respeitados",
                broken.isEmpty(),
                broken.take(3).joinToString("; ") { "${nameOf(it.giverId)} → ${nameOf(it.receiverId)}" },
            )
            val missing = rules.forced.filter { it !in set }
            checks += check(
                "Pares fixos respeitados",
                missing.isEmpty(),
                "faltou: " + missing.take(3).joinToString("; ") { "${nameOf(it.giverId)} → ${nameOf(it.receiverId)}" },
            )
        }

        val permutation = checks.take(5).all { it.ok } && pairs.size == people.size
        val guarantee = if (permutation) DrawEditor.guarantee(pairs.toMap()) else null
        return DrawValidation(checks, guarantee)
    }

    private fun check(label: String, ok: Boolean, detail: String) =
        DrawCheck(label, ok, if (ok) null else detail)
}
