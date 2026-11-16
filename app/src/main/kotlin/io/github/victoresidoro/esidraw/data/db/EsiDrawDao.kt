package io.github.victoresidoro.esidraw.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface EsiDrawDao {

    @Query("SELECT * FROM event WHERE id = $EVENT_ID")
    fun observeEvent(): Flow<EventEntity?>

    @Query("SELECT * FROM event WHERE id = $EVENT_ID")
    suspend fun getEvent(): EventEntity?

    @Upsert
    suspend fun upsertEvent(event: EventEntity)

    @Query("UPDATE event SET cycleGuarantee = NULL")
    suspend fun clearCycleGuarantee()

    @Query("SELECT * FROM participant ORDER BY name COLLATE NOCASE, id")
    fun observeParticipants(): Flow<List<ParticipantEntity>>

    @Query("SELECT * FROM participant")
    suspend fun getParticipants(): List<ParticipantEntity>

    @Insert
    suspend fun insertParticipant(participant: ParticipantEntity): Long

    @Query("DELETE FROM participant WHERE id = :id")
    suspend fun deleteParticipant(id: Long)

    @Query("SELECT * FROM draw_history ORDER BY createdAt DESC, id DESC")
    fun observeHistory(): Flow<List<DrawHistoryEntity>>

    @Query("SELECT * FROM draw_history_entry ORDER BY giverName COLLATE NOCASE, id")
    fun observeHistoryEntries(): Flow<List<DrawHistoryEntryEntity>>

    @Insert
    suspend fun insertHistory(history: DrawHistoryEntity): Long

    @Insert
    suspend fun insertHistoryEntries(entries: List<DrawHistoryEntryEntity>)

    @Query("SELECT id FROM draw_history ORDER BY createdAt DESC, id DESC LIMIT 1")
    suspend fun latestHistoryId(): Long?

    @Query("DELETE FROM draw_history_entry WHERE drawId = :drawId")
    suspend fun deleteHistoryEntries(drawId: Long)

    @Query("UPDATE draw_history SET guarantee = :guarantee WHERE id = :drawId")
    suspend fun setHistoryGuarantee(drawId: Long, guarantee: String)

    @Query("DELETE FROM draw_history WHERE id = :drawId")
    suspend fun deleteHistory(drawId: Long)

    @Query("DELETE FROM participant")
    suspend fun deleteAllParticipants()

    /** Editar nome/telefone não muda o sorteio; só quem trocou de telefone volta a "Pendente". */
    @Query(
        "UPDATE participant SET name = :name, " +
            "messageSent = CASE WHEN phone != :phone THEN 0 ELSE messageSent END, phone = :phone WHERE id = :id",
    )
    suspend fun updateParticipant(id: Long, name: String, phone: String)

    @Query("UPDATE participant SET receiverId = NULL, messageSent = 0")
    suspend fun clearDraw()

    @Query("UPDATE participant SET receiverId = :receiverId, messageSent = 0 WHERE id = :giverId")
    suspend fun setReceiver(giverId: Long, receiverId: Long)

    @Query("UPDATE participant SET messageSent = :sent WHERE id = :id")
    suspend fun setMessageSent(id: Long, sent: Boolean)

    @Query("SELECT * FROM rule")
    fun observeRules(): Flow<List<RuleEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRule(rule: RuleEntity): Long

    @Query(
        "SELECT g.id AS id, g.name AS name, COUNT(m.id) AS memberCount FROM contact_group g " +
            "LEFT JOIN contact_group_member m ON m.groupId = g.id GROUP BY g.id ORDER BY g.name COLLATE NOCASE",
    )
    fun observeGroups(): Flow<List<GroupSummaryRow>>

    @Insert
    suspend fun insertGroup(group: GroupEntity): Long

    @Insert
    suspend fun insertGroupMembers(members: List<GroupMemberEntity>)

    @Query("SELECT * FROM contact_group_member WHERE groupId = :groupId ORDER BY name COLLATE NOCASE")
    suspend fun getGroupMembers(groupId: Long): List<GroupMemberEntity>

    @Query("DELETE FROM contact_group_member WHERE groupId = :groupId")
    suspend fun deleteGroupMembers(groupId: Long)

    @Query("DELETE FROM contact_group WHERE id = :groupId")
    suspend fun deleteGroup(groupId: Long)

    @Query("DELETE FROM rule WHERE forced = :forced AND giverId = :giverId AND receiverId = :receiverId")
    suspend fun deleteRule(forced: Boolean, giverId: Long, receiverId: Long)
}
