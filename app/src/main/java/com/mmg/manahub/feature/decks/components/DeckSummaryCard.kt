package com.mmg.manahub.feature.decks.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import com.mmg.manahub.core.domain.model.DeckCard
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography

@Composable
fun DeckSummaryCard(
    totalCards: Int,
    targetCount: Int,
    manaCurve: Map<Int, Int>,
    maxInCurve: Int,
    deckCards: List<DeckCard>,
    modifier: Modifier = Modifier
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    val animatedProgress by animateFloatAsState(
        targetValue = (totalCards.toFloat() / targetCount).coerceIn(0f, 1f),
        label = "DeckProgress"
    )

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp), 
        color = mc.surface
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Re-introducing ManaCurveChart
            ManaCurveChart(
                cards = deckCards,
                modifier = Modifier.fillMaxWidth()
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.deck_total_cards), style = ty.labelMedium, color = mc.textSecondary)
                    Text(
                        text = "$totalCards / $targetCount",
                        style = ty.titleMedium,
                        color = if (totalCards >= targetCount) mc.lifePositive else mc.textPrimary,
                    )
                }
                
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = if (totalCards > targetCount) mc.lifeNegative else mc.primaryAccent,
                    trackColor = mc.surfaceVariant,
                    gapSize = 0.dp,
                    drawStopIndicator = {}
                )
            }
        }
    }
}


















