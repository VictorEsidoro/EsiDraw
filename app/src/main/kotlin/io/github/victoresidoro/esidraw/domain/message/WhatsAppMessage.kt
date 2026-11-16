package io.github.victoresidoro.esidraw.domain.message

import io.github.victoresidoro.esidraw.domain.model.Participant
import java.net.URLEncoder
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Detalhes do evento que entram na mensagem de cada participante. */
data class EventDetails(
    val name: String = "",
    /** Dia do evento, ou `null` se ainda não definido. Independente do horário: dá para ter só um dos dois. */
    val date: LocalDate? = null,
    /** Horário do evento (relógio local), ou `null` se ainda não definido. */
    val time: LocalTime? = null,
    val location: String = "",
    /** Valor sugerido do presente, em centavos. */
    val giftValueCents: Long? = null,
    val notes: String = "",
)

object WhatsAppMessage {

    private val ptBr: Locale = Locale.forLanguageTag("pt-BR")

    /**
     * Mensagem para [giver] contando quem ele tirou. Os nomes vão em negrito do WhatsApp (`*nome*`)
     * e campos vazios do evento são omitidos.
     */
    fun build(
        giver: Participant,
        receiver: Participant,
        event: EventDetails,
    ): String = buildString {
        appendLine("Olá, ${bold(giver.name)}! 🎁")
        appendLine()
        if (event.name.isNotBlank()) {
            appendLine("Chegou a hora do ${bold(event.name)}!")
        }
        appendLine("O seu amigo oculto é: ${bold(receiver.name)}")
        appendLine()
        val day = event.date?.let { DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", ptBr).format(it) }
        val hour = event.time?.let { DateTimeFormatter.ofPattern("HH:mm", ptBr).format(it) }
        when {
            day != null && hour != null -> appendLine("📅 Data: $day às $hour")
            day != null -> appendLine("📅 Data: $day")
            hour != null -> appendLine("🕒 Horário: $hour")
        }
        if (event.location.isNotBlank()) appendLine("📍 Local: ${event.location.trim()}")
        event.giftValueCents?.let { appendLine("💰 Valor sugerido do presente: ${currency(it)}") }
        if (event.notes.isNotBlank()) appendLine("📝 Observações: ${event.notes.trim()}")
        appendLine()
        append("Shhh! É segredo, não conte pra ninguém. 🤫")
    }

    /** Link que abre o WhatsApp direto na conversa de [phone] (só dígitos, com DDI) com [text]. */
    fun link(phone: String, text: String): String {
        // URLEncoder usa "+" para espaço; no WhatsApp o certo é %20 (um "+" literal vira %2B).
        val encoded = URLEncoder.encode(text, "UTF-8").replace("+", "%20")
        return "https://api.whatsapp.com/send?phone=$phone&text=$encoded"
    }

    fun currency(cents: Long): String =
        NumberFormat.getCurrencyInstance(ptBr).format(cents / 100.0).replace(' ', ' ')

    /** Asteriscos no nome quebrariam o negrito do WhatsApp. */
    private fun bold(text: String) = "*${text.replace("*", "").trim()}*"
}
