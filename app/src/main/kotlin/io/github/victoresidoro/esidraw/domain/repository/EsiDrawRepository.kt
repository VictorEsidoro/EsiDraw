package io.github.victoresidoro.esidraw.domain.repository

import io.github.victoresidoro.esidraw.domain.model.AddParticipantResult
import io.github.victoresidoro.esidraw.domain.model.Contact
import io.github.victoresidoro.esidraw.domain.model.DrawHistory
import io.github.victoresidoro.esidraw.domain.model.GroupSummary
import io.github.victoresidoro.esidraw.domain.model.ImportCounts
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
    val groups: Flow<List<GroupSummary>>

    /** Sorteios feitos, do mais recente para o mais antigo. */
    val history: Flow<List<DrawHistory>>

    /** Aplica [transform] ao evento atual de forma atômica (várias edições seguidas não se perdem). */
    suspend fun updateEvent(transform: (EventSettings) -> EventSettings)

    suspend fun addParticipant(name: String, phone: String): AddParticipantResult
    /** Adiciona várias pessoas de uma vez, pulando as que já estão na lista (mesmo nome e telefone). */
    suspend fun addParticipants(contacts: List<Contact>): ImportCounts

    /** Edita nome e telefone sem refazer o sorteio. Quem trocou de telefone volta a "Pendente". */
    suspend fun updateParticipant(id: Long, name: String, phone: String)

    /**
     * Remove a pessoa (as regras dela saem junto) sem mexer no resultado dos outros. Quem chama decide
     * se o sorteio é descartado ([clearDraw]) ou ajustado ([applyDrawEdit]).
     */
    suspend fun removeParticipant(id: Long)

    /**
     * Grava uma edição do sorteio pronto: remove [removeId] (se houver), grava o novo resultado só de
     * [changedGivers] (eles voltam a "Pendente") e atualiza a garantia contra ciclos curtos.
     */
    suspend fun applyDrawEdit(
        removeId: Long?,
        assignments: Map<Long, Long>,
        changedGivers: Set<Long>,
        guarantee: CycleGuarantee,
    )

    // --- Grupos: a lista atual vira um grupo salvo; no ano seguinte o grupo volta para a lista ---

    suspend fun saveGroup(name: String)

    /** Troca os membros do grupo pela lista atual (quem entrou, saiu ou mudou de telefone). */
    suspend fun updateGroupFromCurrent(groupId: Long)

    /** Substitui a lista atual pelos membros do grupo. Regras e sorteio atuais são descartados. */
    suspend fun loadGroup(groupId: Long)

    suspend fun deleteGroup(groupId: Long)

    suspend fun addBlock(pairing: Pairing, bothWays: Boolean)
    suspend fun removeBlock(pairing: Pairing)
    suspend fun addForced(pairing: Pairing)
    suspend fun removeForced(pairing: Pairing)

    /** Guarda o resultado do sorteio ([assignments]: quem tira → quem foi tirado). */
    suspend fun saveDraw(assignments: Map<Long, Long>, guarantee: CycleGuarantee)
    suspend fun clearDraw()

    /** Apaga um sorteio guardado. Com [discardCurrent], descarta também o sorteio em uso (para refazer). */
    suspend fun deleteDrawHistory(id: Long, discardCurrent: Boolean)
    suspend fun setMessageSent(participantId: Long, sent: Boolean)
}
