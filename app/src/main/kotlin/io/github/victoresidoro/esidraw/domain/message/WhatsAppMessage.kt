package io.github.victoresidoro.esidraw.domain.message

import io.github.victoresidoro.esidraw.domain.model.Participant
import java.net.URLEncoder
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Detalhes do evento que entram na mensagem de cada participante. */
data class EventDetails(
    val name: String = "",
    /** Data e hora do evento, em milissegundos desde 1970 (UTC), ou `null` se ainda não definida. */
    val dateTimeMillis: Long? = null,
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
        zone: ZoneId = ZoneId.systemDefault(),
    ): String = buildString {
        appendLine("Olá, ${bold(giver.name)}! 🎁")
        appendLine()
        if (event.name.isNotBlank()) {
            appendLine("Chegou a hora do ${bold(event.name)}!")
        }
        appendLine("O seu amigo oculto é: ${bold(receiver.name)}")
        appendLine()
        event.dateTimeMillis?.let { millis ->
            val formatted = DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy 'às' HH:mm", ptBr)
                .withZone(zone)
                .format(Instant.ofEpochMilli(millis))
            appendLine("📅 Data: $formatted")
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
