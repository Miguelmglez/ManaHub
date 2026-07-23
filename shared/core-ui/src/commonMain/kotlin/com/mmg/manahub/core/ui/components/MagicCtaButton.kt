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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
    icon: (@Composable () -> Unit)? = null,
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
        MagicCtaColor.Error -> listOf(mc.lifeNegative, mc.primaryAccent)
        MagicCtaColor.Gold -> listOf(mc.goldMtg, mc.secondaryAccent)
        MagicCtaColor.Success -> listOf(mc.lifePositive, mc.primaryAccent)
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

    val buttonPadding = if (text == null) {
        Modifier.padding(spacing.md) // Square-ish for icon only
    } else {
        Modifier.padding(horizontal = spacing.xl, vertical = spacing.md)
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
                    .then(buttonPadding),
                contentAlignment = Alignment.Center
            ) {
                CenteredButtonContent(text, isLoading, icon, if (enabled && !isLoading) contentColor else disabledContent, null)
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
                    .then(buttonPadding),
                contentAlignment = Alignment.Center
            ) {
                CenteredButtonContent(text, isLoading, icon, if (enabled && !isLoading) baseColor else disabledContent, if (enabled && !isLoading) gradientBrush else null)
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
                    .then(buttonPadding),
                contentAlignment = Alignment.Center
            ) {
                CenteredButtonContent(text, isLoading, icon, if (enabled && !isLoading) baseColor else disabledContent, if (enabled && !isLoading) gradientBrush else null)
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
    gradientBrush: Brush?
) {
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val showIcon = isLoading || icon != null
    val hasText = !text.isNullOrBlank()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .then(if (hasText) Modifier.fillMaxWidth() else Modifier)
            .animateContentSize()
    ) {
        // Left side: Visible icon/loader
        AnimatedVisibility(
            visible = showIcon,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(24.dp)) {
                    AnimatedContent(
                        targetState = isLoading,
                        label = "cta_icon_transition"
                    ) { loading ->
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.5.dp,
                                color = tintColor
                            )
                        } else icon?.let {
                            Box(modifier = if (gradientBrush != null) Modifier.gradientTint(gradientBrush) else Modifier) {
                                it()
                            }
                        }
                    }
                }
                if (hasText) {
                    Spacer(Modifier.width(spacing.sm))
                }
            }
        }
        
        // Center: Text perfectly aligned thanks to identical left/right constraints
        if (text != null) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                AutoResizeText(
                    text = text.uppercase(),
                    style = ty.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    ),
                    color = tintColor
                )
            }
        }
        
        // Right side: Invisible clone to balance the left side
        if (hasText) {
            AnimatedVisibility(
                visible = showIcon,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally(),
                modifier = Modifier.alpha(0f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(spacing.sm))
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(24.dp)) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.5.dp, color = tintColor)
                        } else if (icon != null) {
                            icon()
                        }
                    }
                }
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
