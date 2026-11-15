package io.github.victoresidoro.esidraw.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** O app trabalha com um evento por vez: a linha única tem sempre este id. */
const val EVENT_ID = 1L

@Entity(tableName = "event")
data class EventEntity(
    @PrimaryKey val id: Long = EVENT_ID,
    val name: String = "",
    val dateTimeMillis: Long? = null,
    val location: String = "",
    val giftValueCents: Long? = null,
    val notes: String = "",
    val avoidShortCycles: Boolean = true,
    val organizerParticipates: Boolean = true,
    /** Nome de `CycleGuarantee` do último sorteio, ou `null`. */
    val cycleGuarantee: String? = null,
)

@Entity(tableName = "participant")
data class ParticipantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Só dígitos, com DDI (ex.: 5531988887777). */
    val phone: String,
    /** Resultado do sorteio: id de quem este participante tirou. */
    val receiverId: Long? = null,
    val messageSent: Boolean = false,
)

/** Bloqueio ([forced] = false) ou par forçado ([forced] = true): [giverId] → [receiverId]. */
@Entity(
    tableName = "rule",
    foreignKeys = [
        ForeignKey(
            entity = ParticipantEntity::class,
            parentColumns = ["id"],
            childColumns = ["giverId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ParticipantEntity::class,
            parentColumns = ["id"],
            childColumns = ["receiverId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["forced", "giverId", "receiverId"], unique = true),
        Index(value = ["receiverId"]),
    ],
)
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val forced: Boolean,
    val giverId: Long,
    val receiverId: Long,
)
