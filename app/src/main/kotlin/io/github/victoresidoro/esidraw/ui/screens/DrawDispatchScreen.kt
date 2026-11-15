package io.github.victoresidoro.esidraw.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import io.github.victoresidoro.esidraw.domain.message.WhatsAppMessage
import io.github.victoresidoro.esidraw.ui.EsiDrawUiState
import io.github.victoresidoro.esidraw.ui.EsiDrawViewModel
import io.github.victoresidoro.esidraw.ui.components.NoticeCard
import io.github.victoresidoro.esidraw.ui.components.ScreenHeader
import io.github.victoresidoro.esidraw.ui.components.SectionCard
import io.github.victoresidoro.esidraw.ui.components.StatusBadge
import io.github.victoresidoro.esidraw.ui.theme.onSuccessContainer
import io.github.victoresidoro.esidraw.ui.theme.onWarningContainer
import io.github.victoresidoro.esidraw.ui.theme.successContainer
import io.github.victoresidoro.esidraw.ui.theme.warningContainer

@Composable
fun DrawDispatchScreen(
    state: EsiDrawUiState,
    onDraw: () -> Unit,
    onClearDraw: () -> Unit,
    onSetSent: (id: Long, sent: Boolean) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var revealId by rememberSaveable { mutableStateOf<Long?>(null) }
    var revealShown by rememberSaveable { mutableStateOf(false) }
    var confirmPeek by rememberSaveable { mutableStateOf(false) }
    var showPeek by rememberSaveable { mutableStateOf(false) }
    var confirmRedo by rememberSaveable { mutableStateOf(false) }

    val byId = state.participants.associateBy { it.participant.id }
    val sentCount = state.participants.count { it.messageSent }
    val organizerPlays = state.event.organizerParticipates

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader("Sorteio", "Sorteie e entregue a cada pessoa o nome que ela tirou.")

        val diagnosis = state.diagnosis
        if (diagnosis != null && !state.hasDraw) {
            NoticeCard(Icons.Outlined.Warning, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer) {
                Text("Sorteio impossível com estas regras", style = MaterialTheme.typography.titleMedium)
                diagnosis.issues.forEach { Text("• ${it.message}", style = MaterialTheme.typography.bodyMedium) }
                Text(diagnosis.neutralsMessage, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
        }

        if (!state.hasDraw) {
            SectionCard("Sortear", Icons.Outlined.Favorite) {
                Text(
                    "${state.participants.size} participantes, " +
                        "${state.rules.blocks.size} bloqueio(s), ${state.rules.forced.size} par(es) forçado(s). " +
                        if (organizerPlays) "Ninguém vê o resultado: ele só aparece na mensagem de cada pessoa." else "O resultado só aparece na mensagem de cada pessoa, mas você pode vê-lo depois.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onDraw, enabled = diagnosis == null, modifier = Modifier.fillMaxWidth()) { Text("Sortear") }
            }
            return@Column
        }

        NoticeCard(Icons.Outlined.Info, successContainer, onSuccessContainer) {
            Text("Sorteio feito · $sentCount de ${state.participants.size} entregues", style = MaterialTheme.typography.titleMedium)
            val guarantee = EsiDrawViewModel.guaranteeText(state.event.cycleGuarantee)
            if (guarantee.isNotEmpty()) Text(guarantee, style = MaterialTheme.typography.bodyMedium)
        }

        if (organizerPlays) NoticeCard(Icons.Outlined.Warning, warningContainer, onWarningContainer) {
            Text("Privacidade", style = MaterialTheme.typography.titleMedium)
            Text(
                "O WhatsApp envia a mensagem pela SUA conta e ela fica na sua conversa com cada pessoa: " +
                    "quem abrir o chat vê o nome sorteado. Se você não quer saber, use \"Revelar aqui\": " +
                    "cada pessoa vê o próprio resultado na tela, sem enviar nada.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        SectionCard("Entrega individual", Icons.Outlined.Share) {
            state.participants.forEach { status ->
                val p = status.participant
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (status.messageSent) {
                            StatusBadge("Enviado", successContainer, onSuccessContainer)
                        } else {
                            StatusBadge("Pendente", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val receiver = status.receiverId?.let { byId[it]?.participant }
                                if (receiver == null) return@Button onMessage("Sorteio incompleto. Refaça o sorteio.")
                                val text = WhatsAppMessage.build(p, receiver, state.event.details)
                                if (openWhatsApp(context, WhatsAppMessage.link(p.phone, text))) {
                                    onSetSent(p.id, true)
                                } else {
                                    onMessage("Não consegui abrir o WhatsApp.")
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("WhatsApp") }
                        OutlinedButton(
                            onClick = {
                                revealId = p.id
                                revealShown = false
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Revelar aqui") }
                    }
                    if (status.messageSent) {
                        TextButton(onClick = { onSetSent(p.id, false) }) { Text("Marcar como pendente") }
                    }
                }
            }
        }

        SectionCard("Organizador", Icons.Outlined.Face) {
            OutlinedButton(onClick = { confirmRedo = true }, modifier = Modifier.fillMaxWidth()) { Text("Refazer sorteio") }
            if (organizerPlays) {
                // Participando, ver o resultado estraga a surpresa dele: exige confirmação.
                TextButton(onClick = { confirmPeek = true }, modifier = Modifier.fillMaxWidth()) { Text("Espiar resultado") }
            } else {
                TextButton(onClick = { showPeek = true }, modifier = Modifier.fillMaxWidth()) { Text("Ver resultado") }
            }
        }
    }

    // --- Passa o celular -------------------------------------------------------------------------
    val revealing = revealId?.let { byId[it] }
    if (revealing != null) {
        val receiver = revealing.receiverId?.let { byId[it]?.participant }
        fun close() {
            if (revealShown) onSetSent(revealing.participant.id, true)
            revealId = null
            revealShown = false
        }
        AlertDialog(
            onDismissRequest = { if (!revealShown) close() },
            properties = DialogProperties(dismissOnClickOutside = false),
            title = { Text(if (revealShown) "Seu amigo oculto é" else "Passe o celular") },
            text = {
                if (revealShown) {
                    Text(receiver?.name ?: "?", style = MaterialTheme.typography.headlineMedium)
                } else {
                    Text("Entregue o celular para ${revealing.participant.name}. Só ela(e) deve ver a próxima tela.")
                }
            },
            confirmButton = {
                if (revealShown) {
                    Button(onClick = { close() }) { Text("Entendi, ocultar") }
                } else {
                    Button(onClick = { revealShown = true }) { Text("Sou ${revealing.participant.name}") }
                }
            },
            dismissButton = { if (!revealShown) TextButton(onClick = { close() }) { Text("Cancelar") } },
        )
    }

    // --- Espiar (protegido por confirmação) -----------------------------------------------------
    if (confirmPeek) {
        AlertDialog(
            onDismissRequest = { confirmPeek = false },
            title = { Text("Ver quem tirou quem?") },
            text = { Text("Isso mostra TODO o resultado para você e acaba com a surpresa. Só continue se for mesmo necessário.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmPeek = false
                    showPeek = true
                }) { Text("Ver") }
            },
            dismissButton = { TextButton(onClick = { confirmPeek = false }) { Text("Cancelar") } },
        )
    }
    if (showPeek) {
        AlertDialog(
            onDismissRequest = { showPeek = false },
            title = { Text("Resultado") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.participants.forEach { s ->
                        Text("${s.participant.name} → ${s.receiverId?.let { byId[it]?.participant?.name } ?: "?"}")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPeek = false }) { Text("Fechar") } },
        )
    }

    if (confirmRedo) {
        AlertDialog(
            onDismissRequest = { confirmRedo = false },
            title = { Text("Refazer o sorteio?") },
            text = { Text("O resultado atual e o status de envio serão apagados. Quem já recebeu a mensagem ficará com o resultado antigo.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRedo = false
                    onClearDraw()
                }) { Text("Refazer") }
            },
            dismissButton = { TextButton(onClick = { confirmRedo = false }) { Text("Cancelar") } },
        )
    }
}

/** Abre a conversa no WhatsApp (normal, depois Business, depois o que o sistema escolher). */
private fun openWhatsApp(context: Context, link: String): Boolean {
    val uri = Uri.parse(link)
    for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b", null)) {
        val intent = Intent(Intent.ACTION_VIEW, uri).apply { pkg?.let { setPackage(it) } }
        try {
            context.startActivity(intent)
            return true
        } catch (_: ActivityNotFoundException) {
            // tenta o próximo
        }
    }
    return false
}
