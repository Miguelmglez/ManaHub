package com.mmg.manahub.feature.decks.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mmg.manahub.core.ui.components.ManaSymbolImage
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.feature.decks.presentation.LandDelta

@Composable
fun MagicLandSuggestionStatic(
    deltas: List<LandDelta>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = mc.primaryAccent.copy(alpha = 0.1f),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = mc.primaryAccent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Magic Land Suggestion",
                    style = ty.titleMedium,
                    color = mc.primaryAccent,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
            DeltaList(deltas = deltas)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Tap to apply these adjustments",
                style = ty.labelSmall,
                color = mc.textSecondary
            )
        }
    }
}

@Composable
private fun DeltaList(
    deltas: List<LandDelta>,
    textColor: Color = MaterialTheme.magicColors.textPrimary
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        deltas.forEach { delta ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                ManaSymbolImage(token = delta.manaSymbol, size = 16.dp)
                Spacer(Modifier.width(4.dp))
                Text(
                    text = (if (delta.delta > 0) "+" else "") + delta.delta,
                    style = MaterialTheme.magicTypography.labelMedium,
                    color = textColor,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}













