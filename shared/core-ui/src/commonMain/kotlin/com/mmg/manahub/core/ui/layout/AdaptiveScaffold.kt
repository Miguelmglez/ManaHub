package com.mmg.manahub.core.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing

/** A single destination in [AdaptiveScaffold]'s nav chrome. */
data class AdaptiveNavItem(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit,
)

/**
 * The one shared responsive shell for the web target (web roadmap plan §1): renders a bottom
 * bar at [ManaWindowSizeClass.COMPACT], a collapsed icon-only rail at
 * [ManaWindowSizeClass.MEDIUM], and an expanded icon+label rail at
 * [ManaWindowSizeClass.EXPANDED]/[ManaWindowSizeClass.LARGE]. At `LARGE`, content is clamped to
 * 1200.dp and centered with `spacing.xxl` gutters.
 *
 * Screen-level horizontal content padding scales by breakpoint (`spacing.lg` compact /
 * `spacing.xl` medium / `spacing.xxl` expanded+) INSIDE this component — callers receive
 * already-padded content bounds and must never add their own outer horizontal padding on top of
 * this (that would double the gutter).
 *
 * NOTE: this deliberately does NOT reuse [com.mmg.manahub.core.ui.components.MagicBottomBar] —
 * that component is a hardcoded 3-slot phone bar wired to the game/life-counter FAB (out of web v1
 * scope). This nav chrome is built fresh here, generic over an arbitrary [navItems] list, so it is
 * reusable for the real navigation destinations that land in web roadmap W4.
 *
 * NOTE: [com.mmg.manahub.core.ui.theme.MagicShapes] corner radii (`CardShape`, `ChipShape`, etc.)
 * stay CONSTANT across every breakpoint here, intentionally — they are a BRAND token (part of a
 * theme's identity), not a density one, so this component never scales them by size class. This is
 * not an oversight; do not "fix" it in a later density pass.
 *
 * @param navItems the nav destinations to render. An empty list (the default) renders no nav
 *   chrome at all — useful for a screen with no navigation yet (e.g. a pre-auth/showcase screen).
 * @param content receives the resolved [ManaWindowSizeClass] so screens can thread it into
 *   [AdaptiveCardGrid] or their own breakpoint-specific decisions without re-deriving it via a
 *   second `BoxWithConstraints`.
 */
@Composable
fun AdaptiveScaffold(
    navItems: List<AdaptiveNavItem> = emptyList(),
    modifier: Modifier = Modifier,
    content: @Composable (ManaWindowSizeClass) -> Unit,
) {
    val colors = MaterialTheme.magicColors
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(colors.background)) {
        val sizeClass = rememberWindowSizeClass()

        when (sizeClass) {
            ManaWindowSizeClass.COMPACT -> {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        AdaptiveContentArea(sizeClass) { content(sizeClass) }
                    }
                    if (navItems.isNotEmpty()) {
                        CompactBottomBar(navItems)
                    }
                }
            }

            ManaWindowSizeClass.MEDIUM,
            ManaWindowSizeClass.EXPANDED,
            ManaWindowSizeClass.LARGE,
            -> {
                Row(Modifier.fillMaxSize()) {
                    if (navItems.isNotEmpty()) {
                        NavigationRailShell(
                            navItems = navItems,
                            expanded = sizeClass != ManaWindowSizeClass.MEDIUM,
                        )
                    }
                    Box(Modifier.weight(1f)) {
                        AdaptiveContentArea(sizeClass) { content(sizeClass) }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Content area — breakpoint-scaled padding + LARGE clamp/center
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AdaptiveContentArea(
    sizeClass: ManaWindowSizeClass,
    content: @Composable () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val horizontalPadding = when (sizeClass) {
        ManaWindowSizeClass.COMPACT -> spacing.lg
        ManaWindowSizeClass.MEDIUM -> spacing.xl
        ManaWindowSizeClass.EXPANDED, ManaWindowSizeClass.LARGE -> spacing.xxl
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(
            modifier = if (sizeClass == ManaWindowSizeClass.LARGE) {
                Modifier.widthIn(max = 1200.dp).fillMaxHeight()
            } else {
                Modifier.fillMaxWidth().fillMaxHeight()
            },
        ) {
            Box(Modifier.padding(horizontal = horizontalPadding)) {
                content()
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  COMPACT — bottom bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CompactBottomBar(items: List<AdaptiveNavItem>) {
    val colors = MaterialTheme.magicColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(colors.backgroundSecondary),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            NavChromeItem(
                item = item,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                showLabel = true,
                vertical = true,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  MEDIUM/EXPANDED/LARGE — collapsed or expanded rail
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NavigationRailShell(navItems: List<AdaptiveNavItem>, expanded: Boolean) {
    val colors = MaterialTheme.magicColors
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(if (expanded) 200.dp else 80.dp)
            .background(colors.backgroundSecondary)
            .padding(vertical = MaterialTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        navItems.forEach { item ->
            NavChromeItem(
                item = item,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.spacing.sm, vertical = MaterialTheme.spacing.xs),
                showLabel = expanded,
                vertical = !expanded,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Shared nav item — icon-only, icon+label-vertical, or icon+label-horizontal
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NavChromeItem(
    item: AdaptiveNavItem,
    modifier: Modifier = Modifier,
    showLabel: Boolean,
    vertical: Boolean,
) {
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography
    val contentColor = if (item.selected) colors.primaryAccent else colors.textDisabled
    val interactionSource = remember { MutableInteractionSource() }

    // Every nav chrome item stays >= 48dp on its shortest side regardless of icon/label size
    // (CLAUDE.md accessibility rule: every interactive element is at least a 48dp touch target).
    val itemModifier = modifier
        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
        .clickable(
            interactionSource = interactionSource,
            indication = ripple(bounded = true),
            onClick = item.onClick,
        )
        .semantics(mergeDescendants = true) { contentDescription = item.label }
        .padding(MaterialTheme.spacing.sm)

    if (vertical) {
        Column(
            modifier = itemModifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp),
            )
            if (showLabel) {
                Spacer(Modifier.height(MaterialTheme.spacing.xs))
                Text(text = item.label, style = typography.labelSmall, color = contentColor)
            }
        }
    } else {
        Row(
            modifier = itemModifier,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp),
            )
            if (showLabel) {
                Text(text = item.label, style = typography.labelSmall, color = contentColor)
            }
        }
    }
}
