package io.github.victoresidoro.esidraw.domain.model

import io.github.victoresidoro.esidraw.domain.message.EventDetails

/** Configuração salva do evento, mais o que o sorteio garantiu da última vez que rodou. */
data class EventSettings(
    val details: EventDetails = EventDetails(),
    val avoidShortCycles: Boolean = true,
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
