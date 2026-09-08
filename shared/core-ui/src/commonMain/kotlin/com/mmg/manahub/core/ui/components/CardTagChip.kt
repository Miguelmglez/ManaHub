package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.MagicColors
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import kotlin.math.pow

// ═══════════════════════════════════════════════════════════════════════════════
//  CardTagChip — the single shared rendering for a tag, color-coded by TagCategory.
//
//  Consolidates the previously-duplicated inline chip logic in
//  `feature/carddetail/presentation/CardDetailScreen.kt` (TagsSection/TagPickerSheet) and
//  `feature/decks/presentation/components/CardDetailSheet.kt` (read-only tag row).
// ═══════════════════════════════════════════════════════════════════════════════

private const val CHIP_CONTAINER_ALPHA = 0.20f

/** WCAG AA threshold for small text (this chip's label is always `labelSmall`). */
private const val CHIP_MIN_TEXT_CONTRAST = 4.5f

/**
 * The [MagicColors] accent token that visually identifies a [TagCategory].
 *
 * `null` for [TagCategory.CUSTOM] is deliberate — user-authored tags render with a neutral
 * fill/border (see [chipTonalColors]), never a bright accent, so they don't visually compete
 * with the system taxonomy. `manaW`/`manaU`/`manaB`/`manaR`/`manaG` are deliberately never used
 * here either: reusing a WUBRG pip color for a tag category would misread as a color-identity
 * claim on the card, unrelated to what the tag means.
 */
internal fun TagCategory.accentToken(mc: MagicColors): Color? = when (this) {
    TagCategory.ARCHETYPE -> mc.primaryAccent
    TagCategory.STRATEGY  -> mc.secondaryAccent
    TagCategory.ROLE      -> mc.goldMtg
    TagCategory.TRIBAL    -> mc.commanderAccent
    TagCategory.KEYWORD   -> mc.manaC
    TagCategory.TYPE      -> mc.textSecondary
    TagCategory.CUSTOM    -> null
}

/**
 * WCAG 2.1 relative luminance of an sRGB color (per-channel linearization, standard
 * 0.2126/0.7152/0.0722 weights). Pure math over the already-resolved [Color] — never a branch
 * on which theme/token is active, so [chipTonalColors]'s contrast guard runs identically on all
 * 12 palettes and self-corrects for any future one without special-casing a theme by name.
 */
private fun relativeLuminance(color: Color): Float {
    fun channel(c: Float): Float =
        if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    return 0.2126f * channel(color.red) + 0.7152f * channel(color.green) + 0.0722f * channel(color.blue)
}

private fun contrastRatio(a: Color, b: Color): Float {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    val lighter = maxOf(la, lb)
    val darker = minOf(la, lb)
    return (lighter + 0.05f) / (darker + 0.05f)
}

private data class ChipTonalColors(val container: Color, val content: Color)

/**
 * Resolves a category's tonal chip colors against [containerBase] (the surface the chip is
 * drawn on): a `token.copy(alpha = 0.20f)` tonal fill with full-opacity `token` for text/border —
 * UNLESS that text/border color can't clear [CHIP_MIN_TEXT_CONTRAST] against its own tonal fill
 * (this happens on HallowedPrint for [TagCategory.KEYWORD]/[TagCategory.ROLE], whose light-toned
 * `manaC`/`goldMtg` were designed as mana-pip/celebration accents, not body-text ink — see
 * `feedback_light_theme_contrast_tokens` in project memory for the precedent), in which case the
 * chip falls back to [MagicColors.textPrimary] for legibility while the tonal fill still carries
 * the category's hue. [TagCategory.CUSTOM] is a flat neutral [MagicColors.surfaceVariant] fill +
 * [MagicColors.textDisabled] ink — a deliberately muted, "disabled-looking" style (user-authored
 * tags), which is why it is exempt from the same AA guard (WCAG 1.4.11 does not require contrast
 * for inactive/de-emphasized UI).
 */
private fun TagCategory.chipTonalColors(mc: MagicColors, containerBase: Color): ChipTonalColors {
    val accent = accentToken(mc)
        ?: return ChipTonalColors(container = mc.surfaceVariant, content = mc.textDisabled)
    val container = accent.copy(alpha = CHIP_CONTAINER_ALPHA)
    val blended = container.compositeOver(containerBase)
    val content = if (contrastRatio(accent, blended) >= CHIP_MIN_TEXT_CONTRAST) accent else mc.textPrimary
    return ChipTonalColors(container = container, content = content)
}

/**
 * A single tag chip, color-coded by [TagCategory] (see [accentToken] for the mapping). This is
 * the ONE shared rendering for every tag surface in the app — reuse it instead of an inline
 * `Surface`/`InputChip`/`SuggestionChip` wherever a [com.mmg.manahub.core.model.CardTag] (or an
 * equivalent key+label+category triple) is displayed.
 *
 * This composable never localizes or derives the label itself — callers pass the already-resolved
 * display text (Android: `CardTag.label()`; commonMain fallback: `CardTag.displayLabel`). Keeping
 * localization out of this component is what lets it live in `commonMain`.
 *
 * @param label                    Pre-resolved display text.
 * @param category                 Drives the accent color.
 * @param onClick                  Optional whole-chip tap handler. `null` renders a purely
 *                                 decorative, non-interactive chip (e.g. an already-confirmed
 *                                 auto tag with no action).
 * @param onRemove                 Optional trailing "remove" affordance: draws a small close icon
 *                                 and, when [onClick] is `null`, ALSO wires the whole chip's tap
 *                                 to [onRemove] (matches the prior
 *                                 `InputChip(selected = true, onClick = onRemove)` behavior at the
 *                                 Card Detail user-tags call site — tapping anywhere on the chip
 *                                 removes it).
 * @param removeContentDescription Content description for the remove icon (Android call sites
 *                                 should pass a localized `stringResource`; this component cannot
 *                                 reference Android resources itself).
 * @param trailing                 Optional extra trailing content (e.g. an "applied" checkmark,
 *                                 edit/delete icon buttons) rendered after the remove icon, if any.
 */
@Composable
fun CardTagChip(
    label: String,
    category: TagCategory,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    removeContentDescription: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val colors = category.chipTonalColors(mc, containerBase = mc.surface)
    val effectiveOnClick = onClick ?: onRemove

    Surface(
        color = colors.container,
        shape = ChipShape,
        border = BorderStroke(1.dp, colors.content),
        modifier = if (effectiveOnClick != null) {
            modifier.clickable(role = Role.Button, onClick = effectiveOnClick)
        } else {
            modifier
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Text(text = label, style = ty.labelSmall, color = colors.content, maxLines = 1)
            if (onRemove != null) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = removeContentDescription,
                    tint = colors.content,
                    modifier = Modifier.size(14.dp),
                )
            }
            trailing?.invoke()
        }
    }
}

/**
 * A layout that groups [tags] by their [TagCategory], rendering each group with a category
 * header (color-coded to match the chips) and a [FlowRow] of chips.
 *
 * This component flows the categories themselves horizontally to save vertical space. If a
 * category has few tags, the next category will attempt to sit beside it.
 *
 * @param tags       The list of tags to display.
 * @param modifier   Modifier for the root [FlowRow].
 * @param tagLabel   Lambda to resolve the display label for a tag. This allows the component to
 *                   stay platform-agnostic while still using Android-specific localization
 *                   at the call site (e.g. `tag.label()` on Android vs `tag.displayLabel` on Web).
 * @param onTagClick Optional tap handler for the individual chips.
 * @param onTagRemove Optional removal handler.
 * @param removeContentDescription Optional content description for the removal icon.
 * @param tagTrailing Optional extra trailing content per tag.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardTagGroup(
    tags: List<CardTag>,
    modifier: Modifier = Modifier,
    tagLabel: (CardTag) -> String = { it.displayLabel },
    onTagClick: ((CardTag) -> Unit)? = null,
    onTagRemove: ((CardTag) -> Unit)? = null,
    removeContentDescription: @Composable (CardTag) -> String? = { null },
    tagTrailing: @Composable (CardTag) -> Unit = {},
) {
    if (tags.isEmpty()) return

    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    val groupedTags = remember(tags) {
        tags.groupBy { it.category }
            .entries
            .sortedBy { it.key.ordinal }
    }

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.lg), // Large gap between categories
        verticalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        groupedTags.forEach { entry ->
            val category = entry.key
            val categoryTags = entry.value

            Column(
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                modifier = Modifier.padding(bottom = 2.dp)
            ) {
                // Category Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    modifier = Modifier.padding(bottom = 2.dp)
                ) {
                    val accent = category.accentToken(mc) ?: mc.textDisabled
                    Surface(
                        modifier = Modifier.size(width = 3.dp, height = 10.dp),
                        shape = ChipShape,
                        color = accent
                    ) {}
                    Text(
                        text = category.displayLabel.uppercase(),
                        style = ty.labelSmall,
                        color = mc.textSecondary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    categoryTags.forEach { tag ->
                        CardTagChip(
                            label = tagLabel(tag),
                            category = tag.category,
                            onClick = onTagClick?.let { { it(tag) } },
                            onRemove = onTagRemove?.let { { it(tag) } },
                            removeContentDescription = removeContentDescription(tag),
                            trailing = { tagTrailing(tag) }
                        )
                    }
                }
            }
        }
    }
}
