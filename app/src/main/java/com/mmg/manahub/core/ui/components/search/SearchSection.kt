package com.mmg.manahub.core.ui.components.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mmg.manahub.core.ui.components.SectionHeader
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.spacing

/**
 * A collapsible Advanced Search filter section: title/icon header + collapsible body. The header
 * row itself is rendered by the shared [SectionHeader] component (W0, Deck Analysis Suggestions
 * Tab UI Polish plan, D3) — this composable now only owns the surrounding [Surface]/
 * [AnimatedVisibility] shell and its own `expanded` state, which is unchanged from before.
 */
@Composable
fun SearchSection(
    title: String,
    icon: ImageVector? = null,
    collapsedByDefault: Boolean = false,
    expandedState: Boolean? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    titleColor: Color = MaterialTheme.magicColors.goldMtg,
    iconColor: Color = titleColor,
    content: @Composable ColumnScope.() -> Unit,
) {
    var localExpanded by remember { mutableStateOf(!collapsedByDefault) }
    val expanded = expandedState ?: localExpanded
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        color = mc.surface,
        border = BorderStroke(0.5.dp, mc.surfaceVariant.copy(alpha = 0.5f)),
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(
                title = title,
                expanded = expanded,
                onToggle = {
                    if (onExpandedChange != null) onExpandedChange(!expanded)
                    else localExpanded = !expanded
                },
                icon = icon,
                titleColor = titleColor,
                iconColor = iconColor,
                modifier = Modifier.padding(horizontal = spacing.sm)
            )
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = spacing.lg, end = spacing.lg, bottom = spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                    content = content,
                )
            }
        }
    }
}
