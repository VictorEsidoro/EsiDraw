package io.github.victoresidoro.esidraw.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.victoresidoro.esidraw.domain.message.Money
import io.github.victoresidoro.esidraw.domain.model.EventSettings
import io.github.victoresidoro.esidraw.ui.components.RoundedTextField
import io.github.victoresidoro.esidraw.ui.components.ScreenHeader
import io.github.victoresidoro.esidraw.ui.components.SectionCard
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private const val NOTES_LIMIT = 500
private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun EventScreen(
    event: EventSettings,
    onChange: ((EventSettings) -> EventSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // Os textos ficam num estado local (o banco confirma depois) para o cursor não pular ao digitar.
    var name by remember { mutableStateOf(event.details.name) }
    var location by remember { mutableStateOf(event.details.location) }
    var notes by remember { mutableStateOf(event.details.notes) }
    var value by remember { mutableStateOf(event.details.giftValueCents?.let(Money::toInput).orEmpty()) }
    val valueInvalid = value.isNotBlank() && Money.parseCents(value) == null

    val date: LocalDate? = event.details.date
    val time: LocalTime? = event.details.time

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader("Evento", "Estes dados vão na mensagem enviada a cada participante.")

        SectionCard("Detalhes", Icons.Outlined.DateRange) {
            RoundedTextField(
                value = name,
                onValueChange = {
                    name = it
                    onChange { s -> s.copy(details = s.details.copy(name = it)) }
                },
                label = { Text("Nome do evento") },
                placeholder = { Text("Amigo Oculto da Família") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = {
                            val base = date ?: LocalDate.now()
                            DatePickerDialog(
                                context,
                                { _, y, m, d -> onChange { it.copy(details = it.details.copy(date = LocalDate.of(y, m + 1, d))) } },
                                base.year,
                                base.monthValue - 1,
                                base.dayOfMonth,
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(date?.format(dateFormat) ?: "Data") }
                    if (date != null) {
                        TextButton(
                            onClick = { onChange { it.copy(details = it.details.copy(date = null)) } },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Limpar data") }
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = {
                            val base = time ?: LocalTime.of(19, 0)
                            TimePickerDialog(
                                context,
                                { _, h, min -> onChange { it.copy(details = it.details.copy(time = LocalTime.of(h, min))) } },
                                base.hour,
                                base.minute,
                                true,
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(time?.format(timeFormat) ?: "Horário") }
                    if (time != null) {
                        TextButton(
                            onClick = { onChange { it.copy(details = it.details.copy(time = null)) } },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Limpar horário") }
                    }
                }
            }
            Text(
                "Data e horário são opcionais e independentes: preencha só o que já souber. " +
                    "O que ficar vazio não aparece na mensagem.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Justify,
            )
            RoundedTextField(
                value = location,
                onValueChange = {
                    location = it
                    onChange { s -> s.copy(details = s.details.copy(location = it)) }
                },
                label = { Text("Endereço / local") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            RoundedTextField(
                value = value,
                onValueChange = {
                    value = it
                    val cents = Money.parseCents(it)
                    if (cents != null || it.isBlank()) {
                        onChange { s -> s.copy(details = s.details.copy(giftValueCents = cents)) }
                    }
                },
                label = { Text("Valor sugerido do presente (R$)") },
                placeholder = { Text("100,00") },
                singleLine = true,
                isError = valueInvalid,
                supportingText = if (valueInvalid) ({ Text("Use só números, ex.: 100 ou 100,50") }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            RoundedTextField(
                value = notes,
                onValueChange = {
                    val limited = it.take(NOTES_LIMIT)
                    notes = limited
                    onChange { s -> s.copy(details = s.details.copy(notes = limited)) }
                },
                label = { Text("Observações (opcional)") },
                supportingText = { Text("${notes.length}/$NOTES_LIMIT") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SectionCard("Organizador", Icons.Outlined.Person) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Eu participo do sorteio", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (event.organizerParticipates) {
                            "Adicione você mesmo na lista de Pessoas. Para você não saber o resultado dos outros, " +
                                "entregue com \"Revelar aqui\": o WhatsApp deixa a mensagem no seu histórico."
                        } else {
                            "Você só organiza: pode ver o resultado completo e o WhatsApp é a forma mais prática de entregar."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Justify,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = event.organizerParticipates,
                    onCheckedChange = { checked -> onChange { it.copy(organizerParticipates = checked) } },
                )
            }
        }

        SectionCard("Evitar duplas isoladas", Icons.Outlined.Refresh) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Tenta formar uma única roda (A→B→C→…→A), para ninguém descobrir o par por eliminação. " +
                        "Se as regras não permitirem, o app usa o melhor possível e avisa.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Justify,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = event.avoidShortCycles,
                    onCheckedChange = { checked -> onChange { it.copy(avoidShortCycles = checked) } },
                )
            }
        }
    }
}
