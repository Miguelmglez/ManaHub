package com.mmg.manahub.core.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mmg.manahub.core.ui.theme.ButtonShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing

enum class MagicCtaStyle {
    Filled,
    Outlined,
    Ghost
}

enum class MagicCtaSize {
    Normal,
    Compact
}

enum class MagicCtaColor {
    Primary,
    Accent,
    Error,
    Gold,
    Success,
    Warning,
    Info,
    Neutral,
    Surface,
    
    // Solid variants
    PrimarySolid,
    AccentSolid,
    ErrorSolid,
    SuccessSolid,
    GoldSolid,
    SurfaceSolid
}

/**
 * A spectacular and reusable Call To Action (CTA) button component.
 * Features an attractive offset gradient matching MagicBottomBar, glow shadows,
 * smooth animations, loading states, and perfect text centering.
 */
@Composable
fun MagicCtaButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    style: MagicCtaStyle = MagicCtaStyle.Filled,
    color: MagicCtaColor = MagicCtaColor.Primary,
    size: MagicCtaSize = MagicCtaSize.Normal,
    tintIcon: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
    contentPadding: PaddingValues? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing
    
    // Micro-animation: scale down slightly when pressed
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.95f else 1f, label = "cta_scale")
    
    val gradientColors = when (color) {
        MagicCtaColor.Primary -> listOf(mc.primaryAccent, mc.secondaryAccent)
        MagicCtaColor.Accent -> listOf(mc.secondaryAccent, mc.primaryAccent)
        MagicCtaColor.Error -> listOf(mc.lifeNegative, mc.lifeNegative)
        MagicCtaColor.Gold -> listOf(mc.goldMtg, mc.secondaryAccent)
        MagicCtaColor.Success -> listOf(mc.lifePositive, mc.lifePositive)
        MagicCtaColor.Warning -> listOf(mc.lifeNegative, mc.goldMtg)
        MagicCtaColor.Info -> listOf(mc.manaU, mc.secondaryAccent)
        MagicCtaColor.Neutral -> listOf(mc.textSecondary, mc.textDisabled)
        MagicCtaColor.Surface -> listOf(mc.surfaceVariant, mc.surface)
        
        MagicCtaColor.PrimarySolid -> listOf(mc.primaryAccent, mc.primaryAccent)
        MagicCtaColor.AccentSolid -> listOf(mc.secondaryAccent, mc.secondaryAccent)
        MagicCtaColor.ErrorSolid -> listOf(mc.lifeNegative, mc.lifeNegative)
        MagicCtaColor.SuccessSolid -> listOf(mc.lifePositive, mc.lifePositive)
        MagicCtaColor.GoldSolid -> listOf(mc.goldMtg, mc.goldMtg)
        MagicCtaColor.SurfaceSolid -> listOf(mc.surfaceVariant, mc.surfaceVariant)
    }
    val glowColor = gradientColors.first().copy(alpha = 0.35f)
    val baseColor = gradientColors.first()
    
    // Spectacular gradient matching MagicBottomBar
    val gradientBrush = Brush.linearGradient(
        colors = gradientColors,
        start = Offset(0f, Float.POSITIVE_INFINITY),
        end = Offset(Float.POSITIVE_INFINITY, 0f),
    )
    
    val contentColor = mc.onAccent
    val disabledContainer = mc.surfaceVariant
    val disabledContent = mc.textDisabled

    val baseModifier = modifier.scale(scale)

    val finalPadding = contentPadding ?: when (size) {
        MagicCtaSize.Normal -> PaddingValues(
            horizontal = if (text == null) spacing.md else spacing.xl,
            vertical = spacing.md
        )
        MagicCtaSize.Compact -> PaddingValues(
            horizontal = if (text == null) spacing.sm else spacing.md,
            vertical = spacing.xs
        )
    }

    when (style) {
        MagicCtaStyle.Filled -> {
            Box(
                modifier = baseModifier
                    .then(
                        if (enabled && !isLoading) {
                            Modifier
                                .shadow(8.dp, ButtonShape, ambientColor = glowColor, spotColor = glowColor)
                                .clip(ButtonShape)
                                .background(gradientBrush)
                        } else {
                            Modifier
                                .clip(ButtonShape)
                                .background(disabledContainer)
                        }
                    )
                    .clickable(
                        interactionSource = interactionSource,
                        indication = ripple(color = contentColor),
                        enabled = enabled && !isLoading,
                        onClick = onClick
                    )
                    .padding(finalPadding),
                contentAlignment = Alignment.Center
            ) {
                CenteredButtonContent(text, isLoading, icon, if (enabled && !isLoading) contentColor else disabledContent, null, tintIcon, size)
            }
        }
        MagicCtaStyle.Outlined -> {
            Box(
                modifier = baseModifier
                    .clip(ButtonShape)
                    .background(Color.Transparent)
                    .border(
                        width = 1.dp,
                        brush = if (enabled && !isLoading) gradientBrush else Brush.linearGradient(listOf(disabledContainer, disabledContainer)),
                        shape = ButtonShape
                    )
                    .clickable(
                        interactionSource = interactionSource,
                        indication = ripple(color = baseColor),
                        enabled = enabled && !isLoading,
                        onClick = onClick
                    )
                    .padding(finalPadding),
                contentAlignment = Alignment.Center
            ) {
                CenteredButtonContent(text, isLoading, icon, if (enabled && !isLoading) baseColor else disabledContent, if (enabled && !isLoading) gradientBrush else null, tintIcon, size)
            }
        }
        MagicCtaStyle.Ghost -> {
            Box(
                modifier = baseModifier
                    .clip(ButtonShape)
                    .background(Color.Transparent)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = ripple(color = baseColor),
                        enabled = enabled && !isLoading,
                        onClick = onClick
                    )
                    .padding(finalPadding),
                contentAlignment = Alignment.Center
            ) {
                CenteredButtonContent(text, isLoading, icon, if (enabled && !isLoading) baseColor else disabledContent, if (enabled && !isLoading) gradientBrush else null, tintIcon, size)
            }
        }
    }
}

@Composable
private fun CenteredButtonContent(
    text: String?,
    isLoading: Boolean,
    icon: (@Composable () -> Unit)?,
    tintColor: Color,
    gradientBrush: Brush?,
    tintIcon: Boolean = true,
    size: MagicCtaSize = MagicCtaSize.Normal
) {
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val showIcon = isLoading || icon != null
    val hasText = !text.isNullOrBlank()

    val iconBoxSize = if (size == MagicCtaSize.Compact) 16.dp else 24.dp
    val indicatorSize = if (size == MagicCtaSize.Compact) 14.dp else 18.dp
    val strokeWidth = if (size == MagicCtaSize.Compact) 2.dp else 2.5.dp
    val spacerWidth = if (size == MagicCtaSize.Compact) spacing.xs else spacing.sm

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .animateContentSize()
    ) {
        // Left side: Visible icon/loader
        AnimatedVisibility(
            visible = showIcon,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(iconBoxSize)) {
                    AnimatedContent(
                        targetState = isLoading,
                        label = "cta_icon_transition"
                    ) { loading ->
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(indicatorSize),
                                strokeWidth = strokeWidth,
                                color = tintColor
                            )
                        } else icon?.let {
                            CompositionLocalProvider(LocalContentColor provides tintColor) {
                                Box(modifier = if (gradientBrush != null && tintIcon) Modifier.gradientTint(gradientBrush) else Modifier) {
                                    it()
                                }
                            }
                        }
                    }
                }
                if (hasText) {
                    Spacer(Modifier.width(spacerWidth))
                }
            }
        }
        
        // Center: Text
        if (text != null) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                val textModifier = if (gradientBrush != null) Modifier.gradientTint(gradientBrush) else Modifier
                val textStyle = if (size == MagicCtaSize.Compact) {
                    ty.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                } else {
                    ty.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                }
                AutoResizeText(
                    text = text.uppercase(),
                    style = textStyle,
                    color = tintColor,
                    modifier = textModifier
                )
            }
        }
    }
}

@Composable
private fun AutoResizeText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier
) {
    var textSize by remember(text) { mutableStateOf(style.fontSize) }
    
    Text(
        text = text,
        modifier = modifier,
        style = style,
        fontSize = textSize,
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { result ->
            if (result.hasVisualOverflow && (textSize > 10.sp)) {
                textSize *= 0.9f
            }
        }
    )
}

/**
 * Extension to apply a gradient brush to any content (usually icons)
 * using [BlendMode.SrcIn].
 */
private fun Modifier.gradientTint(brush: Brush) = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithCache {
        onDrawWithContent {
            drawContent()
            drawRect(brush = brush, blendMode = BlendMode.SrcIn)
        }
    }
