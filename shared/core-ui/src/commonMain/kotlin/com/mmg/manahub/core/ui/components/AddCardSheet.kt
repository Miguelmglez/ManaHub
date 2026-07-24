package com.mmg.manahub.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mmg.manahub.core.ui.theme.BottomSheetShape
import com.mmg.manahub.core.ui.theme.CardCornerRadius
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.coloredShadow
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.core.util.CardConstants

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddCardSheet(
    cardName: String,
    onConfirm: (isFoil: Boolean, condition: String, language: String, qty: Int) -> Unit,
    onDismiss: () -> Unit,
    cardImage: String?,
    closeButton: Boolean = true,
    initialFoil: Boolean = false,
    initialCondition: String = "NM",
    initialLanguage: String = "en",
    initialQty: Int = 1,
    confirmButtonText: String = "Add to collection",
    setCode: String? = null,
    setName: String? = null,
    rarity: String? = null,
    /**
     * Card Versions & Languages, Phase 1B. When non-null, renders a "Set / Variant" field above the
     * toggles that invokes this callback on tap. The caller owns the printing selection state: it
     * swaps [setCode]/[setName]/[rarity]/[cardImage]/[manaCost] on the NEXT recomposition when the
     * user picks a different printing (e.g. via [VariantSelectorSheet]) — this composable stays
     * stateless with respect to WHICH printing is selected. [initialFoil]/[initialCondition]/
     * [initialLanguage]/[initialQty] are captured in `remember { }` with no keys specifically so a
     * printing swap does NOT reset the user's foil/condition/language/quantity choices.
     */
    onOpenVariantSelector: (() -> Unit)? = null,
    /**
     * Optional placeholder/error painter shown by Coil's [AsyncImage] while [cardImage] loads (or
     * fails to load). Generic [Painter] (not an Android resource id) so this `commonMain` file
     * stays platform-agnostic — Android callers pass `painterResource(R.drawable.xxx)`, web callers
     * would pass a CMP `Res` painter. Defaults to `null` (no placeholder — matches pre-existing
     * behavior for callers that don't opt in).
     */
    cardImagePlaceholder: Painter? = null,
    extraContent: (@Composable () -> Unit)? = null,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    var isFoil by remember { mutableStateOf(initialFoil) }
    var condition by remember { mutableStateOf(initialCondition) }
    var language by remember { mutableStateOf(initialLanguage) }
    var qty by remember { mutableIntStateOf(initialQty) }

    var showConditionSheet by remember { mutableStateOf(false) }
    var showLanguageSheet by remember { mutableStateOf(false) }

    // Edge-case audit A8 (2026-07-15): guards the confirm button against a double-tap firing
    // onConfirm twice (e.g. addOrIncrement being invoked twice for one user action). No reset is
    // needed — every call site dismisses this sheet (showAddSheet/showWishlistSheet = false) as
    // part of handling onConfirm, so the composable is torn down before it could matter.
    var confirmed by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden }
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = mc.background,
        contentColor = mc.textPrimary,
        contentWindowInsets = { WindowInsets(0) },
        dragHandle = if (closeButton) null else {
            { BottomSheetDefaults.DragHandle(color = mc.textDisabled.copy(alpha = 0.4f)) }
        },
        shape = BottomSheetShape
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = spacing.lg, vertical = spacing.sm)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {

            // Header Row
            if (closeButton) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.offset(x = (-12).dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Cancel",
                            tint = mc.textSecondary
                        )
                    }
                }
            }

            // ── Card Preview Section ──────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(BottomSheetShape)
                    .background(mc.surface)
                    .padding(spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.lg)
            ) {
                val displayName = cardName.substringBefore(" // ")
                CardName(
                    name = displayName,
                    style = ty.displayMedium,
                    color = mc.primaryAccent,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                cardImage?.let {
                    Box(
                        modifier = Modifier
                            .padding(vertical = spacing.xs)
                            .heightIn(max = 280.dp)
                            .aspectRatio(0.717f, matchHeightConstraintsFirst = true)
                            .coloredShadow(
                                color = mc.primaryAccent.copy(alpha = 0.4f),
                                borderRadius = CardCornerRadius,
                                blurRadius = 32.dp
                            )
                    ) {
                        AsyncImage(
                            model = cardImage,
                            contentDescription = cardName,
                            contentScale = ContentScale.Fit,
                            placeholder = cardImagePlaceholder,
                            error = cardImagePlaceholder,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CardShape)
                                .border(1.dp, mc.primaryAccent.copy(alpha = 0.3f), CardShape)
                        )

                        if (isFoil) {
                            LightweightFoilShimmer()
                        }
                    }
                }

                // Set / Variant field — only rendered when the caller supports switching printings.
                if (onOpenVariantSelector != null) {
                    Surface(
                        onClick = onOpenVariantSelector,
                        color = mc.background,
                        shape = CardShape,
                        border = BorderStroke(1.dp, mc.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .padding(horizontal = spacing.md, vertical = spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Set / Variant", style = ty.labelSmall, color = mc.textSecondary)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                                ) {
                                    if (setCode != null) {
                                        SetSymbol(
                                            setCode = setCode,
                                            rarity = CardRarity.fromString(rarity ?: "common"),
                                            size = 16.dp,
                                        )
                                    }
                                    Text(
                                        text = setName ?: "Select set",
                                        style = ty.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = mc.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = mc.textDisabled,
                            )
                        }
                    }
                }
            }

            // ── Settings Section ──────────────────────────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing.md)
            ) {
                // Selectors Row: Condition, Language, Foil
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Condition Selector
                    SelectorCard(
                        label = "Condition",
                        value = condition,
                        icon = null, // Could add icon here if needed
                        modifier = Modifier.weight(1.2f),
                        onClick = { showConditionSheet = true }
                    )

                    // Language Selector
                    SelectorCard(
                        label = "Lang",
                        value = CardConstants.getFlag(language),
                        icon = null,
                        modifier = Modifier.weight(1f),
                        onClick = { showLanguageSheet = true }
                    )

                    // Foil Toggle
                    Surface(
                        modifier = Modifier.weight(1.1f).height(56.dp),
                        color = if (isFoil) mc.primaryAccent.copy(alpha = 0.12f) else mc.surface,
                        shape = CardShape,
                        border = BorderStroke(
                            1.dp,
                            if (isFoil) mc.primaryAccent.copy(alpha = 0.6f) else mc.surfaceVariant
                        ),
                        onClick = { isFoil = !isFoil }
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                "Foil",
                                style = ty.labelSmall,
                                color = if (isFoil) mc.primaryAccent else mc.textSecondary
                            )
                            Switch(
                                checked = isFoil,
                                onCheckedChange = { isFoil = it },
                                modifier = Modifier.size(width = 32.dp, height = 24.dp).scale(0.75f),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = mc.background,
                                    checkedTrackColor = mc.primaryAccent,
                                    uncheckedThumbColor = mc.textDisabled,
                                    uncheckedTrackColor = mc.surfaceVariant
                                )
                            )
                        }
                    }
                }

                // Quantity stepper
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CardShape)
                        .background(mc.surface)
                        .padding(horizontal = spacing.lg, vertical = spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Quantity",
                        Modifier.weight(1f),
                        style = ty.bodyMedium,
                        color = mc.textPrimary
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.lg)
                    ) {
                        IconButton(
                            onClick = { if (qty > 1) qty-- },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Remove,
                                contentDescription = "Remove",
                                modifier = Modifier.size(20.dp),
                                tint = if (qty > 1) mc.primaryAccent else mc.textDisabled
                            )
                        }

                        Text(
                            qty.toString(),
                            style = ty.titleMedium,
                            color = mc.primaryAccent,
                            modifier = Modifier.width(28.dp),
                            textAlign = TextAlign.Center
                        )

                        IconButton(
                            onClick = { if (qty < 99) qty++ },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Add",
                                modifier = Modifier.size(20.dp),
                                tint = if (qty < 99) mc.primaryAccent else mc.textDisabled
                            )
                        }
                    }
                }
            }

            // Set / Variant selector was here, moved above

            if (showConditionSheet) {
                ConditionSelectorSheet(
                    selectedCondition = condition,
                    onDismiss = { showConditionSheet = false },
                    onSelectCondition = {
                        condition = it
                        showConditionSheet = false
                    }
                )
            }

            if (showLanguageSheet) {
                LanguageSelectorSheet(
                    selectedLanguage = language,
                    onDismiss = { showLanguageSheet = false },
                    onSelectLanguage = {
                        language = it
                        showLanguageSheet = false
                    }
                )
            }

            extraContent?.invoke()

            // Confirm / Cancel - Vertical CTAs
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                MagicCtaButton(
                    onClick = {
                        if (!confirmed) {
                            confirmed = true
                            onConfirm(
                                isFoil,
                                condition,
                                language,
                                qty
                            )
                        }
                    },
                    enabled = !confirmed,
                    text = confirmButtonText,
                    modifier = Modifier.fillMaxWidth(),
                )

                MagicCtaButton(
                    onClick = onDismiss,
                    text = "Cancel",
                    style = MagicCtaStyle.Ghost,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(spacing.xl))
        }
    }
}

@Composable
private fun SelectorCard(
    label: String,
    value: String,
    icon: @Composable (() -> Unit)?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    Surface(
        modifier = modifier.height(56.dp),
        color = mc.surface,
        shape = CardShape,
        border = BorderStroke(1.dp, mc.surfaceVariant),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Center
        ) {
            Text(label, style = ty.labelSmall, color = mc.textSecondary)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs)
            ) {
                if (icon != null) icon()
                Text(
                    value,
                    style = ty.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = mc.textPrimary
                )
            }
        }
    }
}

@Composable
private fun LightweightFoilShimmer() {
    val shimmerTransition = rememberInfiniteTransition(label = "foil_shimmer")
    val offset by shimmerTransition.animateFloat(
        initialValue = -500f,
        targetValue = 500f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_offset",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0x05FFFFFF),
                        Color(0x15FF00FF),
                        Color(0x1500FFFF),
                        Color(0x05FFFFFF),
                        Color.Transparent,
                    ),
                    start = Offset(offset, offset),
                    end = Offset(offset + 300f, offset + 300f),
                ),
            ),
    )
}


