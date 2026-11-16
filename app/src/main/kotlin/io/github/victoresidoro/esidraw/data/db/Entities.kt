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
    /** Legado (até a 0.4.x guardava data+hora juntas). Migrado para [dateEpochDay]/[timeMinutes]; não é mais usado. */
    val dateTimeMillis: Long? = null,
    /** Dia do evento (`LocalDate.toEpochDay`), ou `null`. */
    val dateEpochDay: Long? = null,
    /** Horário do evento em minutos desde 00:00, ou `null`. */
    val timeMinutes: Int? = null,
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

/** Grupo salvo de pessoas (ex.: "Grupo da Alegria") para reaproveitar nos sorteios dos próximos anos. */
@Entity(tableName = "contact_group")
data class GroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(
    tableName = "contact_group_member",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["groupId"])],
)
data class GroupMemberEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val name: String,
    val phone: String,
)

/** Resultado da consulta que junta cada grupo com a contagem de membros. */
data class GroupSummaryRow(val id: Long, val name: String, val memberCount: Int)

/** Um sorteio feito, guardado com a data para consulta e para apagar/refazer. Guarda nomes, não telefones. */
@Entity(tableName = "draw_history")
data class DrawHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val guarantee: String,
)

@Entity(
    tableName = "draw_history_entry",
    foreignKeys = [
        ForeignKey(
            entity = DrawHistoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["drawId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["drawId"])],
)
data class DrawHistoryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val drawId: Long,
    val giverId: Long,
    val giverName: String,
    val receiverId: Long,
    val receiverName: String,
)
