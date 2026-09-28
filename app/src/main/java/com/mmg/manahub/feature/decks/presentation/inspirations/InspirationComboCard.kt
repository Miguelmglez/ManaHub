package com.mmg.manahub.feature.decks.presentation.inspirations

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.ui.Res
import com.mmg.manahub.core.ui.components.CardName
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.components.OracleText
import com.mmg.manahub.core.ui.mtg_card_back
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.SmallCardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.decks.domain.inspirations.ComboReadiness
import com.mmg.manahub.feature.decks.domain.inspirations.OwnedComboView
import org.jetbrains.compose.resources.painterResource

private val COMBO_TILE_WIDTH = 72.dp
private val COMBO_TILE_HEIGHT = 104.dp

/** One Spellbook combo: what it produces, how close the collection is, every piece as a tile (missing ones dimmed), and "Add to selection". */
@Composable
internal fun InspirationComboCard(
    view: OwnedComboView,
    resolveCard: (String) -> Card?,
    isSelected: Boolean,
    onInspect: (Card) -> Unit,
    onAddToSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    var showExplanation by rememberSaveable(view.combo.id) { mutableStateOf(false) }
    val missing = view.missingCardNames.map { it.lowercase() }.toSet()

    Surface(
        shape = CardShape,
        color = mc.backgroundSecondary,
        border = BorderStroke(1.dp, mc.surfaceVariant.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Text(
                    text = view.combo.produces.joinToString(", ").ifBlank { stringResource(R.string.deck_inspirations_combo_untitled) },
                    style = ty.titleMedium,
                    color = mc.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                ReadinessBadge(view)
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), contentPadding = PaddingValues(vertical = spacing.xxs)) {
                // Index in the key: Spellbook can list the same card twice in one variant.
                itemsIndexed(view.combo.cardNames, key = { index, name -> "${view.combo.id}:$index:$name" }) { _, name ->
                    ComboPieceTile(
                        name = name,
                        card = resolveCard(name),
                        isMissing = name.lowercase() in missing,
                        onClick = onInspect,
                    )
                }
            }

            if (view.combo.description.isNotBlank()) {
                val toggleLabel = stringResource(R.string.deck_inspirations_combo_how_it_works)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ChipShape)
                        .clickable(onClickLabel = toggleLabel) { showExplanation = !showExplanation }
                        .heightIn(min = 48.dp)
                        .padding(horizontal = spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = toggleLabel, style = ty.labelLarge, color = mc.primaryAccent, modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = if (showExplanation) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = mc.primaryAccent,
                    )
                }
                AnimatedVisibility(visible = showExplanation) {
                    // Spellbook explanations quote oracle text, mana tokens included.
                    OracleText(text = view.combo.description, style = ty.bodySmall.copy(color = mc.textSecondary))
                }
            }

            MagicCtaButton(
                onClick = onAddToSelection,
                text = stringResource(if (isSelected) R.string.deck_inspirations_combo_added else R.string.deck_inspirations_add_to_selection),
                enabled = !isSelected,
                style = if (isSelected) MagicCtaStyle.Ghost else MagicCtaStyle.Outlined,
                color = if (isSelected) MagicCtaColor.Success else MagicCtaColor.Primary,
                icon = { Icon(if (isSelected) Icons.Default.Check else Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ReadinessBadge(view: OwnedComboView) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val (label, accent) = when (view.readiness) {
        ComboReadiness.READY -> stringResource(R.string.deck_inspirations_combo_ready_badge) to mc.lifePositive
        else -> stringResource(R.string.deck_inspirations_combo_missing_badge, view.missingCardNames.size) to mc.goldMtg
    }
    // Bordered surface with textPrimary ink: an accent-on-tonal-accent chip fails AA on HallowedPrint.
    Surface(shape = ChipShape, color = mc.backgroundSecondary, border = BorderStroke(1.dp, accent)) {
        Text(
            text = label,
            style = ty.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = mc.textPrimary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun ComboPieceTile(
    name: String,
    card: Card?,
    isMissing: Boolean,
    onClick: (Card) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val missingLabel = stringResource(R.string.deck_inspirations_combo_missing_piece)
    val description = if (isMissing) "$name, $missingLabel" else name

    Column(
        modifier = Modifier.width(COMBO_TILE_WIDTH),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
    ) {
        Box(
            modifier = Modifier
                .size(width = COMBO_TILE_WIDTH, height = COMBO_TILE_HEIGHT)
                .clip(SmallCardShape)
                .background(mc.surfaceVariant)
                .then(if (isMissing) Modifier.border(2.dp, mc.goldMtg, SmallCardShape) else Modifier)
                .then(if (card != null) Modifier.clickable(role = Role.Button) { onClick(card) } else Modifier)
                .semantics { contentDescription = description },
        ) {
            if (card != null) {
                AsyncImage(
                    model = card.imageNormal,
                    contentDescription = null,
                    placeholder = painterResource(Res.drawable.mtg_card_back),
                    error = painterResource(Res.drawable.mtg_card_back),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().alpha(if (isMissing) 0.55f else 1f),
                )
            } else {
                Image(
                    painter = painterResource(Res.drawable.mtg_card_back),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().alpha(0.55f),
                )
            }
            if (isMissing) {
                Surface(
                    shape = ChipShape,
                    color = mc.backgroundSecondary.copy(alpha = 0.9f),
                    border = BorderStroke(1.dp, mc.goldMtg),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(3.dp),
                ) {
                    Text(
                        text = missingLabel,
                        style = ty.labelSmall,
                        color = mc.textPrimary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
        }
        CardName(
            name = name,
            showFrontOnly = true,
            style = ty.labelSmall,
            color = if (isMissing) mc.textSecondary else mc.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
