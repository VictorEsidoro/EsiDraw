package io.github.victoresidoro.esidraw.domain.repository

import io.github.victoresidoro.esidraw.domain.model.AddParticipantResult
import io.github.victoresidoro.esidraw.domain.model.CycleGuarantee
import io.github.victoresidoro.esidraw.domain.model.DrawRules
import io.github.victoresidoro.esidraw.domain.model.EventSettings
import io.github.victoresidoro.esidraw.domain.model.Pairing
import io.github.victoresidoro.esidraw.domain.model.ParticipantStatus
import kotlinx.coroutines.flow.Flow

/**
 * Tudo o que o app guarda. Mudar a lista de participantes ou as regras descarta o sorteio feito,
 * porque o resultado antigo deixaria de valer.
 */
interface EsiDrawRepository {
    val event: Flow<EventSettings>
    val participants: Flow<List<ParticipantStatus>>
    val rules: Flow<DrawRules>

    /** Aplica [transform] ao evento atual de forma atômica (várias edições seguidas não se perdem). */
    suspend fun updateEvent(transform: (EventSettings) -> EventSettings)

    suspend fun addParticipant(name: String, phone: String): AddParticipantResult
    suspend fun removeParticipant(id: Long)

    suspend fun addBlock(pairing: Pairing, bothWays: Boolean)
    suspend fun removeBlock(pairing: Pairing)
    suspend fun addForced(pairing: Pairing)
    suspend fun removeForced(pairing: Pairing)

    /** Guarda o resultado do sorteio ([assignments]: quem tira → quem foi tirado). */
    suspend fun saveDraw(assignments: Map<Long, Long>, guarantee: CycleGuarantee)
    suspend fun clearDraw()
    suspend fun setMessageSent(participantId: Long, sent: Boolean)
}
