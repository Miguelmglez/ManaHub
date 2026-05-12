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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import com.mmg.manahub.app.navigation.Screen
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography

// ═══════════════════════════════════════════════════════════════════════════════
//  MagicBottomBar
//  Custom 5-slot bottom bar with a gradient FAB in the centre slot.
//
//  Slot layout:  [Collection] [News] [⚔ PLAY FAB] [Draft] [Profile]
//
//  The FAB overflows the top of the bar by 8 dp.
//  The outer Box does NOT clip, so the overflow is visible.
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun MagicBottomBar(
    currentRoute:      String?,
    onCollectionClick: () -> Unit,
    onNewsClick:       () -> Unit,
    onPlayClick:       () -> Unit,
    onDraftClick:      () -> Unit,
    onProfileClick:    () -> Unit,
    modifier:          Modifier = Modifier,
) {
    val colors     = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

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
            // Slot 1 — Collection
            BottomBarTab(
                label    = "FOLDER",
                icon     = Icons.Default.CollectionsBookmark,
                selected = currentRoute == Screen.Collection.route,
                onClick  = onCollectionClick,
                modifier = Modifier.weight(1f),
            )

            /*// Slot 2 — Draft
            BottomBarTab(
                label    = "DRAFT",
                icon     = Icons.Default.Style,
                selected = currentRoute == Screen.Draft.route,
                onClick  = onDraftClick,
                modifier = Modifier.weight(1f),
            )*/

            // Slot 3 — Play FAB (overflows upward by 8 dp)
            Box(
                modifier         = Modifier
                    .weight(1f)
                    .height(barHeight),
                contentAlignment = Alignment.Center,
            ) {
                PlayFab(
                    onClick  = onPlayClick,
                    modifier = Modifier.offset(y = (-8).dp),
                )
            }

            /*// Slot 4 — News

            BottomBarTab(
                label    = "NEWS",
                icon     = Icons.Default.Newspaper,
                selected = currentRoute == Screen.News.route,
                onClick  = onNewsClick,
                modifier = Modifier.weight(1f),
            )*/
            // Slot 5 — Profile
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
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = label,
            tint               = contentColor,
            modifier           = Modifier.size(24.dp),
        )
        Spacer(Modifier.height(4.dp))
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
            painter            = painterResource(R.drawable.ic_battle),
            contentDescription = "Play Game",
            tint               = Color.White,
            modifier           = Modifier.size(28.dp),
        )
    }
}
