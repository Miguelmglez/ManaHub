package com.mmg.manahub.feature.decks.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mmg.manahub.R
import com.mmg.manahub.core.model.DeckSlotEntry
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing

/**
 * A per-card construction-warning banner shown beneath a deck card row (Group C / C5).
 *
 * Extracted STATELESS from the legacy `DeckMagicDetailScreen`'s private `WarningOverlay`:
 * the ViewModel/uiState dependencies are replaced with plain boolean inputs + callbacks so
 * the same overlay can be driven from the unified Deck Studio screen. It surfaces three
 * mutually-combinable problems:
 *  - [isOverLimit] — more than [maxCopies] of a non-basic card,
 *  - [isInvalidIdentity] — a card outside the commander's color identity,
 *  - [isNonLegendaryCommander] — a commander slot whose card is not legendary.
 *
 * The over-limit / identity warnings can be acknowledged (the user accepts the deviation),
 * which mutes the banner's coloring; the non-legendary-commander warning has no acknowledge
 * affordance (it is a hard validity error) but shares the same surface.
 *
 * @param entry the card slot this warning belongs to (its [DeckSlotEntry.scryfallId] keys the ack callbacks).
 * @param isOverLimit whether the slot exceeds the format copy limit.
 * @param isInvalidIdentity whether the card is off the commander color identity.
 * @param isNonLegendaryCommander whether this is a commander slot with a non-legendary card.
 * @param isAcknowledged whether the user has already acknowledged this slot's deviation.
 * @param maxCopies the format copy limit, shown in the over-limit message.
 * @param onAcknowledge mark the slot's deviation acknowledged.
 * @param onUnacknowledge clear the slot's acknowledgement.
 * @param isCommander true when rendered beneath the commander banner (affects nothing visually here).
 */
@Composable
internal fun WarningOverlay(
    entry: DeckSlotEntry,
    isOverLimit: Boolean,
    isInvalidIdentity: Boolean,
    isNonLegendaryCommander: Boolean,
    isAcknowledged: Boolean,
    maxCopies: Int,
    onAcknowledge: (String) -> Unit,
    onUnacknowledge: (String) -> Unit,
    isCommander: Boolean = false,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    AnimatedVisibility(
        visible = isOverLimit || isInvalidIdentity || isNonLegendaryCommander,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Surface(
            shape = ChipShape,
            color = if (isAcknowledged) mc.surface else mc.lifeNegative.copy(alpha = 0.1f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                val warningText = when {
                    isNonLegendaryCommander -> stringResource(R.string.deckbuilder_invalid_commander_legendary)
                    isOverLimit && isInvalidIdentity -> stringResource(R.string.deckbuilder_error_limit_and_identity, maxCopies)
                    isOverLimit -> stringResource(R.string.deckbuilder_copy_warning, maxCopies)
                    else -> stringResource(R.string.deckbuilder_error_invalid_identity)
                }
                Text(
                    warningText,
                    style = ty.labelSmall,
                    color = if (isAcknowledged) mc.textSecondary else mc.lifeNegative,
                    modifier = Modifier.weight(1f),
                )
                // The non-legendary-commander error is a hard validity failure with no
                // acknowledge affordance; only the over-limit / identity deviations can be
                // muted by the user.
                if (!isNonLegendaryCommander) {
                    Checkbox(
                        checked = isAcknowledged,
                        onCheckedChange = {
                            if (it) onAcknowledge(entry.scryfallId) else onUnacknowledge(entry.scryfallId)
                        },
                        colors = CheckboxDefaults.colors(checkedColor = mc.primaryAccent, uncheckedColor = mc.lifeNegative),
                    )
                }
            }
        }
    }
}
