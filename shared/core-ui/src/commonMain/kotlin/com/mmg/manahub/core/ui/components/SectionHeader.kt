package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing

/**
 * Standard collapsible section header — the ONE way to render a tappable "title + optional
 * trailing content + expand/collapse chevron" row anywhere in the app. Consumers: Deck Analysis's
 * per-category rows (Mana Base/Curve/Plan Roles/Synergy/Legality, `feature/decks` app module) and
 * Advanced Search's filter sections ([com.mmg.manahub.core.ui.components.search.SearchSection]).
 *
 * Deliberately renders NO background/surface of its own — every known consumer already sits
 * inside its own container (a [androidx.compose.material3.Surface] for `SearchSection`, the Deck
 * Analysis gradient-wrapped category card for `CardSectionRow`), so a second background here would
 * double-layer. Only a clickable row + optional leading icon + title + trailing slot + chevron.
 *
 * The chevron is owned internally (matching `CollectionScreen.kt`'s `CollectionGroupHeader`
 * precedent) — collapse state itself stays hoisted ([expanded]/[onToggle]), this component is
 * purely presentational. Folds in what used to be the app-module-only `ExpandChevron` composable
 * (`feature/decks/presentation/components/HealthComponents.kt`) — that standalone was deleted once
 * this component took over its one remaining call site, rather than kept as a second way to draw
 * the same chevron.
 */
@Composable
fun SectionHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    titleColor: Color = MaterialTheme.magicColors.goldMtg,
    iconColor: Color = titleColor,
    // Deck Analysis Mana Base sections (W6) need a real mana-symbol SVG (ManaSymbolImage), not an
    // ImageVector -- this is the escape hatch for any leading visual [icon] itself cannot express.
    // Takes priority over [icon] when both are supplied (no known caller does that today).
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(ChipShape)
            .clickable(onClick = onToggle)
            .padding(vertical = MaterialTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = title,
            style = ty.titleMedium,
            color = titleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            trailing()
        }
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = mc.textSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}
