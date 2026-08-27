package com.mmg.manahub.core.ui.components.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.mmg.manahub.core.ui.theme.magicColors

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
    titleColor: Color = MaterialTheme.magicColors.goldMtg,
    iconColor: Color = titleColor,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(!collapsedByDefault) }
    val mc = MaterialTheme.magicColors

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = mc.surface,
        border = BorderStroke(0.5.dp, mc.surfaceVariant.copy(alpha = 0.5f)),
        shadowElevation = if (expanded) 1.dp else 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            SectionHeader(
                title = title,
                expanded = expanded,
                onToggle = { expanded = !expanded },
                icon = icon,
                titleColor = titleColor,
                iconColor = iconColor,
            )
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content,
                )
            }
        }
    }
}
