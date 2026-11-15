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

    @Query("DELETE FROM rule WHERE forced = :forced AND giverId = :giverId AND receiverId = :receiverId")
    suspend fun deleteRule(forced: Boolean, giverId: Long, receiverId: Long)
}
