package io.github.victoresidoro.esidraw.domain.model

import io.github.victoresidoro.esidraw.domain.message.EventDetails

/** Configuração salva do evento, mais o que o sorteio garantiu da última vez que rodou. */
data class EventSettings(
    val details: EventDetails = EventDetails(),
    val avoidShortCycles: Boolean = true,
    /**
     * O organizador também está na lista de participantes? Se sim, ele não deve ver o resultado dos
     * outros (o WhatsApp deixa a mensagem no histórico dele). Se não, ver o resultado é livre.
     */
    val organizerParticipates: Boolean = true,
    /** `null` enquanto não há sorteio feito. */
    val cycleGuarantee: CycleGuarantee? = null,
)

/**
 * Um participante com o andamento do sorteio. [receiverId] é o resultado (quem ele tirou) e só deve
 * ser lido na hora de montar a mensagem: a interface nunca o mostra sem confirmação.
 */
data class ParticipantStatus(
    val participant: Participant,
    val receiverId: Long?,
    val messageSent: Boolean,
)

enum class AddParticipantResult { ADDED, DUPLICATE }

/** Uma pessoa pronta para entrar numa lista: nome e telefone já normalizado. */
data class Contact(val name: String, val phone: String)

data class GroupSummary(val id: Long, val name: String, val memberCount: Int)

data class ImportCounts(val added: Int, val duplicates: Int)

/** Uma linha de um sorteio guardado: quem tirou quem (por nome, como estava na data). */
data class DrawHistoryEntry(
    val giverId: Long,
    val giverName: String,
    val receiverId: Long,
    val receiverName: String,
)

/** Um sorteio feito em [createdAt] (milissegundos desde 1970). */
data class DrawHistory(
    val id: Long,
    val createdAt: Long,
    val guarantee: CycleGuarantee,
    val entries: List<DrawHistoryEntry>,
)
