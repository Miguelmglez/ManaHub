package com.mmg.manahub.feature.gamification.presentation

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mmg.manahub.core.gamification.domain.catalog.BadgeFrameShape
import com.mmg.manahub.core.gamification.domain.catalog.CosmeticColorToken
import com.mmg.manahub.core.gamification.domain.catalog.FrameStyle
import com.mmg.manahub.core.gamification.domain.catalog.RenderSpec
import com.mmg.manahub.core.gamification.domain.catalog.RingStyle
import com.mmg.manahub.core.gamification.domain.catalog.TitleStyle
import com.mmg.manahub.core.gamification.domain.catalog.UnlockableKind
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography

/**
 * Procedural, asset-free Compose renderers for the Phase-3 cosmetics system (ADR-002 §10).
 *
 * Every cosmetic is drawn from a [RenderSpec] using ONLY MagicTheme tokens, so the same catalog item
 * adapts to all 12 themes. There are ZERO image assets — titles, badges, avatar frames and the
 * level-ring restyle are all Canvas / `Brush` constructions. [CosmeticColorToken.resolve] is the single
 * place a token becomes a concrete `Color`.
 *
 * The "foil" treatment uses an AGSL [RuntimeShader] on API 33+ for an animated iridescent sweep, and a
 * cheap animated gradient `Brush` fallback below 33 (minSdk 29). The version split is isolated so the
 * AGSL class is never referenced on the pre-33 path.
 */

// ── Token resolution ─────────────────────────────────────────────────────────────

/**
 * Resolves this semantic [CosmeticColorToken] to the active theme's `MagicColors` field. This is the
 * ONLY bridge from a serialisable token to a Compose [Color], keeping the catalog Android-free.
 */
@Composable
fun CosmeticColorToken.resolve(): Color {
    val mc = MaterialTheme.magicColors
    return when (this) {
        CosmeticColorToken.PRIMARY_ACCENT -> mc.primaryAccent
        CosmeticColorToken.GOLD -> mc.goldMtg
        CosmeticColorToken.LIFE_POSITIVE -> mc.lifePositive
        CosmeticColorToken.LIFE_NEGATIVE -> mc.lifeNegative
        CosmeticColorToken.TEXT_PRIMARY -> mc.textPrimary
        CosmeticColorToken.TEXT_SECONDARY -> mc.textSecondary
        CosmeticColorToken.SURFACE -> mc.surface
        CosmeticColorToken.MANA_W -> mc.manaW
        CosmeticColorToken.MANA_U -> mc.manaU
        CosmeticColorToken.MANA_B -> mc.manaB
        CosmeticColorToken.MANA_R -> mc.manaR
        CosmeticColorToken.MANA_G -> mc.manaG
    }
}

// ── Title text ───────────────────────────────────────────────────────────────────

/**
 * Renders a TITLE cosmetic's [text]. A [TitleStyle.PLAIN] title paints in the primary token color; a
 * [TitleStyle.GRADIENT] title blends primary→secondary across the text via a horizontal linear
 * `Brush`. Stateless and theme-token-driven.
 *
 * @param renderSpec the cosmetic's render description (tokens + title style).
 * @param text the resolved (localized) title text.
 * @param style the typography token to draw with (caller supplies a MagicTypography style).
 */
@Composable
fun TitleText(
    renderSpec: RenderSpec,
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val primary = renderSpec.primaryToken.resolve()
    val secondary = renderSpec.secondaryToken?.resolve() ?: primary
    val isGradient = renderSpec.titleStyle == TitleStyle.GRADIENT && renderSpec.secondaryToken != null

    if (isGradient) {
        val brush = Brush.linearGradient(listOf(primary, secondary))
        Text(text = text, style = style.copy(brush = brush), modifier = modifier)
    } else {
        Text(text = text, style = style, color = primary, modifier = modifier)
    }
}

// ── Badge emblem ─────────────────────────────────────────────────────────────────

/**
 * Renders a BADGE emblem: a frame shape ([BadgeFrameShape.CIRCLE]/[BadgeFrameShape.SHIELD]/
 * [BadgeFrameShape.HEX]) with a soft glow + border in the primary token and the [RenderSpec.glyph]
 * centered. Asset-free.
 *
 * Black mana ([CosmeticColorToken.MANA_B]) is near-background in every palette, so its badge is drawn
 * as an OUTLINE on the theme surface (not a flat fill) with a contrasting border, keeping it visible on
 * dark and light themes alike (Chunk A KDoc).
 *
 * @param renderSpec the badge's render description (shape, primary token, glyph).
 * @param size the outer diameter of the emblem.
 */
@Composable
fun BadgeEmblem(
    renderSpec: RenderSpec,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val accent = renderSpec.primaryToken.resolve()
    val surface = (renderSpec.secondaryToken ?: CosmeticColorToken.SURFACE).resolve()
    val isBlackMana = renderSpec.primaryToken == CosmeticColorToken.MANA_B
    val glyph = renderSpec.glyph.orEmpty()

    Box(
        modifier = modifier.size(size),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val path = badgePath(renderSpec.badgeShape ?: BadgeFrameShape.CIRCLE, this.size)
            val stroke = this.size.minDimension * 0.07f

            if (isBlackMana) {
                // Outline treatment: fill with the theme surface (so the dark token never disappears),
                // then a strong contrasting border. The "glow" reads as a faint accent ring.
                drawPath(path = path, color = surface)
                drawPath(
                    path = path,
                    brush = Brush.linearGradient(listOf(accent.copy(alpha = 0.18f), accent.copy(alpha = 0.05f))),
                )
                drawPath(
                    path = path,
                    color = accent.copy(alpha = 0.9f),
                    style = Stroke(width = stroke * 1.4f, join = StrokeJoin.Round),
                )
            } else {
                // Soft glow halo behind the fill.
                drawPath(
                    path = path,
                    brush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.55f), accent.copy(alpha = 0f)),
                        center = Offset(this.size.width / 2f, this.size.height / 2f),
                        radius = this.size.minDimension * 0.62f,
                    ),
                )
                // Two-tone fill: token → surface for depth.
                drawPath(
                    path = path,
                    brush = Brush.linearGradient(
                        colors = listOf(accent, lerpToward(accent, surface)),
                    ),
                )
                drawPath(
                    path = path,
                    color = accent.copy(alpha = 0.85f),
                    style = Stroke(width = stroke, join = StrokeJoin.Round),
                )
            }
        }
        if (glyph.isNotEmpty()) {
            // Glyph sized relative to the badge so it reads at both ~22dp (hero) and ~56dp (preview).
            // Forced to a contrasting token color (NOT inherited LocalContentColor, which can vanish on a
            // filled badge — and this also guarantees the MANA_B outline branch keeps a legible glyph).
            Text(
                text = glyph,
                style = MaterialTheme.magicTypography.titleMedium.copy(
                    fontSize = with(LocalDensity.current) { (size * 0.5f).toSp() },
                    color = MaterialTheme.magicColors.textPrimary,
                ),
            )
        }
    }
}

// ── Avatar frame ring ─────────────────────────────────────────────────────────────

/**
 * Renders an AVATAR_FRAME as a ring meant to be overlaid AROUND an avatar (the caller composes it on
 * top of the avatar `Box`, matching the avatar's bounds). [FrameStyle.BRONZE]/[FrameStyle.SILVER]/
 * [FrameStyle.GOLD] paint a metallic sweep (warm→light→warm) in the frame's token; [FrameStyle.FOIL]
 * uses the iridescent foil treatment (animated AGSL on API 33+, gradient fallback below).
 *
 * @param renderSpec the frame's render description (token + frame style).
 */
@Composable
fun AvatarFrameRing(
    renderSpec: RenderSpec,
    modifier: Modifier = Modifier,
) {
    val accent = renderSpec.primaryToken.resolve()
    val light = (renderSpec.secondaryToken ?: CosmeticColorToken.TEXT_PRIMARY).resolve()
    val frameStyle = renderSpec.frameStyle ?: FrameStyle.BRONZE

    if (frameStyle == FrameStyle.FOIL) {
        val foilBrush = rememberFoilBrush(accent, light)
        Canvas(modifier = modifier) {
            drawFrameRing(foilBrush)
        }
    } else {
        // Metallic sweep: token → light highlight → token, swept around the ring.
        val sweep = Brush.sweepGradient(
            listOf(accent, light, accent, light, accent),
        )
        Canvas(modifier = modifier) {
            drawFrameRing(sweep)
        }
    }
}

/** Draws the avatar frame as a thick rounded ring stroke filling the canvas bounds. */
private fun DrawScope.drawFrameRing(brush: Brush) {
    val stroke = size.minDimension * 0.09f
    val inset = stroke / 2f
    drawRoundedFrame(brush, stroke, inset)
}

/** A rounded-rect ring stroke (matches the hero avatar's rounded corners). */
private fun DrawScope.drawRoundedFrame(brush: Brush, stroke: Float, inset: Float) {
    val corner = size.minDimension * 0.12f
    drawRoundRect(
        brush = brush,
        topLeft = Offset(inset, inset),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner),
        style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

// ── Level-ring style brush ─────────────────────────────────────────────────────────

/**
 * Resolves the progress-arc brush for a [RingStyle], or null for [RingStyle.SOLID]/null (the caller
 * then falls back to a flat primaryAccent color — IDENTICAL to the pre-Phase-3 ring).
 *
 * - [RingStyle.GRADIENT_SWEEP] → a sweep gradient primary→secondary.
 * - [RingStyle.METALLIC] → a metallic sweep (token → light → token).
 * - [RingStyle.FOIL] → the iridescent foil brush (AGSL on API 33+, gradient fallback below).
 */
@Composable
fun rememberRingStyleBrush(
    ringStyle: RingStyle?,
    renderSpec: RenderSpec?,
): Brush? {
    if (ringStyle == null || ringStyle == RingStyle.SOLID || renderSpec == null) return null
    val primary = renderSpec.primaryToken.resolve()
    val secondary = renderSpec.secondaryToken?.resolve() ?: primary
    return when (ringStyle) {
        RingStyle.GRADIENT_SWEEP -> Brush.sweepGradient(listOf(primary, secondary, primary))
        RingStyle.METALLIC -> Brush.sweepGradient(listOf(primary, secondary, primary, secondary, primary))
        RingStyle.FOIL -> rememberFoilBrush(primary, secondary)
        RingStyle.SOLID -> null
    }
}

// ── Foil (AGSL on API 33+, gradient fallback below) ─────────────────────────────────

/**
 * Returns an animated iridescent "foil" [Brush].
 *
 * On API 33+ this is a cheap animated AGSL hue sweep ([rememberFoilShaderBrush]); below 33 it is an
 * animated sweep gradient that pans through the two tokens ([rememberFoilFallbackBrush]). The branch is
 * isolated so the AGSL [RuntimeShader] class is only touched inside the `>= 33` path — the pre-33 build
 * never loads it.
 */
@Composable
private fun rememberFoilBrush(primary: Color, secondary: Color): Brush {
    val transition = rememberInfiniteTransition(label = "foil")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = foilAnimationSpec(),
        label = "foilPhase",
    )
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberFoilShaderBrush(phase, primary, secondary)
    } else {
        rememberFoilFallbackBrush(phase, primary, secondary)
    }
}

private fun foilAnimationSpec(): InfiniteRepeatableSpec<Float> = infiniteRepeatable(
    animation = tween(durationMillis = 2600, easing = LinearEasing),
    repeatMode = RepeatMode.Restart,
)

/**
 * AGSL iridescent sweep (API 33+ only). A simple animated hue band that drifts diagonally; tints toward
 * the two theme tokens so the foil still reads as part of the active palette. Cheap: a couple of
 * trig/mix ops per pixel.
 *
 * The [RuntimeShader] and its [ShaderBrush] wrapper are [remember]ed ONCE; only the per-frame uniforms
 * (`uPhase`, colors) and the size-dependent `uResolution` are updated — no allocation per frame.
 * `uResolution` is injected in [ShaderBrush.createShader], which Compose re-invokes with the live draw
 * size, keeping the sweep size-independent.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun rememberFoilShaderBrush(phase: Float, primary: Color, secondary: Color): Brush {
    val shader = remember { RuntimeShader(FOIL_AGSL) }
    // Update per-frame / per-color uniforms each recomposition (cheap; no new shader allocated).
    shader.setFloatUniform("uPhase", phase)
    shader.setFloatUniform("uPrimary", primary.red, primary.green, primary.blue)
    shader.setFloatUniform("uSecondary", secondary.red, secondary.green, secondary.blue)
    return remember(shader) {
        object : ShaderBrush() {
            override fun createShader(size: Size): Shader {
                shader.setFloatUniform("uResolution", size.width, size.height)
                return shader
            }
        }
    }
}

/** Pre-33 fallback: an animated sweep gradient that pans the two tokens around the ring. */
@Composable
private fun rememberFoilFallbackBrush(phase: Float, primary: Color, secondary: Color): Brush =
    remember(phase, primary, secondary) {
        // Rotate the band by re-ordering stops as phase advances for a sheen-pan effect.
        val a = lerpColor(primary, secondary, phase)
        val b = lerpColor(secondary, primary, phase)
        Brush.sweepGradient(listOf(a, b, a, b, a))
    }

/**
 * AGSL source for the foil sweep (API 33+). Produces a diagonal iridescent band mixed between the two
 * theme tokens, animated by `uPhase`. Coordinates are normalized by `uResolution` (the live draw size),
 * so the band is size- and density-independent (a small badge preview and a large hero ring read the
 * same).
 */
private const val FOIL_AGSL = """
    uniform float uPhase;
    uniform float2 uResolution;
    uniform float3 uPrimary;
    uniform float3 uSecondary;

    half4 main(float2 coord) {
        // Normalize to 0..1 so the foil sweep is size- and density-independent.
        float2 uv = coord / uResolution;
        float band = (uv.x + uv.y) * 3.14159265 + uPhase * 6.2831853;
        float t = 0.5 + 0.5 * sin(band);
        // A faint iridescent shimmer layered over the token blend.
        float shimmer = 0.15 * sin(band * 3.0 + 1.7);
        float3 base = mix(uPrimary, uSecondary, t);
        float3 col = clamp(base + shimmer, 0.0, 1.0);
        return half4(col, 1.0);
    }
"""

// ── Reward preview dispatcher (Rewards tab cells) ───────────────────────────────────

/**
 * Renders a small square preview for a reward cell, dispatching by [kind]:
 * - TITLE → the styled title text (the cosmetic name).
 * - BADGE → the badge emblem.
 * - AVATAR_FRAME → a frame ring around a neutral placeholder.
 * - LEVEL_RING_STYLE → a sample progress ring drawn with the cosmetic's brush.
 *
 * Stateless and token-driven so previews look correct in all 12 themes.
 */
@Composable
fun RewardPreview(
    kind: UnlockableKind,
    renderSpec: RenderSpec,
    name: String,
    modifier: Modifier = Modifier,
) {
    when (kind) {
        UnlockableKind.TITLE -> Box(modifier = modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
            TitleText(
                renderSpec = renderSpec,
                text = name,
                style = MaterialTheme.magicTypography.titleMedium,
            )
        }

        UnlockableKind.BADGE -> BadgeEmblem(
            renderSpec = renderSpec,
            size = 56.dp,
            modifier = modifier,
        )

        UnlockableKind.AVATAR_FRAME -> Box(modifier = modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = MaterialTheme.magicColors.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                    ),
            )
            AvatarFrameRing(renderSpec = renderSpec, modifier = Modifier.size(56.dp))
        }

        UnlockableKind.LEVEL_RING_STYLE -> RingStylePreview(renderSpec = renderSpec, modifier = modifier)
    }
}

/** A small sample progress ring (~70% sweep) drawn with the cosmetic's ring-style brush. */
@Composable
private fun RingStylePreview(renderSpec: RenderSpec, modifier: Modifier = Modifier) {
    val brush = rememberRingStyleBrush(renderSpec.ringStyle, renderSpec)
    val fallback = renderSpec.primaryToken.resolve()
    val track = MaterialTheme.magicColors.surfaceVariant
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.12f
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(inset, inset)
        drawArc(
            color = track,
            startAngle = 0f, sweepAngle = 360f, useCenter = false,
            topLeft = topLeft, size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        if (brush != null) {
            drawArc(
                brush = brush,
                startAngle = -90f, sweepAngle = 252f, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        } else {
            drawArc(
                color = fallback,
                startAngle = -90f, sweepAngle = 252f, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}

// ── Geometry + color helpers ────────────────────────────────────────────────────────

/** Builds the [Path] for a badge frame [shape] filling [size]. */
private fun badgePath(shape: BadgeFrameShape, size: Size): Path {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    val r = minOf(w, h) / 2f
    return when (shape) {
        BadgeFrameShape.CIRCLE -> Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(Offset(cx - r, cy - r), Size(r * 2f, r * 2f)))
        }

        BadgeFrameShape.HEX -> Path().apply {
            // Pointy-top hexagon.
            for (i in 0 until 6) {
                val angle = Math.toRadians((60.0 * i) - 90.0)
                val x = cx + r * kotlin.math.cos(angle).toFloat()
                val y = cy + r * kotlin.math.sin(angle).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }

        BadgeFrameShape.SHIELD -> Path().apply {
            // A simple heraldic shield: flat top, tapering to a point.
            val top = cy - r
            val bottom = cy + r
            moveTo(cx - r, top)
            lineTo(cx + r, top)
            lineTo(cx + r, cy + r * 0.2f)
            quadraticTo(cx + r, bottom, cx, bottom)
            quadraticTo(cx - r, bottom, cx - r, cy + r * 0.2f)
            close()
        }
    }
}

/** Blends [from] ~35% toward [to] for a subtle two-tone fill. */
private fun lerpToward(from: Color, to: Color): Color = lerpColor(from, to, 0.35f)

/** Linear color interpolation in sRGB component space (cheap, good enough for cosmetics). */
private fun lerpColor(a: Color, b: Color, t: Float): Color {
    val c = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * c,
        green = a.green + (b.green - a.green) * c,
        blue = a.blue + (b.blue - a.blue) * c,
        alpha = a.alpha + (b.alpha - a.alpha) * c,
    )
}
