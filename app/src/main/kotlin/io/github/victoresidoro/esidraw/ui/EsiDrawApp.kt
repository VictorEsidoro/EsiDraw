package io.github.victoresidoro.esidraw.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.victoresidoro.esidraw.ui.screens.DrawDispatchScreen
import io.github.victoresidoro.esidraw.ui.screens.EventScreen
import io.github.victoresidoro.esidraw.ui.screens.ParticipantsScreen
import io.github.victoresidoro.esidraw.ui.screens.RulesScreen

private data class TabItem(val tab: Tab, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabItem(Tab.EVENT, "Evento", Icons.Outlined.DateRange),
    TabItem(Tab.PARTICIPANTS, "Pessoas", Icons.Outlined.Person),
    TabItem(Tab.RULES, "Regras", Icons.Outlined.Lock),
    TabItem(Tab.DRAW, "Sorteio", Icons.Outlined.Favorite),
)

@Composable
fun EsiDrawApp(viewModel: EsiDrawViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                tabs.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item.tab,
                        onClick = { viewModel.selectTab(item.tab) },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (!state.loaded) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                when (tab) {
                    Tab.EVENT -> EventScreen(
                        event = state.event,
                        onChange = viewModel::updateEvent,
                    )
                    Tab.PARTICIPANTS -> ParticipantsScreen(
                        participants = state.participants,
                        groups = state.groups,
                        onAdd = { name, phone, onDone -> viewModel.addParticipant(name, phone, onDone) },
                        onImport = viewModel::importContacts,
                        onImportText = viewModel::importText,
                        onUpdate = { id, name, phone, onDone -> viewModel.updateParticipant(id, name, phone, onDone) },
                        onRemove = viewModel::removeParticipant,
                        onSaveGroup = { name, onDone -> viewModel.saveGroup(name, onDone) },
                        onUpdateGroup = viewModel::updateGroup,
                        onLoadGroup = viewModel::loadGroup,
                        onDeleteGroup = viewModel::deleteGroup,
                        onMessage = viewModel::notify,
                    )
                    Tab.RULES -> RulesScreen(
                        participants = state.participants,
                        rules = state.rules,
                        onAddBlock = viewModel::addBlock,
                        onRemoveBlocks = viewModel::removeBlocks,
                        onAddForced = viewModel::addForced,
                        onRemoveForced = viewModel::removeForced,
                    )
                    Tab.DRAW -> DrawDispatchScreen(
                        state = state,
                        onDraw = viewModel::runDraw,
                        onClearDraw = viewModel::clearDraw,
                        onSetSent = viewModel::setMessageSent,
                        onMessage = viewModel::notify,
                    )
                }
            }
        }
    }
}
