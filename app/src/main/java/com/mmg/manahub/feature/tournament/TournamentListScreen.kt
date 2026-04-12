package com.mmg.manahub.feature.tournament

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.data.local.entity.TournamentEntity
import com.mmg.manahub.core.domain.repository.TournamentRepository
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class TournamentListViewModel @Inject constructor(
    private val repository: TournamentRepository,
) : ViewModel() {

    val tournaments: StateFlow<List<TournamentEntity>> =
        repository.observeTournaments()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TournamentListScreen(
    onNavigateBack:     () -> Unit,
    onCreateTournament: () -> Unit,
    onOpenTournament:   (Long) -> Unit,
    viewModel:          TournamentListViewModel = hiltViewModel(),
) {
    val tournaments by viewModel.tournaments.collectAsStateWithLifecycle()
    val mc           = MaterialTheme.magicColors

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Tournaments", style = MaterialTheme.magicTypography.titleLarge, color = mc.textPrimary)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = mc.textPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = onCreateTournament) {
                        Icon(Icons.Default.Add, contentDescription = "New tournament", tint = mc.primaryAccent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = mc.backgroundSecondary),
            )
        },
        containerColor = mc.background,
    ) { padding ->
        if (tournaments.isEmpty()) {
            Box(
                modifier          = Modifier.fillMaxSize().padding(padding),
                contentAlignment  = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⚔", fontSize = androidx.compose.ui.unit.TextUnit(48f, androidx.compose.ui.unit.TextUnitType.Sp))
                    Spacer(Modifier.height(12.dp))
                    Text("No tournaments yet", style = MaterialTheme.magicTypography.titleMedium, color = mc.textSecondary)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onCreateTournament) {
                        Text("Create your first tournament →", color = mc.primaryAccent)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier       = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(tournaments) { tournament ->
                    TournamentListItem(
                        tournament = tournament,
                        onClick    = { onOpenTournament(tournament.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TournamentListItem(
    tournament: TournamentEntity,
    onClick:    () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val statusColor = when (tournament.status) {
        "ACTIVE"   -> mc.primaryAccent
        "FINISHED" -> mc.lifePositive
        else       -> mc.textDisabled
    }
    Surface(
        shape    = RoundedCornerShape(12.dp),
        color    = mc.surface,
        border   = BorderStroke(0.5.dp, mc.surfaceVariant),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier              = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Column {
                Text(tournament.name, style = MaterialTheme.magicTypography.bodyLarge, color = mc.textPrimary)
                Text(
                    "${tournament.format} · ${tournament.structure.replace("_", " ")}",
                    style = MaterialTheme.magicTypography.bodySmall,
                    color = mc.textSecondary,
                )
            }
            Text(
                text  = tournament.status,
                style = MaterialTheme.magicTypography.labelSmall,
                color = statusColor,
            )
        }
    }
}
