package io.github.victoresidoro.esidraw.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.victoresidoro.esidraw.domain.model.DrawRules
import io.github.victoresidoro.esidraw.domain.model.Pairing
import io.github.victoresidoro.esidraw.domain.model.ParticipantStatus
import io.github.victoresidoro.esidraw.ui.components.PersonPicker
import io.github.victoresidoro.esidraw.ui.components.ScreenHeader
import io.github.victoresidoro.esidraw.ui.components.SectionCard

@Composable
fun RulesScreen(
    participants: List<ParticipantStatus>,
    rules: DrawRules,
    onAddBlock: (giverId: Long, receiverId: Long, bothWays: Boolean) -> Unit,
    onRemoveBlocks: (List<Pairing>) -> Unit,
    onAddForced: (giverId: Long, receiverId: Long) -> Unit,
    onRemoveForced: (Pairing) -> Unit,
    modifier: Modifier = Modifier,
) {
    val people = participants.map { it.participant }
    fun nameOf(id: Long) = people.firstOrNull { it.id == id }?.name ?: "?"

    var blockGiver by rememberSaveable { mutableStateOf<Long?>(null) }
    var blockReceiver by rememberSaveable { mutableStateOf<Long?>(null) }
    var bothWays by rememberSaveable { mutableStateOf(true) }
    var forcedGiver by rememberSaveable { mutableStateOf<Long?>(null) }
    var forcedReceiver by rememberSaveable { mutableStateOf<Long?>(null) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader("Regras", "Quem não pode tirar quem, e quem já está combinado.")

        if (people.size < 2) {
            Text(
                "Cadastre pelo menos 2 participantes para criar regras.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Justify,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        SectionCard("Bloqueios (brigados)", Icons.Outlined.Lock) {
            Text(
                "A primeira pessoa NÃO pode tirar a segunda.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Justify,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PersonPicker("Quem tira", people, blockGiver, { blockGiver = it }, Modifier.fillMaxWidth(), excludeId = blockReceiver)
            PersonPicker("Não pode tirar", people, blockReceiver, { blockReceiver = it }, Modifier.fillMaxWidth(), excludeId = blockGiver)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = bothWays, onCheckedChange = { bothWays = it })

            }
            Button(
                enabled = blockGiver != null && blockReceiver != null,
                onClick = {
                    onAddBlock(blockGiver!!, blockReceiver!!, bothWays)
                    blockGiver = null
                    blockReceiver = null
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Bloquear") }

            // Pares nos dois sentidos aparecem numa linha só ("A ↔ B"); remover tira os dois.
            val blocks = rules.blocks.toList()
            val rows = blocks.mapNotNull { b ->
                val reverse = Pairing(b.receiverId, b.giverId)
                when {
                    reverse in rules.blocks && b.giverId < b.receiverId ->
                        "${nameOf(b.giverId)} ↔ ${nameOf(b.receiverId)}" to listOf(b, reverse)
                    reverse in rules.blocks -> null
                    else -> "${nameOf(b.giverId)} ✕ ${nameOf(b.receiverId)}" to listOf(b)
                }
            }.sortedBy { it.first }
            rows.forEach { (label, pairings) ->
                RuleRow(label) { onRemoveBlocks(pairings) }
            }
        }

        SectionCard("Pares forçados (combinado)", Icons.Outlined.Favorite) {
            Text(
                "A primeira pessoa TEM que tirar a segunda. Cada pessoa só pode ser tirada por uma.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Justify,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PersonPicker("Quem tira", people, forcedGiver, { forcedGiver = it }, Modifier.fillMaxWidth(), excludeId = forcedReceiver)
            PersonPicker("Tem que tirar", people, forcedReceiver, { forcedReceiver = it }, Modifier.fillMaxWidth(), excludeId = forcedGiver)
            Button(
                enabled = forcedGiver != null && forcedReceiver != null,
                onClick = {
                    onAddForced(forcedGiver!!, forcedReceiver!!)
                    forcedGiver = null
                    forcedReceiver = null
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Forçar par") }

            rules.forced.sortedBy { nameOf(it.giverId) }.forEach { f ->
                RuleRow("${nameOf(f.giverId)} → ${nameOf(f.receiverId)}") { onRemoveForced(f) }
            }
        }
    }
}

@Composable
private fun RuleRow(label: String, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onRemove) {
            Icon(Icons.Outlined.Close, contentDescription = "Remover regra $label", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
