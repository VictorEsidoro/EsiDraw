package io.github.victoresidoro.esidraw.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.victoresidoro.esidraw.data.vcard.ImportedContact
import io.github.victoresidoro.esidraw.data.vcard.VCardParser
import io.github.victoresidoro.esidraw.data.vcard.VCardParser.toImported
import io.github.victoresidoro.esidraw.data.sheet.SpreadsheetException
import io.github.victoresidoro.esidraw.data.sheet.SpreadsheetParser
import io.github.victoresidoro.esidraw.domain.engine.DrawEditor
import io.github.victoresidoro.esidraw.domain.engine.DrawEngine
import io.github.victoresidoro.esidraw.domain.engine.EditResult
import io.github.victoresidoro.esidraw.domain.model.AddParticipantResult
import io.github.victoresidoro.esidraw.domain.model.Contact
import io.github.victoresidoro.esidraw.domain.model.DrawHistory
import io.github.victoresidoro.esidraw.domain.model.GroupSummary
import io.github.victoresidoro.esidraw.domain.model.CycleGuarantee
import io.github.victoresidoro.esidraw.domain.model.Diagnosis
import io.github.victoresidoro.esidraw.domain.model.DrawOptions
import io.github.victoresidoro.esidraw.domain.model.DrawOutcome
import io.github.victoresidoro.esidraw.domain.model.DrawRules
import io.github.victoresidoro.esidraw.domain.model.EventSettings
import io.github.victoresidoro.esidraw.domain.model.Pairing
import io.github.victoresidoro.esidraw.domain.model.ParticipantStatus
import io.github.victoresidoro.esidraw.domain.phone.PhoneNumber
import io.github.victoresidoro.esidraw.domain.phone.TextListParser
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

enum class Tab { EVENT, PARTICIPANTS, RULES, DRAW, RESULT }

data class EsiDrawUiState(
    val loaded: Boolean = false,
    val event: EventSettings = EventSettings(),
    val participants: List<ParticipantStatus> = emptyList(),
    val rules: DrawRules = DrawRules(),
    val groups: List<GroupSummary> = emptyList(),
    val history: List<DrawHistory> = emptyList(),
    /** `null` = o sorteio é possível com as regras atuais. */
    val diagnosis: Diagnosis? = null,
    /** Todos os participantes já têm quem tirar. */
    val hasDraw: Boolean = false,
) {
    /** Quem já tem resultado no sorteio. */
    val assigned: List<ParticipantStatus> get() = participants.filter { it.receiverId != null }

    /** O sorteio já foi feito (mesmo que haja gente que entrou depois dele). */
    val drawStarted: Boolean get() = participants.any { it.receiverId != null }

    /** Quem entrou depois do sorteio e ainda não foi encaixado nele. */
    val pending: List<ParticipantStatus> get() = if (drawStarted) participants.filter { it.receiverId == null } else emptyList()

    fun nameOf(id: Long): String = participants.firstOrNull { it.participant.id == id }?.participant?.name ?: "?"
}

class EsiDrawViewModel(private val repository: EsiDrawRepository) : ViewModel() {

    private val engine = DrawEngine()
    private val editor = DrawEditor()
    private val eventLock = Mutex()

    val state: StateFlow<EsiDrawUiState> = combine(
        repository.event,
        repository.participants,
        repository.rules,
        repository.groups,
        repository.history,
    ) { event, participants, rules, groups, history ->
        EsiDrawUiState(
            loaded = true,
            event = event,
            participants = participants,
            rules = rules,
            groups = groups,
            history = history,
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
            val started = state.value.drawStarted
            when (repository.addParticipant(cleanName, phone)) {
                AddParticipantResult.DUPLICATE -> notify("$cleanName já está na lista com esse telefone.")
                AddParticipantResult.ADDED -> {
                    onDone()
                    notify(if (started) "$cleanName entrou fora do sorteio. $INCLUDE_HINT" else "$cleanName adicionado(a).")
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

    /** Lista colada em texto: "Nome telefone", uma pessoa por linha. */
    fun importText(text: String) {
        val entries = TextListParser.parse(text)
        if (entries.isEmpty()) return notify("Não encontrei ninguém no texto colado.")
        importContacts(entries.map { ImportedContact(it.name, it.phone) })
    }

    /** Planilha (.xlsx do modelo do app ou .csv) com nome e telefone. */
    fun importSpreadsheet(open: () -> java.io.InputStream?) {
        viewModelScope.launch {
            val entries = try {
                // Em IO: o arquivo pode estar no Drive e demorar para baixar.
                withContext(Dispatchers.IO) {
                    val stream = open() ?: throw SpreadsheetException("Não consegui abrir o arquivo.")
                    SpreadsheetParser.parse(stream.use { SpreadsheetParser.readLimited(it) })
                }
            } catch (e: SpreadsheetException) {
                return@launch notify(e.message ?: "Não consegui ler a planilha.")
            } catch (e: Exception) {
                return@launch notify("Não consegui ler a planilha (${e.javaClass.simpleName}). Use o modelo do EsiDraw (.xlsx) ou um .csv.")
            }
            if (entries.isEmpty()) return@launch notify("Não encontrei ninguém na planilha.")
            importContacts(entries.map { ImportedContact(it.name, it.phone) })
        }
    }

    fun importContacts(contacts: List<ImportedContact>) {
        viewModelScope.launch {
            val started = state.value.drawStarted
            val valid = mutableListOf<Contact>()
            val withoutPhone = mutableListOf<String>()
            for (contact in contacts) {
                val phone = contact.phone
                if (contact.name.isBlank() || phone == null) {
                    withoutPhone += contact.name.ifBlank { "(sem nome)" }
                } else {
                    valid += Contact(contact.name, phone)
                }
            }
            val counts = repository.addParticipants(valid)
            val parts = buildList {
                if (counts.added > 0) add(if (counts.added == 1) "1 contato adicionado" else "${counts.added} contatos adicionados")
                if (counts.duplicates > 0) add("${counts.duplicates} já estava(m) na lista")
                if (withoutPhone.isNotEmpty()) add("sem telefone com DDD: ${withoutPhone.joinToString(", ")}")
            }
            val summary = parts.joinToString("; ").replaceFirstChar { it.uppercase() } + "."
            notify(if (started && counts.added > 0) "$summary $INCLUDE_HINT" else summary)
        }
    }

    fun updateParticipant(id: Long, name: String, rawPhone: String, onDone: () -> Unit) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return notify("Informe o nome.")
        val phone = PhoneNumber.normalize(rawPhone)
            ?: return notify("Telefone inválido. Informe DDD + número, ex.: (31) 98888-7777.")
        viewModelScope.launch {
            repository.updateParticipant(id, cleanName, phone)
            onDone()
            notify("$cleanName atualizado(a).")
        }
    }

    // --- Grupos ----------------------------------------------------------------------------------

    fun saveGroup(name: String, onDone: () -> Unit) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return notify("Dê um nome ao grupo.")
        if (state.value.participants.isEmpty()) return notify("A lista está vazia: adicione pessoas antes de salvar o grupo.")
        if (state.value.groups.any { it.name.equals(cleanName, ignoreCase = true) }) {
            return notify("Já existe um grupo chamado $cleanName. Use Atualizar nele.")
        }
        viewModelScope.launch {
            repository.saveGroup(cleanName)
            onDone()
            notify("Grupo $cleanName salvo com ${state.value.participants.size} pessoas.")
        }
    }

    fun updateGroup(group: GroupSummary) {
        if (state.value.participants.isEmpty()) return notify("A lista está vazia: o grupo ficaria sem ninguém.")
        viewModelScope.launch {
            repository.updateGroupFromCurrent(group.id)
            notify("Grupo ${group.name} atualizado com a lista atual.")
        }
    }

    fun loadGroup(group: GroupSummary) {
        viewModelScope.launch {
            repository.loadGroup(group.id)
            notify("Grupo ${group.name} carregado: ${group.memberCount} pessoas. Regras e sorteio anteriores foram zerados.")
        }
    }

    fun deleteGroup(group: GroupSummary) {
        viewModelScope.launch {
            repository.deleteGroup(group.id)
            notify("Grupo ${group.name} excluído.")
        }
    }

    fun removeParticipant(id: Long) {
        viewModelScope.launch {
            val s = state.value
            val target = s.participants.firstOrNull { it.participant.id == id } ?: return@launch
            // Sem sorteio, ou pessoa que ainda estava fora dele: sai direto, sem mexer em ninguém.
            if (!s.drawStarted || target.receiverId == null) {
                repository.removeParticipant(id)
                return@launch
            }
            val current = s.assigned.associate { it.participant.id to checkNotNull(it.receiverId) }
            val rules = DrawRules(
                blocks = s.rules.blocks.filterNot { it.giverId == id || it.receiverId == id }.toSet(),
                forced = s.rules.forced.filterNot { it.giverId == id || it.receiverId == id }.toSet(),
            )
            val name = target.participant.name
            when (val result = withContext(Dispatchers.Default) { editor.remove(rules, current, id) }) {
                is EditResult.Success -> {
                    repository.applyDrawEdit(id, result.assignments, result.changedGivers, result.cycleGuarantee)
                    val resend = result.changedGivers.map(s::nameOf).sorted()
                    notify("$name saiu do sorteio. Mande mensagem nova só para: ${resend.joinToString(", ")}.")
                }
                is EditResult.Failure ->
                    notify("Não dá para tirar $name sem mexer em muita gente: ${result.reason} Use Refazer sorteio e depois remova.")
            }
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

    /** Encaixa no sorteio pronto quem entrou depois, mexendo no mínimo de gente. */
    fun includePending() {
        viewModelScope.launch {
            val s = state.value
            val pending = s.pending
            if (!s.drawStarted || pending.isEmpty()) return@launch
            var current = s.assigned.associate { it.participant.id to checkNotNull(it.receiverId) }
            val changed = linkedSetOf<Long>()
            var level = s.event.cycleGuarantee ?: CycleGuarantee.NONE
            val included = mutableListOf<Long>()
            var failure: String? = null
            for (p in pending) {
                val id = p.participant.id
                when (val r = withContext(Dispatchers.Default) { editor.insert(s.rules, current, id) }) {
                    is EditResult.Success -> {
                        current = r.assignments
                        changed += r.changedGivers
                        level = r.cycleGuarantee
                        included += id
                    }
                    is EditResult.Failure -> {
                        failure = "Não consegui encaixar ${p.participant.name}: ${r.reason} Use Refazer sorteio."
                        break
                    }
                }
            }
            if (included.isNotEmpty()) repository.applyDrawEdit(null, current, changed, level)
            val oldChanged = (changed - included.toSet()).map(s::nameOf).sorted()
            val newOnes = included.map(s::nameOf).sorted()
            val parts = buildList {
                if (newOnes.isNotEmpty()) add("${newOnes.joinToString(", ")} entrou(entraram) no sorteio")
                if (oldChanged.isNotEmpty()) add("o resultado de ${oldChanged.joinToString(", ")} mudou")
            }
            val ok = if (parts.isEmpty()) "" else parts.joinToString("; ").replaceFirstChar { it.uppercase() } +
                ". Mande mensagem nova só para quem ficou Pendente."
            notify(listOfNotNull(ok.ifEmpty { null }, failure).joinToString(" "))
        }
    }

    /**
     * Apaga um sorteio da lista de resultados. Se for o mais recente e ainda estiver em uso,
     * o sorteio em uso também é descartado, para poder refazer.
     */
    fun deleteDrawHistory(id: Long) {
        viewModelScope.launch {
            val s = state.value
            val discardCurrent = s.drawStarted && s.history.firstOrNull()?.id == id
            repository.deleteDrawHistory(id, discardCurrent)
            notify(
                if (discardCurrent) "Sorteio apagado e descartado. Vá em Sorteio para sortear de novo."
                else "Sorteio apagado da lista.",
            )
        }
    }

    fun clearDraw() {
        viewModelScope.launch { repository.clearDraw() }
    }

    fun setMessageSent(participantId: Long, sent: Boolean) {
        viewModelScope.launch { repository.setMessageSent(participantId, sent) }
    }

    companion object {
        private const val INCLUDE_HINT = "Vá em Sorteio e toque em Incluir no sorteio."

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
