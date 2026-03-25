package com.mmg.magicfolder.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mmg.magicfolder.R
import com.mmg.magicfolder.app.navigation.Screen
import com.mmg.magicfolder.core.ui.theme.magicColors
import com.mmg.magicfolder.core.ui.theme.magicTypography

// ═══════════════════════════════════════════════════════════════════════════════
//  MagicBottomBar
//  Custom 4-slot bottom bar with a gradient FAB in the centre slot.
//
//  Slot layout:  [Collection]  [Stats]  [⚔ PLAY]  [Profile]
//
//  The FAB overflows the top of the bar by 10 dp.
//  The outer Box does NOT clip, so the overflow is visible.
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun MagicBottomBar(
    currentRoute:      String?,
    onCollectionClick: () -> Unit,
    onStatsClick:      () -> Unit,
    onPlayClick:       () -> Unit,
    onProfileClick:    () -> Unit,
    modifier:          Modifier = Modifier,
) {
    val colors     = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    // Total visible height of the bar (not counting gesture-nav insets)
    val barHeight = 64.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        // ── Background surface with top border ───────────────────────────────
        val borderColor = colors.surfaceVariant
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .align(Alignment.BottomCenter)
                .background(colors.backgroundSecondary)
                .drawBehind {
                    drawLine(
                        color       = borderColor,
                        start       = Offset(0f, 0f),
                        end         = Offset(size.width, 0f),
                        strokeWidth = 0.5.dp.toPx(),
                    )
                },
        )

        // ── Nav row ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .align(Alignment.BottomCenter),
            verticalAlignment = Alignment.Bottom,
        ) {
            // Slot 1 — Collection
            BottomBarTab(
                label    = "COLLECTION",
                icon     = Icons.Default.CollectionsBookmark,
                selected = currentRoute == Screen.Collection.route,
                onClick  = onCollectionClick,
                modifier = Modifier.weight(1f),
            )

            // Slot 2 — Stats
            BottomBarTab(
                label    = "STATS",
                icon     = Icons.Default.BarChart,
                selected = currentRoute == Screen.Stats.route,
                onClick  = onStatsClick,
                modifier = Modifier.weight(1f),
            )

            // Slot 3 — Play FAB (overflows upward by 10 dp)
            Box(
                modifier          = Modifier
                    .weight(1f)
                    .height(barHeight),
                contentAlignment  = Alignment.Center,
            ) {
                PlayFab(
                    onClick  = onPlayClick,
                    modifier = Modifier.offset(y = (-10).dp),
                )
            }

            // Slot 4 — Profile
            BottomBarTab(
                label    = "PROFILE",
                icon     = Icons.Default.Person,
                selected = currentRoute == Screen.Profile.route,
                onClick  = onProfileClick,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Individual tab item
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BottomBarTab(
    label:    String,
    icon:     ImageVector,
    selected: Boolean,
    onClick:  () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors        = MaterialTheme.magicColors
    val typography    = MaterialTheme.magicTypography
    val contentColor  = if (selected) colors.primaryAccent else colors.textDisabled
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = interactionSource,
                indication        = ripple(bounded = true),
                onClick           = onClick,
            )
            .padding(bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = label,
            tint               = contentColor,
            modifier           = Modifier.size(20.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text      = label,
            style     = typography.labelSmall,
            color     = contentColor,
            textAlign = TextAlign.Center,
            maxLines  = 1,
        )
        Spacer(Modifier.height(3.dp))
        // Active indicator line
        Box(
            modifier = Modifier
                .width(24.dp)
                .height(if (selected) 2.dp else 0.dp)
                .background(contentColor),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Central gradient Play FAB
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PlayFab(
    onClick:  () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors     = MaterialTheme.magicColors
    val glowColor  = colors.primaryAccent.copy(alpha = 0.35f)
    val gradient   = Brush.linearGradient(
        colors = listOf(colors.lifeNegative, colors.primaryAccent),
        start  = Offset(0f, Float.POSITIVE_INFINITY),
        end    = Offset(Float.POSITIVE_INFINITY, 0f),
    )
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(60.dp)
            .drawBehind {
                // Soft glow ring
                drawCircle(
                    color  = glowColor,
                    radius = size.minDimension / 2f + 10.dp.toPx(),
                )
            }
            .clip(CircleShape)
            .background(gradient)
            .clickable(
                interactionSource = interactionSource,
                indication        = ripple(bounded = true, color = colors.primaryAccent),
                onClick           = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter            = painterResource(R.drawable.ic_sword),
            contentDescription = "Play Game",
            tint               = MaterialTheme.colorScheme.onPrimary,
            modifier           = Modifier.size(24.dp),
        )
    }
}
