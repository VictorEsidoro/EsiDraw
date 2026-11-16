package io.github.victoresidoro.esidraw.domain

import io.github.victoresidoro.esidraw.domain.message.EventDetails
import io.github.victoresidoro.esidraw.domain.message.GiftValueKind
import io.github.victoresidoro.esidraw.domain.message.WhatsAppMessage
import io.github.victoresidoro.esidraw.domain.model.Participant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class WhatsAppMessageTest {

    private val giver = Participant(1, "Maria", "5531988887777")
    private val receiver = Participant(2, "João", "5531977776666")

    private val event = EventDetails(
        name = "Amigo Oculto da Família",
        date = LocalDate.of(2026, 12, 20),
        time = LocalTime.of(19, 0),
        location = "Rua das Flores, 123",
        giftValueCents = 10_000,
        notes = "Levar embrulhado",
    )

    @Test
    fun `mensagem completa tem saudacao, sorteado em negrito e detalhes do evento`() {
        val text = WhatsAppMessage.build(giver, receiver, event)

        assertTrue(text, text.startsWith("Olá, *Maria*!"))
        assertTrue(text, text.contains("*Amigo Oculto da Família*"))
        assertTrue(text, text.contains("O seu amigo oculto é: *João*"))
        assertTrue(text, text.contains("domingo, 20/12/2026 às 19:00"))
        assertTrue(text, text.contains("Local: Rua das Flores, 123"))
        assertTrue(text, text.contains("R$ 100,00"))
        assertTrue(text, text.contains("Observações: Levar embrulhado"))
    }

    @Test
    fun `campos vazios do evento sao omitidos`() {
        val text = WhatsAppMessage.build(giver, receiver, EventDetails())
        assertTrue(text.contains("*João*"))
        assertFalse(text.contains("Data:"))
        assertFalse(text.contains("Local:"))
        assertFalse(text.contains("Valor"))
        assertFalse(text.contains("Observações"))
    }

    @Test
    fun `so a data, sem horario, nao inventa horario`() {
        val text = WhatsAppMessage.build(giver, receiver, EventDetails(date = LocalDate.of(2026, 12, 20)))
        assertTrue(text, text.contains("📅 Data: domingo, 20/12/2026"))
        assertFalse(text, text.contains(" às "))
        assertFalse(text, text.contains("Horário"))
    }

    @Test
    fun `valor minimo e maximo aparecem com o rotulo certo`() {
        val min = WhatsAppMessage.build(giver, receiver, EventDetails(giftValueCents = 5_000, giftValueKind = GiftValueKind.MINIMUM))
        assertTrue(min, min.contains("Valor mínimo do presente: R$ 50,00"))
        assertFalse(min, min.contains("sugerido"))
        val max = WhatsAppMessage.build(giver, receiver, EventDetails(giftValueCents = 5_000, giftValueKind = GiftValueKind.MAXIMUM))
        assertTrue(max, max.contains("Valor máximo do presente: R$ 50,00"))
    }

    @Test
    fun `so o horario, sem data`() {
        val text = WhatsAppMessage.build(giver, receiver, EventDetails(time = LocalTime.of(14, 30)))
        assertTrue(text, text.contains("🕒 Horário: 14:30"))
        assertFalse(text, text.contains("Data:"))
    }

    @Test
    fun `asteriscos no nome nao quebram o negrito`() {
        val tricky = Participant(3, "Zé *VIP*", "5531900000000")
        assertTrue(WhatsAppMessage.build(giver, tricky, EventDetails()).contains("*Zé VIP*"))
    }

    @Test
    fun `link usa api whatsapp com telefone e texto codificado com percent`() {
        val link = WhatsAppMessage.link("5531988887777", "Olá, *Maria*!\nA+B")
        assertTrue(link, link.startsWith("https://api.whatsapp.com/send?phone=5531988887777&text="))
        assertFalse(link, link.contains(" "))
        assertFalse(link, link.contains("\n"))
        assertTrue(link, link.contains("%0A")) // quebra de linha
        assertTrue(link, link.contains("*Maria*")) // asterisco é caractere seguro na URL
        assertTrue(link, link.contains("%2B")) // "+" literal
        assertTrue(link, link.contains("%20")) // espaço
    }

    @Test
    fun `moeda em reais com espaco comum`() {
        assertEquals("R$ 100,00", WhatsAppMessage.currency(10_000))
        assertEquals("R$ 1.234,56", WhatsAppMessage.currency(123_456))
    }
}
