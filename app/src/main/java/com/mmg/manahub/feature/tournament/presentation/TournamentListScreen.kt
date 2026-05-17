package com.mmg.manahub.feature.tournament.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.core.data.local.entity.TournamentEntity
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography

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
        contentWindowInsets = WindowInsets.statusBars,
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
            EmptyState(
                icon        = Icons.Default.EmojiEvents,
                title       = "No tournaments yet",
                actionLabel = "Create your first tournament",
                onAction    = onCreateTournament,
                modifier    = Modifier.fillMaxSize().padding(padding),
            )
        } else {
            val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            LazyColumn(
                modifier            = Modifier.fillMaxSize().padding(padding),
                contentPadding      = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + navBarBottom),
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
