package io.github.victoresidoro.esidraw.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.victoresidoro.esidraw.data.contacts.AgendaContact
import io.github.victoresidoro.esidraw.data.contacts.AgendaGroup
import io.github.victoresidoro.esidraw.data.contacts.ContactsReader
import io.github.victoresidoro.esidraw.data.vcard.ImportedContact
import io.github.victoresidoro.esidraw.domain.model.Participant
import io.github.victoresidoro.esidraw.domain.phone.PhoneNumber
import io.github.victoresidoro.esidraw.domain.phone.TextListParser
import io.github.victoresidoro.esidraw.ui.components.RoundedTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer

/** Tela cheia com a agenda: busca, filtro por rótulo e caixinhas para importar vários contatos de uma vez. */
@Composable
fun ContactPickerDialog(
    onDismiss: () -> Unit,
    onImport: (List<ImportedContact>) -> Unit,
) {
    val resolver = LocalContext.current.contentResolver
    var contacts by remember { mutableStateOf<List<AgendaContact>?>(null) }
    var groups by remember { mutableStateOf<List<AgendaGroup>>(emptyList()) }
    var failed by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var label by remember { mutableStateOf<AgendaGroup?>(null) }
    var labelMenu by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Long>()) }

    LaunchedEffect(Unit) {
        try {
            withContext(Dispatchers.IO) {
                contacts = ContactsReader.listContacts(resolver)
                groups = ContactsReader.listGroups(resolver)
            }
        } catch (_: Exception) {
            failed = true
        }
    }

    val all = contacts.orEmpty()
    val needle = remember(query) { fold(query) }
    val visible = all.filter { c ->
        (label == null || c.groupIds.any { it in label!!.ids }) &&
            (needle.isEmpty() || fold(c.name).contains(needle))
    }
    val selectable = visible.filter { it.phone != null }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Escolher da agenda", style = MaterialTheme.typography.headlineSmall)
                RoundedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Buscar por nome") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (groups.isNotEmpty()) {
                    Box {
                        OutlinedButton(onClick = { labelMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Rótulo: ${label?.title ?: "todos os contatos"}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        DropdownMenu(expanded = labelMenu, onDismissRequest = { labelMenu = false }) {
                            DropdownMenuItem(text = { Text("Todos os contatos") }, onClick = {
                                label = null
                                labelMenu = false
                            })
                            groups.forEach { g ->
                                DropdownMenuItem(text = { Text(g.title) }, onClick = {
                                    label = g
                                    labelMenu = false
                                })
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { selected = selected + selectable.map { it.id } }) {
                        Text("Marcar todos (${selectable.size})")
                    }
                    TextButton(onClick = { selected = emptySet() }) { Text("Limpar") }
                }
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        failed -> Text("Não consegui ler a agenda.", color = MaterialTheme.colorScheme.error)
                        contacts == null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        visible.isEmpty() -> Text(
                            "Nenhum contato encontrado.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        else -> LazyColumn {
                            items(visible, key = { it.id }) { c ->
                                val enabled = c.phone != null
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) {
                                            selected = if (c.id in selected) selected - c.id else selected + c.id
                                        },
                                    ) {
                                        Checkbox(
                                            checked = c.id in selected,
                                            onCheckedChange = {
                                                selected = if (it) selected + c.id else selected - c.id
                                            },
                                            enabled = enabled,
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(
                                                c.phone?.let(PhoneNumber::format) ?: "sem telefone com DDD",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancelar") }
                    Button(
                        enabled = selected.isNotEmpty(),
                        onClick = {
                            onImport(all.filter { it.id in selected }.map { ImportedContact(it.name, it.phone) })
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Importar ${selected.size}") }
                }
            }
        }
    }
}

/** Sem acento e em minúsculas, para a busca achar "joao" em "João". */
private fun fold(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().trim()

/** Cola uma lista "Nome telefone" (uma pessoa por linha) e mostra quantas pessoas foram reconhecidas. */
@Composable
fun PasteListDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val entries = remember(text) { TextListParser.parse(text) }
    val withPhone = entries.count { it.phone != null }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Colar lista") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RoundedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Maria Silva - (31) 98888-7777\nJoão 31 97777-6666") },
                    minLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (entries.isNotEmpty()) {
                    Text(
                        "$withPhone com telefone válido" +
                            if (entries.size > withPhone) ", ${entries.size - withPhone} sem telefone com DDD (serão avisados)" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(enabled = entries.isNotEmpty(), onClick = {
                onImport(text)
                onDismiss()
            }) { Text("Importar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun EditParticipantDialog(
    participant: Participant,
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String) -> Unit,
) {
    var name by remember { mutableStateOf(participant.name) }
    var phone by remember { mutableStateOf(PhoneNumber.format(participant.phone)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar participante") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RoundedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                RoundedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Telefone com DDD") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { Button(onClick = { onSave(name, phone) }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
