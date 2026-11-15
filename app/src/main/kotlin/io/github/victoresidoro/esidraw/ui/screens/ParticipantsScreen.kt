package io.github.victoresidoro.esidraw.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.victoresidoro.esidraw.data.contacts.ContactsReader
import io.github.victoresidoro.esidraw.data.vcard.ImportedContact
import io.github.victoresidoro.esidraw.domain.model.ParticipantStatus
import io.github.victoresidoro.esidraw.domain.phone.PhoneNumber
import io.github.victoresidoro.esidraw.ui.components.ScreenHeader
import io.github.victoresidoro.esidraw.ui.components.SectionCard

@Composable
fun ParticipantsScreen(
    participants: List<ParticipantStatus>,
    onAdd: (name: String, phone: String, onDone: () -> Unit) -> Unit,
    onImport: (List<ImportedContact>) -> Unit,
    onRemove: (Long) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }

    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val contact = runCatching { ContactsReader.read(context.contentResolver, uri) }.getOrNull()
        if (contact == null) onMessage("Não consegui ler esse contato.") else onImport(listOf(contact))
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pickContact.launch(null) else onMessage("Sem permissão da agenda. Você ainda pode digitar o contato.")
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { ScreenHeader("Participantes", "${participants.size} na lista") }

        item {
            SectionCard("Adicionar", Icons.Outlined.Add) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Telefone com DDD") },
                    placeholder = { Text("(31) 98888-7777") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            onAdd(name, phone) {
                                name = ""
                                phone = ""
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Adicionar") }
                    OutlinedButton(
                        onClick = {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.READ_CONTACTS,
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) pickContact.launch(null) else askPermission.launch(Manifest.permission.READ_CONTACTS)
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Da agenda") }
                }
            }
        }

        item {
            SectionCard("Do WhatsApp", Icons.Outlined.Info) {
                Text(
                    "No WhatsApp, abra os dados do contato, use a opção de compartilhar o contato e escolha o EsiDraw. " +
                        "Ele entra na lista sozinho. Vários contatos de uma vez também funcionam.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SectionCard("Lista", Icons.Outlined.Person) {
                if (participants.isEmpty()) {
                    Text(
                        "Ninguém ainda. Adicione pelo menos 3 pessoas para um sorteio com graça.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        items(participants, key = { it.participant.id }) { status ->
            val p = status.participant
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                        Text(
                            PhoneNumber.format(p.phone),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onRemove(p.id) }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Remover ${p.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}
