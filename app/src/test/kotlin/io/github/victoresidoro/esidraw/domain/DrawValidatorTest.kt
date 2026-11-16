package io.github.victoresidoro.esidraw.domain

import io.github.victoresidoro.esidraw.domain.engine.DrawEngine
import io.github.victoresidoro.esidraw.domain.model.DrawOptions
import io.github.victoresidoro.esidraw.domain.model.DrawOutcome
import io.github.victoresidoro.esidraw.domain.engine.DrawValidator
import io.github.victoresidoro.esidraw.domain.model.CycleGuarantee
import io.github.victoresidoro.esidraw.domain.model.DrawRules
import io.github.victoresidoro.esidraw.domain.model.Pairing
import io.github.victoresidoro.esidraw.domain.model.Participant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawValidatorTest {

    private val people = setOf(1L, 2L, 3L, 4L)
    private val ring = listOf(1L to 2L, 2L to 3L, 3L to 4L, 4L to 1L)

    private fun failed(v: io.github.victoresidoro.esidraw.domain.engine.DrawValidation) =
        v.checks.filterNot { it.ok }.map { it.label }

    @Test
    fun `sorteio correto passa em tudo e informa o ciclo`() {
        val v = DrawValidator.validate(ring, people)
        assertTrue(v.valid)
        assertEquals(CycleGuarantee.SINGLE_CYCLE, v.cycleGuarantee)
    }

    @Test
    fun `quem tirou a si mesmo e detectado`() {
        val v = DrawValidator.validate(listOf(1L to 1L, 2L to 3L, 3L to 4L, 4L to 2L), people)
        assertFalse(v.valid)
        assertTrue("Ninguém tirou a si mesmo" in failed(v))
        assertNull(v.cycleGuarantee)
    }

    @Test
    fun `quem saiu duas vezes e quem ficou sem ser tirado sao detectados`() {
        val v = DrawValidator.validate(listOf(1L to 2L, 2L to 3L, 3L to 2L, 4L to 1L), people)
        assertTrue("Ninguém saiu duas vezes" in failed(v))
        assertTrue("Todos foram sorteados por alguém" in failed(v))
    }

    @Test
    fun `quem ficou sem par e detectado`() {
        val v = DrawValidator.validate(listOf(1L to 2L, 2L to 3L, 3L to 1L), people)
        assertTrue("Todos têm alguém para presentear" in failed(v))
    }

    @Test
    fun `quem apareceu com dois resultados e detectado`() {
        val v = DrawValidator.validate(ring + (1L to 3L), people)
        assertTrue("Todos têm alguém para presentear" in failed(v))
    }

    @Test
    fun `pessoa fora da lista e detectada`() {
        val v = DrawValidator.validate(listOf(1L to 2L, 2L to 3L, 3L to 9L, 9L to 1L), setOf(1L, 2L, 3L))
        assertTrue("Só participantes da lista" in failed(v))
    }

    @Test
    fun `bloqueio quebrado e par fixo ausente sao detectados com nomes`() {
        val rules = DrawRules(blocks = setOf(Pairing(1, 2)), forced = setOf(Pairing(2, 4)))
        val v = DrawValidator.validate(ring, people, rules) { "P$it" }
        assertEquals(listOf("Bloqueios respeitados", "Pares fixos respeitados"), failed(v))
        assertEquals("P1 → P2", v.checks.first { it.label == "Bloqueios respeitados" }.detail)
    }

    @Test
    fun `ciclos curtos aparecem na garantia`() {
        val v = DrawValidator.validate(listOf(1L to 2L, 2L to 1L, 3L to 4L, 4L to 3L), people)
        assertTrue(v.valid)
        assertEquals(CycleGuarantee.NONE, v.cycleGuarantee)
    }

    @Test
    fun `sorteios de 33 pessoas feitos pelo motor sempre passam na validacao`() {
        val participants = (1L..33L).map { Participant(it, "P$it", "5531999990000") }
        repeat(300) {
            val outcome = DrawEngine().draw(participants, DrawRules(), DrawOptions(avoidShortCycles = true))
            val assignments = (outcome as DrawOutcome.Success).assignments
            val v = DrawValidator.validate(assignments.map { it.key to it.value }, participants.map { it.id }.toSet())
            assertTrue(failed(v).toString(), v.valid)
            assertEquals(CycleGuarantee.SINGLE_CYCLE, v.cycleGuarantee)
        }
    }
}
