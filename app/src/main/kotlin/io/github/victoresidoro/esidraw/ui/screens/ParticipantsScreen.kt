package io.github.victoresidoro.esidraw.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.victoresidoro.esidraw.data.sheet.XlsxTemplate
import io.github.victoresidoro.esidraw.data.vcard.ImportedContact
import io.github.victoresidoro.esidraw.domain.model.GroupSummary
import io.github.victoresidoro.esidraw.domain.model.ParticipantStatus
import io.github.victoresidoro.esidraw.domain.phone.PhoneNumber
import io.github.victoresidoro.esidraw.ui.components.RoundedTextField
import io.github.victoresidoro.esidraw.ui.components.ScreenHeader
import io.github.victoresidoro.esidraw.ui.components.SectionCard

@Composable
fun ParticipantsScreen(
    participants: List<ParticipantStatus>,
    groups: List<GroupSummary>,
    onAdd: (name: String, phone: String, onDone: () -> Unit) -> Unit,
    onImport: (List<ImportedContact>) -> Unit,
    onImportText: (String) -> Unit,
    onImportSpreadsheet: (open: () -> java.io.InputStream?) -> Unit,
    onUpdate: (id: Long, name: String, phone: String, onDone: () -> Unit) -> Unit,
    onRemove: (Long) -> Unit,
    onSaveGroup: (name: String, onDone: () -> Unit) -> Unit,
    onUpdateGroup: (GroupSummary) -> Unit,
    onLoadGroup: (GroupSummary) -> Unit,
    onDeleteGroup: (GroupSummary) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var groupName by rememberSaveable { mutableStateOf("") }

    var pickerOpen by remember { mutableStateOf(false) }
    var pasteOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ParticipantStatus?>(null) }
    var confirmLoad by remember { mutableStateOf<GroupSummary?>(null) }
    var confirmUpdate by remember { mutableStateOf<GroupSummary?>(null) }
    var confirmDelete by remember { mutableStateOf<GroupSummary?>(null) }

    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pickerOpen = true else onMessage("Sem permissão da agenda. Você ainda pode digitar ou colar uma lista.")
    }

    val saveTemplate = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(XlsxTemplate.MIME)) { uri ->
        if (uri != null) {
            val saved = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(XlsxTemplate.build()) } != null
            }.getOrDefault(false)
            onMessage(
                if (saved) "Modelo salvo. Preencha Nome e Telefone e use Importar planilha."
                else "Não consegui salvar o modelo.",
            )
        }
    }
    val pickSheet = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImportSpreadsheet { context.contentResolver.openInputStream(uri) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { ScreenHeader("Participantes", "${participants.size} na lista") }

        item {
            SectionCard("Adicionar", Icons.Outlined.Add) {
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
                    placeholder = { Text("(31) 98888-7777") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        onAdd(name, phone) {
                            name = ""
                            phone = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Adicionar") }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.READ_CONTACTS,
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) pickerOpen = true else askPermission.launch(Manifest.permission.READ_CONTACTS)
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Da agenda") }
                    OutlinedButton(onClick = { pasteOpen = true }, modifier = Modifier.weight(1f)) { Text("Colar lista") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { if (!shareTemplate(context)) onMessage("Não consegui enviar o modelo.") },
                        modifier = Modifier.weight(1f),
                    ) { Text("Enviar modelo") }
                    OutlinedButton(
                        onClick = { saveTemplate.launch(XlsxTemplate.FILE_NAME) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Salvar modelo") }
                }
                OutlinedButton(
                    onClick = { pickSheet.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Importar planilha") }
                Text(
                    "Planilha: envie o modelo (.xlsx) por WhatsApp, e-mail ou Drive, ou salve no celular; preencha Nome e Telefone no Excel ou no Google Planilhas, " +
                        "salve no celular ou no Drive e importe. Aceita também .csv.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Justify,
                )
            }
        }

        item {
            SectionCard("Do WhatsApp", Icons.Outlined.Info) {
                Text(
                    "Contatos recebidos no WhatsApp precisam estar salvos na agenda. Salve-os (botão Adicionar contato), " +
                        "crie um rótulo no Google Contatos, ou marque todos de uma vez em Da agenda. " +
                        "Também dá para compartilhar contatos direto para o EsiDraw.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Justify,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SectionCard("Grupos", Icons.Outlined.Face) {
                Text(
                    "Salve a lista como um grupo (ex.: Grupo da Alegria). No ano seguinte, carregue o grupo, " +
                        "edite quem entrou, saiu ou trocou de telefone, e atualize o grupo.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Justify,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                RoundedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Nome do grupo") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = { onSaveGroup(groupName) { groupName = "" } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Salvar lista atual como grupo") }
                groups.forEach { group ->
                    Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(group.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
                        Text(
                            if (group.memberCount == 1) "1 pessoa" else "${group.memberCount} pessoas",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = {
                                if (participants.isEmpty()) onLoadGroup(group) else confirmLoad = group
                            }) { Text("Usar") }
                            TextButton(onClick = { confirmUpdate = group }) { Text("Atualizar") }
                            TextButton(onClick = { confirmDelete = group }) { Text("Excluir") }
                        }
                    }
                }
            }
        }

        item {
            SectionCard("Lista", Icons.Outlined.Person) {
                Text(
                    if (participants.isEmpty()) {
                        "Ninguém ainda. Adicione pelo menos 3 pessoas para um sorteio com graça."
                    } else {
                        "Toque em uma pessoa para editar o nome ou o telefone."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Justify,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(participants, key = { it.participant.id }) { status ->
            val p = status.participant
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { editing = status }.padding(horizontal = 8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                        Text(
                            PhoneNumber.format(p.phone),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { editing = status }) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Editar ${p.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onRemove(p.id) }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Remover ${p.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }

    if (pickerOpen) ContactPickerDialog(onDismiss = { pickerOpen = false }, onImport = onImport)
    if (pasteOpen) PasteListDialog(onDismiss = { pasteOpen = false }, onImport = onImportText)

    editing?.let { status ->
        EditParticipantDialog(
            participant = status.participant,
            onDismiss = { editing = null },
            onSave = { newName, newPhone -> onUpdate(status.participant.id, newName, newPhone) { editing = null } },
        )
    }

    confirmLoad?.let { group ->
        AlertDialog(
            onDismissRequest = { confirmLoad = null },
            title = { Text("Usar o grupo ${group.name}?") },
            text = { Text("A lista atual, as regras e o sorteio serão substituídos pelas ${group.memberCount} pessoas do grupo.", textAlign = TextAlign.Justify) },
            confirmButton = {
                TextButton(onClick = {
                    confirmLoad = null
                    onLoadGroup(group)
                }) { Text("Usar grupo") }
            },
            dismissButton = { TextButton(onClick = { confirmLoad = null }) { Text("Cancelar") } },
        )
    }
    confirmUpdate?.let { group ->
        AlertDialog(
            onDismissRequest = { confirmUpdate = null },
            title = { Text("Atualizar ${group.name}?") },
            text = { Text("Os membros do grupo passam a ser as ${participants.size} pessoas da lista atual.", textAlign = TextAlign.Justify) },
            confirmButton = {
                TextButton(onClick = {
                    confirmUpdate = null
                    onUpdateGroup(group)
                }) { Text("Atualizar") }
            },
            dismissButton = { TextButton(onClick = { confirmUpdate = null }) { Text("Cancelar") } },
        )
    }
    confirmDelete?.let { group ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Excluir ${group.name}?") },
            text = { Text("Só o grupo salvo some. A lista atual não muda.", textAlign = TextAlign.Justify) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    onDeleteGroup(group)
                }) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancelar") } },
        )
    }
}
