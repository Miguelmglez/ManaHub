package com.mmg.manahub.feature.tournament.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.BottomSheetShape
import com.mmg.manahub.core.ui.theme.ButtonShape
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.online.presentation.lobby.OnlineJoinSheet

/**
 * A bottom sheet that provides the entry points for the tournament flow.
 *
 * Offers three actions:
 * 1. Create a local tournament — navigates to [TournamentSetupScreen].
 * 2. Host online tournament — currently shows a "coming soon" toast.
 * 3. Join online tournament — opens [OnlineJoinSheet] nested inside.
 *
 * Also shows a list of recent tournaments (via [TournamentListViewModel]) or a
 * fallback "View all tournaments" button.
 *
 * @param initialDisplayName Player display name inherited from the setup screen.
 * @param initialThemeKey Player theme key inherited from the setup screen.
 * @param onDismiss Called when this sheet should close.
 * @param onCreateLocal Called when the user taps "Create local tournament".
 * @param onOpenTournament Called with the tournament ID when the user taps a recent tournament.
 * @param onNavigateToTournamentList Called when the user taps the fallback "View all" button.
 * @param onOnlineJoinGameStart Called when an online game successfully starts from this sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TournamentsSheet(
    initialDisplayName: String = "",
    initialThemeKey: String = "Crimson",
    onDismiss: () -> Unit,
    onCreateLocal: () -> Unit,
    onOpenTournament: (Long) -> Unit,
    onNavigateToTournamentList: () -> Unit = {},
    onOnlineJoinGameStart: (sessionId: String, slotIndex: Int, mode: String, playerCount: Int) -> Unit,
    viewModel: TournamentListViewModel = hiltViewModel(),
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    val tournaments by viewModel.tournaments.collectAsStateWithLifecycle()

    var showJoinOnlineSheet by remember { mutableStateOf(false) }
    var showCreateOnlineStub by remember { mutableStateOf(false) }
    val toastState = rememberMagicToastState()
    // Resolved in composable scope; the LaunchedEffect below cannot call stringResource directly.
    val comingSoonMsg = stringResource(R.string.tournaments_sheet_online_coming_soon)

    LaunchedEffect(showCreateOnlineStub) {
        if (showCreateOnlineStub) {
            toastState.show(comingSoonMsg, MagicToastType.INFO)
            showCreateOnlineStub = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = mc.backgroundSecondary,
        shape = BottomSheetShape,
        contentWindowInsets = { WindowInsets(0) },
        dragHandle = {
            Surface(
                modifier = Modifier.padding(vertical = MaterialTheme.spacing.md),
                color = mc.textDisabled.copy(alpha = 0.4f),
                shape = CircleShape
            ) {
                Box(modifier = Modifier.size(width = 32.dp, height = 4.dp))
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.spacing.xl, vertical = MaterialTheme.spacing.sm)
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
                ) {
                    Icon(
                        Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = mc.primaryAccent,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = stringResource(R.string.tournaments_sheet_title),
                        style = ty.titleLarge,
                        color = mc.textPrimary,
                    )
                }

                Spacer(Modifier.height(MaterialTheme.spacing.xl))

                // Action rows
                SheetActionRow(
                    emoji = "🏟️",
                    title = stringResource(R.string.tournaments_sheet_create_local_title),
                    subtitle = stringResource(R.string.tournaments_sheet_create_local_subtitle),
                    onClick = onCreateLocal,
                )

                SheetActionRow(
                    emoji = "🏆",
                    title = stringResource(R.string.tournaments_sheet_host_online_title),
                    subtitle = stringResource(R.string.tournaments_sheet_host_online_subtitle),
                    onClick = { showCreateOnlineStub = true },
                )

                SheetActionRow(
                    emoji = "🔗",
                    title = stringResource(R.string.tournaments_sheet_join_online_title),
                    subtitle = stringResource(R.string.tournaments_sheet_join_online_subtitle),
                    onClick = { showJoinOnlineSheet = true },
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = MaterialTheme.spacing.xl),
                    color = mc.surfaceVariant.copy(alpha = 0.5f),
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
                    modifier = Modifier.padding(bottom = MaterialTheme.spacing.md)
                ) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        tint = mc.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.tournaments_sheet_recent_header).uppercase(),
                        style = ty.labelLarge,
                        color = mc.textSecondary,
                    )
                }

                if (tournaments.isNotEmpty()) {
                    // Intentionally capped at take(3): a bounded list inside a verticalScroll Column.
                    // Keep it bounded — nesting a LazyColumn in a scrollable Column would crash.
                    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)) {
                        tournaments.take(3).forEach { tournament ->
                            Surface(
                                color = mc.surface.copy(alpha = 0.5f),
                                shape = CardShape,
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, mc.surfaceVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenTournament(tournament.id) },
                            ) {
                                Row(
                                    modifier = Modifier.padding(
                                        horizontal = MaterialTheme.spacing.lg,
                                        vertical   = MaterialTheme.spacing.md,
                                    ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = tournament.name,
                                            style = ty.bodyLarge,
                                            color = mc.textPrimary,
                                        )
                                        Text(
                                            text = tournamentStatusLabel(tournament.status),
                                            style = ty.labelSmall,
                                            color = mc.primaryAccent,
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = mc.textDisabled,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = onNavigateToTournamentList,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = mc.primaryAccent,
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, mc.primaryAccent.copy(alpha = 0.5f)),
                        shape = ButtonShape,
                    ) {
                        Text(
                            text = stringResource(R.string.tournaments_sheet_view_all),
                            style = ty.labelLarge,
                            color = mc.primaryAccent,
                        )
                    }
                }

                Spacer(Modifier.height(MaterialTheme.spacing.xl))
            }

            MagicToastHost(
                state = toastState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = MaterialTheme.spacing.sm),
            )
        }
    }

    // Nested join sheet
    if (showJoinOnlineSheet) {
        OnlineJoinSheet(
            initialDisplayName = initialDisplayName,
            initialThemeKey = initialThemeKey,
            onDismiss = { showJoinOnlineSheet = false },
            onGameStart = onOnlineJoinGameStart,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Private sub-composables
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A tappable row with an emoji icon, title, subtitle, and a trailing chevron.
 */
@Composable
private fun SheetActionRow(
    emoji: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = CardShape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(vertical = MaterialTheme.spacing.md),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Emoji icon in a circle
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(mc.surfaceVariant.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = emoji, fontSize = 24.sp)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = ty.titleMedium,
                    color = mc.textPrimary,
                )
                Text(
                    text = subtitle,
                    style = ty.bodySmall,
                    color = mc.textSecondary,
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = mc.textDisabled,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
