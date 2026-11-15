package io.github.victoresidoro.esidraw.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.victoresidoro.esidraw.data.vcard.ImportedContact
import io.github.victoresidoro.esidraw.data.vcard.VCardParser
import io.github.victoresidoro.esidraw.data.vcard.VCardParser.toImported
import io.github.victoresidoro.esidraw.domain.engine.DrawEngine
import io.github.victoresidoro.esidraw.domain.model.AddParticipantResult
import io.github.victoresidoro.esidraw.domain.model.CycleGuarantee
import io.github.victoresidoro.esidraw.domain.model.Diagnosis
import io.github.victoresidoro.esidraw.domain.model.DrawOptions
import io.github.victoresidoro.esidraw.domain.model.DrawOutcome
import io.github.victoresidoro.esidraw.domain.model.DrawRules
import io.github.victoresidoro.esidraw.domain.model.EventSettings
import io.github.victoresidoro.esidraw.domain.model.Pairing
import io.github.victoresidoro.esidraw.domain.model.ParticipantStatus
import io.github.victoresidoro.esidraw.domain.phone.PhoneNumber
import io.github.victoresidoro.esidraw.domain.repository.EsiDrawRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class Tab { EVENT, PARTICIPANTS, RULES, DRAW }

data class EsiDrawUiState(
    val loaded: Boolean = false,
    val event: EventSettings = EventSettings(),
    val participants: List<ParticipantStatus> = emptyList(),
    val rules: DrawRules = DrawRules(),
    /** `null` = o sorteio é possível com as regras atuais. */
    val diagnosis: Diagnosis? = null,
    /** Todos os participantes já têm quem tirar. */
    val hasDraw: Boolean = false,
) {
    fun nameOf(id: Long): String = participants.firstOrNull { it.participant.id == id }?.participant?.name ?: "?"
}

class EsiDrawViewModel(private val repository: EsiDrawRepository) : ViewModel() {

    private val engine = DrawEngine()
    private val eventLock = Mutex()

    val state: StateFlow<EsiDrawUiState> = combine(
        repository.event,
        repository.participants,
        repository.rules,
    ) { event, participants, rules ->
        EsiDrawUiState(
            loaded = true,
            event = event,
            participants = participants,
            rules = rules,
            diagnosis = engine.analyze(participants.map { it.participant }, rules),
            hasDraw = participants.isNotEmpty() && participants.all { it.receiverId != null },
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EsiDrawUiState())

    private val _tab = MutableStateFlow(Tab.EVENT)
    val tab: StateFlow<Tab> = _tab.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun selectTab(tab: Tab) {
        _tab.value = tab
    }

    fun notify(message: String) {
        _messages.tryEmit(message)
    }

    // --- Evento ----------------------------------------------------------------------------------

    fun updateEvent(transform: (EventSettings) -> EventSettings) {
        viewModelScope.launch { eventLock.withLock { repository.updateEvent(transform) } }
    }

    // --- Participantes ---------------------------------------------------------------------------

    fun addParticipant(name: String, rawPhone: String, onDone: () -> Unit = {}) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return notify("Informe o nome do participante.")
        val phone = PhoneNumber.normalize(rawPhone)
            ?: return notify("Telefone inválido. Informe DDD + número, ex.: (31) 98888-7777.")
        viewModelScope.launch {
            val hadDraw = state.value.hasDraw
            when (repository.addParticipant(cleanName, phone)) {
                AddParticipantResult.DUPLICATE -> notify("$cleanName já está na lista com esse telefone.")
                AddParticipantResult.ADDED -> {
                    onDone()
                    notify(if (hadDraw) DRAW_DISCARDED else "$cleanName adicionado(a).")
                }
            }
        }
    }

    fun importVCard(text: String) {
        viewModelScope.launch {
            val contacts = withContext(Dispatchers.Default) { VCardParser.parse(text).map { it.toImported() } }
            if (contacts.isEmpty()) notify("Não encontrei nenhum contato nesse arquivo.") else importContacts(contacts)
            selectTab(Tab.PARTICIPANTS)
        }
    }

    fun importContacts(contacts: List<ImportedContact>) {
        viewModelScope.launch {
            val hadDraw = state.value.hasDraw
            var added = 0
            var duplicates = 0
            val withoutPhone = mutableListOf<String>()
            for (contact in contacts) {
                val phone = contact.phone
                if (contact.name.isBlank() || phone == null) {
                    withoutPhone += contact.name.ifBlank { "(sem nome)" }
                    continue
                }
                when (repository.addParticipant(contact.name, phone)) {
                    AddParticipantResult.ADDED -> added++
                    AddParticipantResult.DUPLICATE -> duplicates++
                }
            }
            val parts = buildList {
                if (added > 0) add(if (added == 1) "1 contato adicionado" else "$added contatos adicionados")
                if (duplicates > 0) add("$duplicates já estava(m) na lista")
                if (withoutPhone.isNotEmpty()) {
                    add("sem telefone com DDD: ${withoutPhone.joinToString(", ")}")
                }
            }
            val summary = parts.joinToString("; ").replaceFirstChar { it.uppercase() } + "."
            notify(if (hadDraw && added > 0) "$summary $DRAW_DISCARDED" else summary)
        }
    }

    fun removeParticipant(id: Long) {
        viewModelScope.launch {
            val hadDraw = state.value.hasDraw
            repository.removeParticipant(id)
            if (hadDraw) notify(DRAW_DISCARDED)
        }
    }

    // --- Regras ----------------------------------------------------------------------------------

    fun addBlock(giverId: Long, receiverId: Long, bothWays: Boolean) {
        val s = state.value
        val forced = s.rules.forced
        val error = when {
            giverId == receiverId -> "Escolha duas pessoas diferentes."
            Pairing(giverId, receiverId) in forced ->
                "${s.nameOf(giverId)} está forçado(a) a tirar ${s.nameOf(receiverId)}. Remova o par forçado antes."
            bothWays && Pairing(receiverId, giverId) in forced ->
                "${s.nameOf(receiverId)} está forçado(a) a tirar ${s.nameOf(giverId)}. Remova o par forçado antes."
            else -> null
        }
        if (error != null) return notify(error)
        viewModelScope.launch {
            val hadDraw = s.hasDraw
            repository.addBlock(Pairing(giverId, receiverId), bothWays)
            if (hadDraw) notify(DRAW_DISCARDED)
        }
    }

    fun addForced(giverId: Long, receiverId: Long) {
        val s = state.value
        val rules = s.rules
        val error = when {
            giverId == receiverId -> "Ninguém pode tirar a si mesmo."
            rules.forced.any { it.giverId == giverId } ->
                "${s.nameOf(giverId)} já tem um par forçado. Remova-o antes de combinar outro."
            rules.forced.any { it.receiverId == receiverId } -> {
                val other = rules.forced.first { it.receiverId == receiverId }.giverId
                "${s.nameOf(other)} já está forçado(a) a tirar ${s.nameOf(receiverId)}; só uma pessoa pode tirá-lo(a)."
            }
            Pairing(giverId, receiverId) in rules.blocks ->
                "${s.nameOf(giverId)} está bloqueado(a) de tirar ${s.nameOf(receiverId)}. Remova o bloqueio antes."
            else -> null
        }
        if (error != null) return notify(error)
        viewModelScope.launch {
            val hadDraw = s.hasDraw
            repository.addForced(Pairing(giverId, receiverId))
            if (hadDraw) notify(DRAW_DISCARDED)
        }
    }

    fun removeBlocks(pairings: List<Pairing>) {
        viewModelScope.launch {
            val hadDraw = state.value.hasDraw
            pairings.forEach { repository.removeBlock(it) }
            if (hadDraw) notify(DRAW_DISCARDED)
        }
    }

    fun removeForced(pairing: Pairing) {
        viewModelScope.launch {
            val hadDraw = state.value.hasDraw
            repository.removeForced(pairing)
            if (hadDraw) notify(DRAW_DISCARDED)
        }
    }

    // --- Sorteio ---------------------------------------------------------------------------------

    fun runDraw() {
        viewModelScope.launch {
            val s = state.value
            val outcome = withContext(Dispatchers.Default) {
                engine.draw(
                    participants = s.participants.map { it.participant },
                    rules = s.rules,
                    options = DrawOptions(avoidShortCycles = s.event.avoidShortCycles),
                )
            }
            when (outcome) {
                is DrawOutcome.Success -> {
                    repository.saveDraw(outcome.assignments, outcome.cycleGuarantee)
                    notify("Sorteio feito! Agora é só enviar a cada participante.")
                }
                is DrawOutcome.Impossible -> notify("Sorteio impossível. Veja o diagnóstico na tela.")
            }
        }
    }

    fun clearDraw() {
        viewModelScope.launch { repository.clearDraw() }
    }

    fun setMessageSent(participantId: Long, sent: Boolean) {
        viewModelScope.launch { repository.setMessageSent(participantId, sent) }
    }

    companion object {
        private const val DRAW_DISCARDED =
            "O sorteio anterior foi descartado porque os participantes ou as regras mudaram."

        fun factory(repository: EsiDrawRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = EsiDrawViewModel(repository) as T
        }

        fun guaranteeText(guarantee: CycleGuarantee?): String = when (guarantee) {
            CycleGuarantee.SINGLE_CYCLE -> "Ciclo único: todos formam uma só roda, então ninguém descobre o par por eliminação."
            CycleGuarantee.NO_MUTUAL_PAIRS -> "Sem duplas A↔B, mas as regras impediram um ciclo único."
            CycleGuarantee.NONE -> "As regras não permitiram evitar ciclos curtos: pode haver dupla que se tirou."
            null -> ""
        }
    }
}
