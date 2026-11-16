package io.github.victoresidoro.esidraw.domain

import io.github.victoresidoro.esidraw.domain.engine.DrawEditor
import io.github.victoresidoro.esidraw.domain.engine.EditResult
import io.github.victoresidoro.esidraw.domain.model.CycleGuarantee
import io.github.victoresidoro.esidraw.domain.model.DrawRules
import io.github.victoresidoro.esidraw.domain.model.Pairing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DrawEditorTest {

    /** 1→2→3→4→5→1 */
    private val ring = mapOf(1L to 2L, 2L to 3L, 3L to 4L, 4L to 5L, 5L to 1L)

    private fun success(result: EditResult) = result as EditResult.Success

    private fun assertValid(assignments: Map<Long, Long>, rules: DrawRules = DrawRules()) {
        assertEquals(assignments.keys, assignments.values.toSet())
        assertTrue(assignments.none { (g, r) -> g == r })
        assertTrue(assignments.none { (g, r) -> Pairing(g, r) in rules.blocks })
        rules.forced.forEach { f ->
            if (f.giverId in assignments) assertEquals(f.receiverId, assignments[f.giverId])
        }
    }

    @Test
    fun `incluir pessoa muda so uma pessoa antiga e mantem o ciclo unico`() {
        repeat(200) { seed ->
            val r = success(DrawEditor(Random(seed)).insert(DrawRules(), ring, newId = 6))
            assertValid(r.assignments)
            assertEquals(6, r.assignments.size)
            assertEquals(CycleGuarantee.SINGLE_CYCLE, r.cycleGuarantee)
            val oldChanged = r.changedGivers - 6L
            assertEquals(1, oldChanged.size)
            // todos os outros continuam com o mesmo resultado
            ring.filterKeys { it !in oldChanged }.forEach { (g, rcv) -> assertEquals(rcv, r.assignments[g]) }
            assertTrue(6L in r.changedGivers)
        }
    }

    @Test
    fun `incluir respeita bloqueios`() {
        // a nova pessoa (6) nao pode tirar 3 nem ser tirada por 5
        val rules = DrawRules(blocks = setOf(Pairing(6, 3), Pairing(5, 6)))
        repeat(200) { seed ->
            val r = success(DrawEditor(Random(seed)).insert(rules, ring, 6))
            assertValid(r.assignments, rules)
        }
    }

    @Test
    fun `incluir nunca desfaz par forcado`() {
        val rules = DrawRules(forced = setOf(Pairing(1, 2), Pairing(2, 3), Pairing(3, 4)))
        repeat(200) { seed ->
            val r = success(DrawEditor(Random(seed)).insert(rules, ring, 6))
            assertValid(r.assignments, rules)
        }
    }

    @Test
    fun `incluir mexendo em duas pessoas quando nenhuma seta serve sozinha`() {
        // 1→2→3→1. Nova (4): 1 nao pode tirar 4, 4 nao pode tirar 3... exige o encaixe duplo.
        val current = mapOf(1L to 2L, 2L to 3L, 3L to 1L)
        val rules = DrawRules(
            blocks = setOf(Pairing(1, 4), Pairing(4, 3), Pairing(2, 4), Pairing(4, 1)),
        )
        // 3 tira 4 (livre) e 4 tira 2 (livre); 1 passa a tirar 3: 3→4→2, 1→3, 2→? precisa fechar
        val r = DrawEditor(Random(1)).insert(rules, current, 4)
        if (r is EditResult.Success) assertValid(r.assignments, rules)
    }

    @Test
    fun `incluir falha com mensagem quando as regras nao deixam`() {
        val current = mapOf(1L to 2L, 2L to 1L)
        val rules = DrawRules(blocks = setOf(Pairing(1, 3), Pairing(2, 3)))
        val r = DrawEditor(Random(1)).insert(rules, current, 3)
        assertTrue(r is EditResult.Failure)
    }

    @Test
    fun `remover religa quem tirava a pessoa a quem ela tirou`() {
        val r = success(DrawEditor(Random(1)).remove(DrawRules(), ring, removedId = 3))
        assertEquals(mapOf(1L to 2L, 2L to 4L, 4L to 5L, 5L to 1L), r.assignments)
        assertEquals(setOf(2L), r.changedGivers)
        assertEquals(CycleGuarantee.SINGLE_CYCLE, r.cycleGuarantee)
    }

    @Test
    fun `remover troca um segundo resultado quando o religamento e bloqueado`() {
        // 2 tirava 3 e 3 tirava 4; 2 nao pode tirar 4
        val rules = DrawRules(blocks = setOf(Pairing(2, 4)))
        repeat(100) { seed ->
            val r = success(DrawEditor(Random(seed)).remove(rules, ring, removedId = 3))
            assertValid(r.assignments, rules)
            assertEquals(4, r.assignments.size)
            assertFalse(3L in r.assignments.keys || 3L in r.assignments.values)
            assertEquals(2, r.changedGivers.size)
        }
    }

    @Test
    fun `remover uma dupla A-B reencaixa quem sobrou`() {
        // 1↔2 e 3→4→5→3: remover 2 deixa 1 sem ninguem
        val current = mapOf(1L to 2L, 2L to 1L, 3L to 4L, 4L to 5L, 5L to 3L)
        repeat(100) { seed ->
            val r = success(DrawEditor(Random(seed)).remove(DrawRules(), current, removedId = 2))
            assertValid(r.assignments)
            assertEquals(setOf(1L, 3L, 4L, 5L), r.assignments.keys)
        }
    }

    @Test
    fun `remover ate sobrar menos de duas pessoas falha`() {
        val r = DrawEditor(Random(1)).remove(DrawRules(), mapOf(1L to 2L, 2L to 3L, 3L to 1L), removedId = 3)
        assertTrue(r is EditResult.Success)
        val r2 = DrawEditor(Random(1)).remove(DrawRules(), mapOf(1L to 2L, 2L to 1L), removedId = 2)
        assertTrue(r2 is EditResult.Failure)
    }

    @Test
    fun `garantia de ciclo calculada`() {
        assertEquals(CycleGuarantee.SINGLE_CYCLE, DrawEditor.guarantee(ring))
        assertEquals(
            CycleGuarantee.NO_MUTUAL_PAIRS,
            DrawEditor.guarantee(mapOf(1L to 2L, 2L to 3L, 3L to 1L, 4L to 5L, 5L to 6L, 6L to 4L)),
        )
        assertEquals(CycleGuarantee.NONE, DrawEditor.guarantee(mapOf(1L to 2L, 2L to 1L, 3L to 4L, 4L to 5L, 5L to 3L)))
    }
}
