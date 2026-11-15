package io.github.victoresidoro.esidraw.data.repo

import androidx.room.withTransaction
import io.github.victoresidoro.esidraw.data.db.EsiDrawDatabase
import io.github.victoresidoro.esidraw.data.db.EventEntity
import io.github.victoresidoro.esidraw.data.db.ParticipantEntity
import io.github.victoresidoro.esidraw.data.db.RuleEntity
import io.github.victoresidoro.esidraw.domain.message.EventDetails
import io.github.victoresidoro.esidraw.domain.model.AddParticipantResult
import io.github.victoresidoro.esidraw.domain.model.CycleGuarantee
import io.github.victoresidoro.esidraw.domain.model.DrawRules
import io.github.victoresidoro.esidraw.domain.model.EventSettings
import io.github.victoresidoro.esidraw.domain.model.Pairing
import io.github.victoresidoro.esidraw.domain.model.Participant
import io.github.victoresidoro.esidraw.domain.model.ParticipantStatus
import io.github.victoresidoro.esidraw.domain.repository.EsiDrawRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomEsiDrawRepository(private val db: EsiDrawDatabase) : EsiDrawRepository {

    private val dao = db.dao()

    override val event: Flow<EventSettings> = dao.observeEvent().map { it?.toDomain() ?: EventSettings() }

    override val participants: Flow<List<ParticipantStatus>> =
        dao.observeParticipants().map { list -> list.map { it.toStatus() } }

    override val rules: Flow<DrawRules> = dao.observeRules().map { list ->
        fun pairings(forced: Boolean) =
            list.filter { it.forced == forced }.map { Pairing(it.giverId, it.receiverId) }.toSet()
        DrawRules(blocks = pairings(forced = false), forced = pairings(forced = true))
    }

    override suspend fun updateEvent(transform: (EventSettings) -> EventSettings) {
        db.withTransaction { writeEvent(transform) }
    }

    override suspend fun addParticipant(name: String, phone: String): AddParticipantResult =
        db.withTransaction {
            val clean = name.trim()
            val duplicate = dao.getParticipants().any { it.name.equals(clean, ignoreCase = true) && it.phone == phone }
            if (duplicate) return@withTransaction AddParticipantResult.DUPLICATE
            dao.insertParticipant(ParticipantEntity(name = clean, phone = phone))
            discardDraw()
            AddParticipantResult.ADDED
        }

    override suspend fun removeParticipant(id: Long) {
        db.withTransaction {
            dao.deleteParticipant(id) // regras ligadas a ele saem junto (ON DELETE CASCADE)
            discardDraw()
        }
    }

    override suspend fun addBlock(pairing: Pairing, bothWays: Boolean) {
        db.withTransaction {
            dao.insertRule(pairing.toRule(forced = false))
            if (bothWays) dao.insertRule(Pairing(pairing.receiverId, pairing.giverId).toRule(forced = false))
            discardDraw()
        }
    }

    override suspend fun removeBlock(pairing: Pairing) {
        db.withTransaction {
            dao.deleteRule(forced = false, giverId = pairing.giverId, receiverId = pairing.receiverId)
            discardDraw()
        }
    }

    override suspend fun addForced(pairing: Pairing) {
        db.withTransaction {
            dao.insertRule(pairing.toRule(forced = true))
            discardDraw()
        }
    }

    override suspend fun removeForced(pairing: Pairing) {
        db.withTransaction {
            dao.deleteRule(forced = true, giverId = pairing.giverId, receiverId = pairing.receiverId)
            discardDraw()
        }
    }

    override suspend fun saveDraw(assignments: Map<Long, Long>, guarantee: CycleGuarantee) {
        db.withTransaction {
            dao.clearDraw()
            assignments.forEach { (giver, receiver) -> dao.setReceiver(giver, receiver) }
            writeEvent { it.copy(cycleGuarantee = guarantee) }
        }
    }

    override suspend fun clearDraw() {
        db.withTransaction { discardDraw() }
    }

    override suspend fun setMessageSent(participantId: Long, sent: Boolean) {
        dao.setMessageSent(participantId, sent)
    }

    private suspend fun discardDraw() {
        dao.clearDraw()
        dao.clearCycleGuarantee()
    }

    private suspend fun writeEvent(transform: (EventSettings) -> EventSettings) {
        val current = dao.getEvent()?.toDomain() ?: EventSettings()
        dao.upsertEvent(transform(current).toEntity())
    }

    // ---------------------------------------------------------------------------------------------

    private fun Pairing.toRule(forced: Boolean) = RuleEntity(forced = forced, giverId = giverId, receiverId = receiverId)

    private fun EventEntity.toDomain() = EventSettings(
        details = EventDetails(
            name = name,
            dateTimeMillis = dateTimeMillis,
            location = location,
            giftValueCents = giftValueCents,
            notes = notes,
        ),
        avoidShortCycles = avoidShortCycles,
        cycleGuarantee = cycleGuarantee?.let { runCatching { CycleGuarantee.valueOf(it) }.getOrNull() },
    )

    private fun EventSettings.toEntity() = EventEntity(
        name = details.name,
        dateTimeMillis = details.dateTimeMillis,
        location = details.location,
        giftValueCents = details.giftValueCents,
        notes = details.notes,
        avoidShortCycles = avoidShortCycles,
        cycleGuarantee = cycleGuarantee?.name,
    )

    private fun ParticipantEntity.toStatus() = ParticipantStatus(
        participant = Participant(id = id, name = name, phone = phone),
        receiverId = receiverId,
        messageSent = messageSent,
    )
}
