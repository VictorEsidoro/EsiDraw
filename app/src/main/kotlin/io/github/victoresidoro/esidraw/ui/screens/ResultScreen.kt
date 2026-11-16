package io.github.victoresidoro.esidraw.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.victoresidoro.esidraw.domain.engine.DrawValidation
import io.github.victoresidoro.esidraw.domain.engine.DrawValidator
import io.github.victoresidoro.esidraw.domain.model.DrawHistory
import io.github.victoresidoro.esidraw.ui.EsiDrawUiState
import io.github.victoresidoro.esidraw.ui.EsiDrawViewModel
import io.github.victoresidoro.esidraw.ui.components.ScreenHeader
import io.github.victoresidoro.esidraw.ui.components.SectionCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFormat get() = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR"))

/** Sorteios feitos, por data, cada um conferido de forma independente. */
@Composable
fun ResultScreen(
    state: EsiDrawUiState,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var viewing by remember { mutableStateOf<DrawHistory?>(null) }
    var confirmView by remember { mutableStateOf<DrawHistory?>(null) }
    var confirmDelete by remember { mutableStateOf<DrawHistory?>(null) }
    val organizerPlays = state.event.organizerParticipates
    val currentId = if (state.drawStarted) state.history.firstOrNull()?.id else null

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(
            "Resultado",
            if (state.history.isEmpty()) "Nenhum sorteio feito" else "${state.history.size} sorteio(s), do mais recente ao mais antigo",
        )

        if (state.history.isEmpty()) {
            SectionCard("Nenhum sorteio ainda", Icons.Outlined.CheckCircle) {
                Text(
                    "Quando você sortear, o resultado fica guardado aqui com a data e é conferido automaticamente: " +
                        "ninguém sem par, ninguém sorteado duas vezes e ninguém tirando a si mesmo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Justify,
                )
            }
        }

        state.history.forEach { draw ->
            val isCurrent = draw.id == currentId
            val validation = validate(state, draw, isCurrent)
            SectionCard(dateFormat.format(Date(draw.createdAt)), Icons.Outlined.CheckCircle) {
                if (isCurrent) {
                    Text(
                        "Sorteio em uso (é o das mensagens do WhatsApp).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                ValidationList(validation)
                EsiDrawViewModel.guaranteeText(draw.guarantee).takeIf { it.isNotEmpty() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Justify,
                    )
                }
                if (isCurrent && state.pending.isNotEmpty()) {
                    Text(
                        "${state.pending.size} pessoa(s) entraram depois e ainda estão fora do sorteio: " +
                            "use Incluir no sorteio na aba Sorteio.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Justify,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { if (organizerPlays) confirmView = draw else viewing = draw },
                        modifier = Modifier.weight(1f),
                    ) { Text(if (organizerPlays) "Espiar" else "Ver resultado") }
                    OutlinedButton(onClick = { confirmDelete = draw }, modifier = Modifier.weight(1f)) { Text("Apagar") }
                }
            }
        }
    }

    confirmView?.let { draw ->
        AlertDialog(
            onDismissRequest = { confirmView = null },
            title = { Text("Ver quem tirou quem?") },
            text = {
                Text(
                    "Isso mostra TODO o resultado e acaba com a surpresa. Só continue se for mesmo necessário.",
                    textAlign = TextAlign.Justify,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmView = null
                    viewing = draw
                }) { Text("Ver") }
            },
            dismissButton = { TextButton(onClick = { confirmView = null }) { Text("Cancelar") } },
        )
    }

    viewing?.let { draw ->
        FullResultDialog(
            title = dateFormat.format(Date(draw.createdAt)),
            lines = draw.entries.map { it.giverName to it.receiverName },
            onDismiss = { viewing = null },
        )
    }

    confirmDelete?.let { draw ->
        val isCurrent = draw.id == currentId
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Apagar este sorteio?") },
            text = {
                Text(
                    if (isCurrent) {
                        "Este é o sorteio em uso. Apagar também descarta o resultado atual e o status de envio, " +
                            "para você sortear de novo. Quem já recebeu a mensagem ficará com o resultado antigo."
                    } else {
                        "Só some da lista. O sorteio em uso não muda."
                    },
                    textAlign = TextAlign.Justify,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    onDelete(draw.id)
                }) { Text("Apagar") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancelar") } },
        )
    }
}

/** O sorteio em uso é conferido com os dados vivos (os mesmos das mensagens), os antigos pelo que foi guardado. */
private fun validate(state: EsiDrawUiState, draw: DrawHistory, isCurrent: Boolean): DrawValidation {
    if (isCurrent) {
        val byId = state.participants.associate { it.participant.id to it.participant.name }
        val pairs = state.assigned.map { it.participant.id to checkNotNull(it.receiverId) }
        return DrawValidator.validate(
            pairs = pairs,
            people = state.assigned.map { it.participant.id }.toSet(),
            rules = state.rules,
            nameOf = { byId[it] ?: "#$it" },
        )
    }
    val names = HashMap<Long, String>()
    draw.entries.forEach {
        names[it.giverId] = it.giverName
        names[it.receiverId] = it.receiverName
    }
    return DrawValidator.validate(
        pairs = draw.entries.map { it.giverId to it.receiverId },
        people = names.keys,
        nameOf = { names[it] ?: "#$it" },
    )
}

@Composable
private fun ValidationList(validation: DrawValidation) {
    Text(
        if (validation.valid) "Sorteio validado" else "Sorteio com problema",
        style = MaterialTheme.typography.titleSmall,
        color = if (validation.valid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
    )
    validation.checks.forEach { check ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Icon(
                if (check.ok) Icons.Outlined.CheckCircle else Icons.Outlined.Warning,
                contentDescription = if (check.ok) "ok" else "erro",
                tint = if (check.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            Column {
                Text(check.label, style = MaterialTheme.typography.bodyMedium)
                check.detail?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/** Resultado completo em tela cheia, com rolagem: cabe qualquer número de pessoas. */
@Composable
fun FullResultDialog(
    title: String,
    lines: List<Pair<String, String>>,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Resultado", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "$title · ${lines.size} pessoas",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    itemsIndexed(lines) { index, (giver, receiver) ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Text("${index + 1}. $giver", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "→ $receiver",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Fechar") }
            }
        }
    }
}
