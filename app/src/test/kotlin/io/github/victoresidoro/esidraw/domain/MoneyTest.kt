package io.github.victoresidoro.esidraw.domain

import io.github.victoresidoro.esidraw.domain.message.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {

    @Test
    fun `aceita inteiro, decimal com virgula e com ponto`() {
        assertEquals(10_000L, Money.parseCents("100"))
        assertEquals(10_050L, Money.parseCents("100,50"))
        assertEquals(10_050L, Money.parseCents("100.50"))
        assertEquals(10_050L, Money.parseCents("100,5".replace(",5", ",50")))
        assertEquals(10_050L, Money.parseCents("100,5"))
    }

    @Test
    fun `entende separador de milhar`() {
        assertEquals(123_456L, Money.parseCents("1.234,56"))
        assertEquals(123_400L, Money.parseCents("1.234"))
        assertEquals(1_234_500L, Money.parseCents("R$ 12.345"))
        assertEquals(123_456L, Money.parseCents("R$ 1.234,56"))
    }

    @Test
    fun `texto vazio, invalido ou negativo vira null`() {
        assertNull(Money.parseCents(""))
        assertNull(Money.parseCents("  "))
        assertNull(Money.parseCents("abc"))
        assertNull(Money.parseCents("-10"))
        assertNull(Money.parseCents("1,2,3"))
    }

    @Test
    fun `toInput volta para o formato do campo`() {
        assertEquals("100,00", Money.toInput(10_000))
        assertEquals("0,05", Money.toInput(5))
        assertEquals("1234,56", Money.toInput(123_456))
        assertEquals(123_456L, Money.parseCents(Money.toInput(123_456)))
    }
}
