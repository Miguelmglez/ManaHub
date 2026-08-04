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
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
 * this (that would double the gutter). The clamp/center at `LARGE` and the full-width fill below it
 * are likewise STRUCTURAL guarantees of this component, not something [content] needs to opt into
 * with its own `fillMaxWidth()` — a screen that fills its own root layout (as any normal screen
 * would) gets the correct clamp/gutter behavior automatically, with no silent collapse-to-intrinsic-
 * width failure mode if it forgets to.
 *
 * [content] does NOT receive scrolling — this shell only clamps/pads, it never wraps [content] in a
 * scroll container (a screen with more content than the viewport, or a short-but-wide viewport like
 * mobile landscape, needs its OWN `Modifier.verticalScroll(rememberScrollState())` on its root
 * layout; see `ThemeShowcaseScreen` for the reference pattern).
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

        // `content` is invoked through a SINGLE movableContentOf-wrapped reference, remembered once
        // (no key), so Compose can RELOCATE its composition subtree between the COMPACT (Column)
        // and MEDIUM/EXPANDED/LARGE (Row) structural parents on a live breakpoint crossing instead
        // of disposing and recreating it. Compose keys composition state by call-site position in
        // the slot table -- calling `content(sizeClass)` directly from the two different `when`
        // branches below would destroy/recreate the whole subtree every time a live resize crosses
        // COMPACT<->MEDIUM. Harmless today (ThemeShowcaseScreen holds no local `remember` state --
        // everything lives in its Koin-provided ViewModel or is hoisted in App()), but the next
        // screen with an ephemeral `remember { mutableStateOf(...) }` (a search draft, a scroll
        // position, an expanded/collapsed flag) would silently lose it on every resize without this.
        val movableContent = remember {
            movableContentOf<ManaWindowSizeClass> { sc -> content(sc) }
        }

        if (sizeClass == ManaWindowSizeClass.COMPACT) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    AdaptiveContentArea(sizeClass) { movableContent(sizeClass) }
                }
                if (navItems.isNotEmpty()) {
                    CompactBottomBar(navItems)
                }
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                if (navItems.isNotEmpty()) {
                    NavigationRailShell(
                        navItems = navItems,
                        expanded = sizeClass != ManaWindowSizeClass.MEDIUM,
                    )
                }
                Box(Modifier.weight(1f)) {
                    AdaptiveContentArea(sizeClass) { movableContent(sizeClass) }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Content area — breakpoint-scaled padding + LARGE clamp/center
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Applies breakpoint-scaled horizontal padding and, at [ManaWindowSizeClass.LARGE], clamps+centers
 * the content column to 1200.dp. The clamp box structurally fills its available width (up to the
 * 1200.dp cap) itself via [Modifier.fillMaxWidth] — [content] does NOT need to (and must not) apply
 * its own outer `fillMaxWidth()`/min-width for the clamp/center to take effect; that guarantee lives
 * here, not as an incidental side effect of what a given screen happens to declare.
 */
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
        // CRITICAL ordering fix (W5b regression sweep, 2026-08-04): `widthIn(max = ...)` MUST be
        // the OUTER modifier here, applied BEFORE `fillMaxWidth()`, not after. Compose modifiers
        // apply constraints outer-to-inner: `fillMaxWidth()` (if placed first/outer) locks the
        // width to the full incoming max as a TIGHT (min == max) constraint before a later
        // `widthIn(max = 1200.dp)` ever sees it, so the cap becomes a structural no-op once the
        // incoming max already exceeds 1200.dp -- the box silently fills the entire available
        // width instead of clamping. Verified live: at a 1920px viewport (LARGE, rail-adjusted
        // content area ~1720.dp), the old `.fillMaxWidth().then(widthIn(max=1200.dp))` ordering
        // rendered content spanning ~1656.dp (edge-to-edge minus only the inner padding), not the
        // documented ~1136.dp (1200.dp minus `spacing.xxl` gutters). Putting `widthIn(max=)`
        // OUTER lets it cap the constraint passed down to `fillMaxWidth()`, which then correctly
        // fills only up to that capped max -- reconfirmed via the same 1920px check afterward
        // (content clamped and centered as designed). Do not reorder this back.
        Box(
            modifier = Modifier
                .then(
                    if (sizeClass == ManaWindowSizeClass.LARGE) {
                        Modifier.widthIn(max = 1200.dp)
                    } else {
                        Modifier
                    },
                )
                .fillMaxWidth()
                .fillMaxHeight(),
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

/**
 * Bar height is 64.dp, deliberately leaner than Android's [com.mmg.manahub.core.ui.components.MagicBottomBar]
 * (`barHeight = 80.dp`) — this is an intentional web-density choice, not a copy/paste drift: web
 * `NavChromeItem`s already guarantee an independent >= 48dp touch target via `defaultMinSize`
 * regardless of the bar's own height, so 64.dp (still on the 8dp grid) keeps the chrome comfortable
 * without carrying over Android's larger touch-first sizing, which this bar doesn't need to match.
 */
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
    // `textDisabled` is semantically for disabled/border content, not "enabled but not the current
    // selection" -- using it here for the unselected nav state fails WCAG contrast against
    // `backgroundSecondary` (measured ~2.2-2.3:1 across themes, well under the 3:1 minimum for
    // UI/icon content), and it's the DEFAULT state for 2 of every 3 nav items. `textSecondary`
    // carries real contrast margin while still reading as visually subordinate to the selected
    // `primaryAccent` item. NOTE: `MagicBottomBar.BottomBarTab` (Android,
    // core/ui/components/MagicBottomBar.kt) has this exact same `selected ? primaryAccent :
    // textDisabled` pattern and the same contrast failure -- pre-existing Android debt, out of
    // scope for this web-only branch; do not fix it here, but do not copy this mistake again either.
    val contentColor = if (item.selected) colors.primaryAccent else colors.textSecondary
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
        .semantics(mergeDescendants = true) {
            contentDescription = item.label
            selected = item.selected
            role = Role.Tab
        }
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
                // maxLines=1 + ellipsis (W5b regression sweep, 2026-08-04): at 320px COMPACT, 6
                // equal-weight bottom-bar items get ~53.dp each (~37.dp after this item's own
                // padding) -- without a line cap, `Text` wraps a longer label like "Collection"/
                // "Account" mid-WORD onto a second line ("Colle"/"ction", "Acco"/"unt", verified
                // live via screenshot), inconsistent with the other, shorter labels that stayed on
                // one line at the same width. Ellipsis reads as "this label is abbreviated" (a
                // known, expected mobile-nav pattern); a mid-word wrap reads as broken.
                Text(
                    text = item.label,
                    style = typography.labelSmall,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
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
