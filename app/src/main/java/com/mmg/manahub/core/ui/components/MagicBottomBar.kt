package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Home
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import com.mmg.manahub.app.navigation.Screen
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing

// ═══════════════════════════════════════════════════════════════════════════════
//  MagicBottomBar
//  Custom 3-slot bottom bar with a gradient Play FAB in the centre slot.
//
//  Slot layout:  [Home] [⚔ PLAY FAB] [Library]
//
//  The FAB overflows the top of the bar by 8 dp.
//  The outer Box does NOT clip, so the overflow is visible.
//
//  Home redesign (free-first/account-enhanced): the bar surfaces only the two
//  always-relevant destinations plus the centered Game action. Draft, News,
//  Profile, etc. are reachable from Home modules and the Library sub-tabs, not
//  as permanent tabs.
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun MagicBottomBar(
    currentRoute:   String?,
    onHomeClick:    () -> Unit,
    onPlayClick:    () -> Unit,
    onLibraryClick: () -> Unit,
    modifier:       Modifier = Modifier,
) {
    val colors = MaterialTheme.magicColors

    val barHeight = 80.dp

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
            verticalAlignment    = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            // Slot 1 — Home
            BottomBarTab(
                label    = "HOME",
                icon     = Icons.Default.Home,
                selected = currentRoute == Screen.Home.route,
                onClick  = onHomeClick,
                modifier = Modifier.weight(1f),
            )

            // Slot 2 — Play FAB (overflows upward by 8 dp)
            Box(
                modifier         = Modifier
                    .weight(1f)
                    .height(barHeight),
                contentAlignment = Alignment.Center,
            ) {
                PlayFab(
                    onClick  = onPlayClick,
                    modifier = Modifier.offset(y = -MaterialTheme.spacing.sm),
                )
            }

            // Slot 3 — Library (reuses the existing Collection destination)
            BottomBarTab(
                label    = "LIBRARY",
                icon     = Icons.Default.CollectionsBookmark,
                selected = currentRoute == Screen.Collection.route,
                onClick  = onLibraryClick,
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

    val accessibleLabel = label.lowercase().replaceFirstChar { it.uppercase() }
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = interactionSource,
                indication        = ripple(bounded = true),
                onClick           = onClick,
            )
            .semantics(mergeDescendants = true) { contentDescription = accessibleLabel },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = contentColor,
            modifier           = Modifier.size(24.dp),
        )
        Spacer(Modifier.height(MaterialTheme.spacing.xs))
        Text(
            text      = label,
            style     = typography.labelSmall,
            color     = contentColor,
            textAlign = TextAlign.Center,
            maxLines  = 1,
        )
        
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Central gradient Play FAB — crossed swords icon
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PlayFab(
    onClick:  () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors    = MaterialTheme.magicColors
    val spacing   = MaterialTheme.spacing
    val glowColor = colors.primaryAccent.copy(alpha = 0.35f)
    val gradient  = Brush.linearGradient(
        colors = listOf(colors.primaryAccent, colors.secondaryAccent),
        start  = Offset(0f, Float.POSITIVE_INFINITY),
        end    = Offset(Float.POSITIVE_INFINITY, 0f),
    )
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(60.dp)
            .drawBehind {
                drawCircle(
                    color  = glowColor,
                    radius = size.minDimension / 2f + spacing.sm.toPx(),
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
            painter            = painterResource(R.drawable.ic_battle),
            contentDescription = "Play Game",
            tint               = colors.background,
            modifier           = Modifier.size(28.dp),
        )
    }
}
