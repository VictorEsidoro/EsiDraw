package io.github.victoresidoro.esidraw.data.repo

import androidx.room.withTransaction
import io.github.victoresidoro.esidraw.data.db.EsiDrawDatabase
import io.github.victoresidoro.esidraw.data.db.EventEntity
import io.github.victoresidoro.esidraw.data.db.GroupEntity
import io.github.victoresidoro.esidraw.data.db.GroupMemberEntity
import io.github.victoresidoro.esidraw.data.db.ParticipantEntity
import io.github.victoresidoro.esidraw.data.db.RuleEntity
import io.github.victoresidoro.esidraw.domain.message.EventDetails
import io.github.victoresidoro.esidraw.domain.model.AddParticipantResult
import io.github.victoresidoro.esidraw.domain.model.Contact
import io.github.victoresidoro.esidraw.domain.model.CycleGuarantee
import io.github.victoresidoro.esidraw.domain.model.GroupSummary
import io.github.victoresidoro.esidraw.domain.model.ImportCounts
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

    override val groups: Flow<List<GroupSummary>> =
        dao.observeGroups().map { rows -> rows.map { GroupSummary(it.id, it.name, it.memberCount) } }

    override suspend fun updateEvent(transform: (EventSettings) -> EventSettings) {
        db.withTransaction { writeEvent(transform) }
    }

    override suspend fun addParticipant(name: String, phone: String): AddParticipantResult =
        db.withTransaction {
            val clean = name.trim()
            val duplicate = dao.getParticipants().any { it.name.equals(clean, ignoreCase = true) && it.phone == phone }
            if (duplicate) return@withTransaction AddParticipantResult.DUPLICATE
            // Com sorteio pronto, a pessoa entra "fora do sorteio" e é encaixada depois (DrawEditor).
            dao.insertParticipant(ParticipantEntity(name = clean, phone = phone))
            AddParticipantResult.ADDED
        }

    override suspend fun addParticipants(contacts: List<Contact>): ImportCounts = db.withTransaction {
        val known = dao.getParticipants().map { it.name.lowercase() to it.phone }.toMutableSet()
        var added = 0
        var duplicates = 0
        for (contact in contacts) {
            val name = contact.name.trim()
            if (!known.add(name.lowercase() to contact.phone)) {
                duplicates++
                continue
            }
            dao.insertParticipant(ParticipantEntity(name = name, phone = contact.phone))
            added++
        }
        ImportCounts(added, duplicates)
    }

    override suspend fun updateParticipant(id: Long, name: String, phone: String) {
        dao.updateParticipant(id, name.trim(), phone)
    }

    override suspend fun saveGroup(name: String) {
        db.withTransaction {
            val groupId = dao.insertGroup(GroupEntity(name = name.trim()))
            dao.insertGroupMembers(currentMembers(groupId))
        }
    }

    override suspend fun updateGroupFromCurrent(groupId: Long) {
        db.withTransaction {
            dao.deleteGroupMembers(groupId)
            dao.insertGroupMembers(currentMembers(groupId))
        }
    }

    override suspend fun loadGroup(groupId: Long) {
        db.withTransaction {
            val members = dao.getGroupMembers(groupId)
            dao.deleteAllParticipants() // as regras ligadas a eles saem junto (ON DELETE CASCADE)
            discardDraw()
            members.forEach { dao.insertParticipant(ParticipantEntity(name = it.name, phone = it.phone)) }
        }
    }

    override suspend fun deleteGroup(groupId: Long) {
        dao.deleteGroup(groupId)
    }

    private suspend fun currentMembers(groupId: Long): List<GroupMemberEntity> =
        dao.getParticipants().map { GroupMemberEntity(groupId = groupId, name = it.name, phone = it.phone) }

    override suspend fun removeParticipant(id: Long) {
        db.withTransaction {
            dao.deleteParticipant(id) // regras ligadas a ele saem junto (ON DELETE CASCADE)
        }
    }

    override suspend fun applyDrawEdit(
        removeId: Long?,
        assignments: Map<Long, Long>,
        changedGivers: Set<Long>,
        guarantee: CycleGuarantee,
    ) {
        db.withTransaction {
            removeId?.let { dao.deleteParticipant(it) }
            // setReceiver também zera o "enviado": só quem mudou precisa de mensagem nova.
            changedGivers.forEach { giver -> assignments[giver]?.let { dao.setReceiver(giver, it) } }
            writeEvent { it.copy(cycleGuarantee = guarantee) }
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
        organizerParticipates = organizerParticipates,
        cycleGuarantee = cycleGuarantee?.let { runCatching { CycleGuarantee.valueOf(it) }.getOrNull() },
    )

    private fun EventSettings.toEntity() = EventEntity(
        name = details.name,
        dateTimeMillis = details.dateTimeMillis,
        location = details.location,
        giftValueCents = details.giftValueCents,
        notes = details.notes,
        avoidShortCycles = avoidShortCycles,
        organizerParticipates = organizerParticipates,
        cycleGuarantee = cycleGuarantee?.name,
    )

    private fun ParticipantEntity.toStatus() = ParticipantStatus(
        participant = Participant(id = id, name = name, phone = phone),
        receiverId = receiverId,
        messageSent = messageSent,
    )
}
