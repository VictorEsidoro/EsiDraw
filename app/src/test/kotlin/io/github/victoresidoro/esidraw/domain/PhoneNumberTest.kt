package io.github.victoresidoro.esidraw.domain

import io.github.victoresidoro.esidraw.domain.phone.PhoneNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneNumberTest {

    @Test
    fun `celular com formatacao`() {
        assertEquals("5531988887777", PhoneNumber.normalize("(31) 98888-7777"))
        assertEquals("5531988887777", PhoneNumber.normalize("31 98888 7777"))
        assertEquals("5531988887777", PhoneNumber.normalize("31988887777"))
    }

    @Test
    fun `com codigo do pais`() {
        assertEquals("5531988887777", PhoneNumber.normalize("+55 31 98888-7777"))
        assertEquals("5531988887777", PhoneNumber.normalize("5531988887777"))
        assertEquals("5531988887777", PhoneNumber.normalize("0055 31 98888-7777"))
    }

    @Test
    fun `zero de tronco na frente`() {
        assertEquals("5531988887777", PhoneNumber.normalize("031 98888-7777"))
    }

    @Test
    fun `fixo com 10 digitos`() {
        assertEquals("553133334444", PhoneNumber.normalize("(31) 3333-4444"))
    }

    @Test
    fun `sem DDD ou invalido vira nulo`() {
        assertNull(PhoneNumber.normalize("98888-7777"))
        assertNull(PhoneNumber.normalize("3333-4444"))
        assertNull(PhoneNumber.normalize("abc"))
        assertNull(PhoneNumber.normalize(""))
        assertNull(PhoneNumber.normalize(null))
        assertNull(PhoneNumber.normalize("(31) 88888-7777")) // celular de 11 dígitos sem o 9
        assertNull(PhoneNumber.normalize("(05) 98888-7777")) // DDD inexistente
    }

    @Test
    fun `waid tem prioridade`() {
        assertEquals("5531988887777", PhoneNumber.normalize("qualquer", waid = "5531988887777"))
    }

    @Test
    fun `numero internacional explicito passa como esta`() {
        assertEquals("14155550123", PhoneNumber.normalize("+1 (415) 555-0123"))
        assertEquals("351912345678", PhoneNumber.normalize("00351 912 345 678"))
    }

    @Test
    fun `formatacao para exibir`() {
        assertEquals("+55 (31) 98888-7777", PhoneNumber.format("5531988887777"))
        assertEquals("+55 (31) 3333-4444", PhoneNumber.format("553133334444"))
        assertEquals("+14155550123", PhoneNumber.format("14155550123"))
    }
}
