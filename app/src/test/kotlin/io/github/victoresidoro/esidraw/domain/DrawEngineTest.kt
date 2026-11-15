package io.github.victoresidoro.esidraw.domain

import io.github.victoresidoro.esidraw.domain.engine.DrawEngine
import io.github.victoresidoro.esidraw.domain.model.CycleGuarantee
import io.github.victoresidoro.esidraw.domain.model.Diagnosis
import io.github.victoresidoro.esidraw.domain.model.DrawOptions
import io.github.victoresidoro.esidraw.domain.model.DrawOutcome
import io.github.victoresidoro.esidraw.domain.model.DrawRules
import io.github.victoresidoro.esidraw.domain.model.IssueKind
import io.github.victoresidoro.esidraw.domain.model.Pairing
import io.github.victoresidoro.esidraw.domain.model.Participant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.random.Random

class DrawEngineTest {

    private fun people(count: Int) = (1..count).map { Participant(it.toLong(), "P$it", "5531988887777") }

    private fun engine(seed: Int) = DrawEngine(Random(seed))

    private fun success(outcome: DrawOutcome): DrawOutcome.Success {
        if (outcome !is DrawOutcome.Success) fail("Esperava sucesso, veio $outcome")
        return outcome as DrawOutcome.Success
    }

    private fun impossible(outcome: DrawOutcome): Diagnosis {
        if (outcome !is DrawOutcome.Impossible) fail("Esperava impossível, veio $outcome")
        return (outcome as DrawOutcome.Impossible).diagnosis
    }

    /** Cada pessoa tira exatamente uma e é tirada exatamente uma vez, nunca a si mesma. */
    private fun assertValid(participants: List<Participant>, result: Map<Long, Long>) {
        val ids = participants.map { it.id }.toSet()
        assertEquals(ids, result.keys)
        assertEquals(ids, result.values.toSet())
        result.forEach { (giver, receiver) -> assertTrue("$giver tirou a si mesmo", giver != receiver) }
    }

    private fun cycleCount(result: Map<Long, Long>): Int {
        val seen = mutableSetOf<Long>()
        var cycles = 0
        for (start in result.keys) {
            if (start in seen) continue
            cycles++
            var current = start
            while (seen.add(current)) current = result.getValue(current)
        }
        return cycles
    }

    // --- Sorteio simples ---------------------------------------------------------------------

    @Test
    fun `sorteio simples sem restricoes e valido e forma um unico ciclo`() {
        for (n in listOf(2, 3, 4, 5, 8, 15, 40)) {
            val participants = people(n)
            for (seed in 1..50) {
                val result = success(engine(seed).draw(participants, DrawRules()))
                assertValid(participants, result.assignments)
                assertEquals(CycleGuarantee.SINGLE_CYCLE, result.cycleGuarantee)
                assertEquals("n=$n seed=$seed", 1, cycleCount(result.assignments))
            }
        }
    }

    @Test
    fun `sorteio com varias sementes produz resultados diferentes`() {
        val participants = people(8)
        val results = (1..40).map { success(engine(it).draw(participants, DrawRules())).assignments }.toSet()
        assertTrue("o sorteio parece determinístico", results.size > 5)
    }

    @Test
    fun `sem evitar ciclos curtos ainda sorteia validamente`() {
        val participants = people(6)
        repeat(30) { seed ->
            val result = success(engine(seed).draw(participants, DrawRules(), DrawOptions(avoidShortCycles = false)))
            assertValid(participants, result.assignments)
            assertEquals(CycleGuarantee.NONE, result.cycleGuarantee)
        }
    }

    @Test
    fun `dois participantes se tiram um ao outro`() {
        val participants = people(2)
        val result = success(engine(1).draw(participants, DrawRules()))
        assertEquals(mapOf(1L to 2L, 2L to 1L), result.assignments)
    }

    // --- Bloqueios ---------------------------------------------------------------------------

    @Test
    fun `bloqueios mutuos sao respeitados`() {
        val participants = people(6)
        val blocks = setOf(
            Pairing(1, 2), Pairing(2, 1), // casal brigado nos dois sentidos
            Pairing(3, 4), Pairing(4, 3),
            Pairing(5, 6), // só um sentido
        )
        val rules = DrawRules(blocks = blocks)
        for (seed in 1..200) {
            val result = success(engine(seed).draw(participants, rules))
            assertValid(participants, result.assignments)
            blocks.forEach { assertFalse("seed=$seed violou $it", result.assignments[it.giverId] == it.receiverId) }
        }
    }

    // --- Pares forçados ----------------------------------------------------------------------

    @Test
    fun `pares forcados sao respeitados`() {
        val participants = people(6)
        val forced = setOf(Pairing(1, 2), Pairing(3, 5))
        val rules = DrawRules(forced = forced)
        for (seed in 1..200) {
            val result = success(engine(seed).draw(participants, rules))
            assertValid(participants, result.assignments)
            forced.forEach { assertEquals("seed=$seed", it.receiverId, result.assignments[it.giverId]) }
        }
    }

    @Test
    fun `pares forcados e bloqueios juntos`() {
        val participants = people(7)
        val rules = DrawRules(
            blocks = setOf(Pairing(2, 1), Pairing(3, 1), Pairing(4, 5)),
            forced = setOf(Pairing(1, 2), Pairing(5, 4)),
        )
        for (seed in 1..100) {
            val result = success(engine(seed).draw(participants, rules))
            assertValid(participants, result.assignments)
            assertEquals(2L, result.assignments[1L])
            assertEquals(4L, result.assignments[5L])
            rules.blocks.forEach { assertFalse(result.assignments[it.giverId] == it.receiverId) }
        }
    }

    @Test
    fun `par forcado que fecha ciclo curto faz o sorteio cair para sem garantia de ciclo`() {
        // 1<->2 e 3<->4 forçados: único sorteio possível tem duas duplas.
        val participants = people(4)
        val rules = DrawRules(forced = setOf(Pairing(1, 2), Pairing(2, 1)))
        val result = success(engine(7).draw(participants, rules))
        assertEquals(CycleGuarantee.NONE, result.cycleGuarantee)
        assertEquals(2L, result.assignments[1L])
        assertEquals(1L, result.assignments[2L])
        assertValid(participants, result.assignments)
    }

    @Test
    fun `cadeia forcada ainda permite ciclo unico`() {
        val participants = people(5)
        val rules = DrawRules(forced = setOf(Pairing(1, 2), Pairing(2, 3)))
        val result = success(engine(3).draw(participants, rules))
        assertEquals(CycleGuarantee.SINGLE_CYCLE, result.cycleGuarantee)
        assertEquals(1, cycleCount(result.assignments))
    }

    // --- Cenários impossíveis e diagnóstico --------------------------------------------------

    @Test
    fun `quem bloqueou todo mundo e apontado como gargalo e pede 1 neutro`() {
        val participants = people(3)
        val rules = DrawRules(blocks = setOf(Pairing(1, 2), Pairing(1, 3)))
        val diagnosis = impossible(engine(1).draw(participants, rules))

        assertEquals(1, diagnosis.issues.size)
        val issue = diagnosis.issues.single()
        assertEquals(IssueKind.ISOLATED_GIVER, issue.kind)
        assertEquals(listOf(1L), issue.culpritIds)
        assertTrue(issue.message, issue.message.contains("P1 não tem ninguém para tirar"))
        assertTrue(issue.message, issue.message.contains("bloqueou P2 e P3"))
        assertEquals(1, diagnosis.neutralsNeeded)
    }

    @Test
    fun `dois brigados num grupo de dois precisam de 2 neutros`() {
        val participants = people(2)
        val rules = DrawRules(blocks = setOf(Pairing(1, 2), Pairing(2, 1)))
        val diagnosis = impossible(engine(1).draw(participants, rules))
        assertEquals(setOf(1L, 2L), diagnosis.issues.flatMap { it.culpritIds }.toSet())
        assertEquals(2, diagnosis.neutralsNeeded)
    }

    @Test
    fun `um participante so precisa de 1 neutro e zero precisa de 2`() {
        val one = impossible(engine(1).draw(people(1), DrawRules()))
        assertEquals(IssueKind.TOO_FEW_PARTICIPANTS, one.issues.single().kind)
        assertEquals(1, one.neutralsNeeded)

        val none = impossible(engine(1).draw(emptyList(), DrawRules()))
        assertEquals(IssueKind.TOO_FEW_PARTICIPANTS, none.issues.single().kind)
        assertEquals(2, none.neutralsNeeded)
    }

    @Test
    fun `tres pessoas so podem tirar um mesmo quarto - gargalo de Hall pede 2 neutros`() {
        val participants = people(4)
        // P1, P2 e P3 só podem tirar P4; P4 pode tirar qualquer um.
        val blocks = setOf(
            Pairing(1, 2), Pairing(1, 3),
            Pairing(2, 1), Pairing(2, 3),
            Pairing(3, 1), Pairing(3, 2),
        )
        val diagnosis = impossible(engine(1).draw(participants, DrawRules(blocks = blocks)))
        val issue = diagnosis.issues.single()
        assertEquals(IssueKind.HALL_VIOLATION, issue.kind)
        assertEquals(setOf(1L, 2L, 3L), issue.culpritIds.toSet())
        assertEquals(2, diagnosis.neutralsNeeded)
    }

    @Test
    fun `ninguem pode tirar uma pessoa bloqueada por todos`() {
        val participants = people(4)
        val rules = DrawRules(blocks = setOf(Pairing(1, 4), Pairing(2, 4), Pairing(3, 4)))
        val diagnosis = impossible(engine(1).draw(participants, rules))
        val issue = diagnosis.issues.single()
        assertEquals(IssueKind.ISOLATED_RECEIVER, issue.kind)
        assertEquals(setOf(1L, 2L, 3L), issue.culpritIds.toSet())
        assertEquals(1, diagnosis.neutralsNeeded)
    }

    @Test
    fun `par forcado que isola o resto do grupo e detectado`() {
        // P1 forçado a tirar P2 e P3 bloqueou P2 e P4 (só podia tirar P1 ou... P1 já é tirado por outro).
        val participants = people(3)
        val rules = DrawRules(
            forced = setOf(Pairing(1, 2)),
            blocks = setOf(Pairing(3, 1)), // P3 só poderia tirar P1 ou P2; P2 já é de P1
        )
        val diagnosis = impossible(engine(1).draw(participants, rules))
        val issue = diagnosis.issues.single()
        assertEquals(IssueKind.ISOLATED_GIVER, issue.kind)
        assertTrue(issue.culpritIds.contains(3L))
        assertTrue(issue.culpritIds.contains(1L)) // o par forçado de P1 é parte da causa
        assertEquals(1, diagnosis.neutralsNeeded)
    }

    @Test
    fun `duas pessoas forcadas a tirar a mesma e contradicao que neutros nao resolvem`() {
        val participants = people(5)
        val rules = DrawRules(forced = setOf(Pairing(1, 3), Pairing(2, 3)))
        val diagnosis = impossible(engine(1).draw(participants, rules))
        val issue = diagnosis.issues.single()
        assertEquals(IssueKind.FORCED_TWICE_ON_RECEIVER, issue.kind)
        assertEquals(setOf(1L, 2L), issue.culpritIds.toSet())
        assertNull(diagnosis.neutralsNeeded)
    }

    @Test
    fun `mesma pessoa com dois pares forcados e contradicao`() {
        val rules = DrawRules(forced = setOf(Pairing(1, 2), Pairing(1, 3)))
        val diagnosis = impossible(engine(1).draw(people(5), rules))
        assertEquals(IssueKind.FORCED_TWICE_BY_GIVER, diagnosis.issues.single().kind)
        assertNull(diagnosis.neutralsNeeded)
    }

    @Test
    fun `par forcado que tambem e bloqueio e contradicao`() {
        val rules = DrawRules(blocks = setOf(Pairing(1, 2)), forced = setOf(Pairing(1, 2)))
        val diagnosis = impossible(engine(1).draw(people(5), rules))
        assertEquals(IssueKind.FORCED_AND_BLOCKED, diagnosis.issues.single().kind)
        assertNull(diagnosis.neutralsNeeded)
    }

    @Test
    fun `par forcado consigo mesmo e rejeitado`() {
        val rules = DrawRules(forced = setOf(Pairing(2, 2)))
        val diagnosis = impossible(engine(1).draw(people(5), rules))
        assertEquals(IssueKind.SELF_FORCED, diagnosis.issues.single().kind)
    }

    @Test
    fun `analyze devolve null quando o sorteio e possivel`() {
        assertNull(engine(1).analyze(people(5), DrawRules(blocks = setOf(Pairing(1, 2)))))
        assertNotNull(engine(1).analyze(people(1), DrawRules()))
    }

    @Test
    fun `regras de ids desconhecidos sao ignoradas`() {
        val rules = DrawRules(blocks = setOf(Pairing(99, 1)), forced = setOf(Pairing(1, 98)))
        val result = success(engine(1).draw(people(4), rules))
        assertValid(people(4), result.assignments)
    }

    // --- Propriedade: o diagnóstico e o sorteio concordam ------------------------------------

    @Test
    fun `em qualquer conjunto aleatorio de regras analyze e draw concordam`() {
        val rnd = Random(2026)
        var feasible = 0
        var infeasible = 0
        repeat(600) { round ->
            val n = rnd.nextInt(2, 9)
            val participants = people(n)
            val blocks = mutableSetOf<Pairing>()
            repeat(rnd.nextInt(0, n * 2)) {
                val a = rnd.nextInt(1, n + 1).toLong()
                val b = rnd.nextInt(1, n + 1).toLong()
                if (a != b) blocks += Pairing(a, b)
            }
            val forced = mutableSetOf<Pairing>()
            repeat(rnd.nextInt(0, 3)) {
                val a = rnd.nextInt(1, n + 1).toLong()
                val b = rnd.nextInt(1, n + 1).toLong()
                if (a != b) forced += Pairing(a, b)
            }
            val rules = DrawRules(blocks, forced)
            val diagnosis = engine(round).analyze(participants, rules)
            val outcome = engine(round).draw(participants, rules)

            if (diagnosis == null) {
                feasible++
                val result = success(outcome)
                assertValid(participants, result.assignments)
                blocks.forEach { assertFalse(result.assignments[it.giverId] == it.receiverId) }
                forced.forEach { assertEquals(it.receiverId, result.assignments[it.giverId]) }
            } else {
                infeasible++
                impossible(outcome)
                assertTrue(diagnosis.issues.isNotEmpty())
                diagnosis.neutralsNeeded?.let { assertTrue(it >= 1) }
            }
        }
        // Garante que o teste exercitou os dois lados.
        assertTrue("feasible=$feasible", feasible > 50)
        assertTrue("infeasible=$infeasible", infeasible > 50)
    }
}
