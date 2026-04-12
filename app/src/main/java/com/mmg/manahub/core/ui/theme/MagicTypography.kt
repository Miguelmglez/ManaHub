package com.mmg.manahub.core.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mmg.manahub.R

// ── Font families cargadas desde res/font/ ────────────────────────────────────

val MarcellusFontFamily = FontFamily(
    Font(R.font.marcellus_regular, FontWeight.Normal),
    Font(R.font.marcellus_regular,  FontWeight.SemiBold),
    Font(R.font.marcellus_regular,      FontWeight.Bold),
    Font(R.font.marcellus_regular, FontWeight.ExtraBold),
    Font(R.font.marcellus_regular,     FontWeight.Black),
)

val MulishFontFamily = FontFamily(
    Font(R.font.mulish_light,    FontWeight.Light),
    Font(R.font.mulish_regular,  FontWeight.Normal),
    Font(R.font.mulish_medium,   FontWeight.Medium),
    Font(R.font.mulish_semibold, FontWeight.SemiBold),
    Font(R.font.mulish_bold,     FontWeight.Bold),
)

val ManaFontFamily = FontFamily(
    Font(R.font.mana, FontWeight.Normal)
)

// ── Escala tipográfica ────────────────────────────────────────────────────────

/**
 * Typography scale for the ManaHub design system.
 *
 * Hierarchy:
 *  displayLarge / displayMedium — MarcellusFontFamily Black, section titles, victory screen
 *  lifeNumber / lifeNumberMd   — MarcellusFontFamily Black, main life-total numeral
 *  titleLarge / titleMedium    — MarcellusFontFamily, card titles, feature headers
 *  labelLarge … labelSmall     — MarcellusFontFamily, nav tabs, chips, badges
 *  bodyLarge … bodySmall       — mulishFontFamily, prose, descriptions, metadata
 *  deltaNumber                 — mulishFontFamily, floating +N / −N delta indicator
 */
data class MagicTypography(

    // ── Display ───────────────────────────────────────────────────────────────
    val displayLarge: TextStyle = TextStyle(
        fontFamily    = MarcellusFontFamily,
        fontWeight    = FontWeight.Black,
        fontSize      = 40.sp,
        lineHeight    = 48.sp,
        letterSpacing = 1.sp,
    ),
    val displayMedium: TextStyle = TextStyle(
        fontFamily    = MarcellusFontFamily,
        fontWeight    = FontWeight.Black,
        fontSize      = 32.sp,
        lineHeight    = 40.sp,
        letterSpacing = 0.5.sp,
    ),

    // ── Life numerals ─────────────────────────────────────────────────────────
    val lifeNumber: TextStyle = TextStyle(
        fontFamily    = MarcellusFontFamily,
        fontWeight    = FontWeight.Black,
        fontSize      = 88.sp,
        lineHeight    = 88.sp,
        letterSpacing = (-2).sp,
    ),
    val lifeNumberMd: TextStyle = TextStyle(
        fontFamily    = MarcellusFontFamily,
        fontWeight    = FontWeight.Black,
        fontSize      = 64.sp,
        lineHeight    = 64.sp,
        letterSpacing = (-1).sp,
    ),

    // ── Titles ────────────────────────────────────────────────────────────────
    val titleLarge: TextStyle = TextStyle(
        fontFamily    = MarcellusFontFamily,
        fontWeight    = FontWeight.Bold,
        fontSize      = 22.sp,
        lineHeight    = 30.sp,
        letterSpacing = 1.sp,
    ),
    val titleMedium: TextStyle = TextStyle(
        fontFamily    = MarcellusFontFamily,
        fontWeight    = FontWeight.SemiBold,
        fontSize      = 18.sp,
        lineHeight    = 26.sp,
        letterSpacing = 1.sp,
    ),

    // ── Labels ────────────────────────────────────────────────────────────────
    val labelLarge: TextStyle = TextStyle(
        fontFamily    = MarcellusFontFamily,
        fontWeight    = FontWeight.Bold,
        fontSize      = 16.sp,
        lineHeight    = 20.sp,
        letterSpacing = 3.sp,
    ),
    val labelMedium: TextStyle = TextStyle(
        fontFamily    = MarcellusFontFamily,
        fontWeight    = FontWeight.SemiBold,
        fontSize      = 14.sp,
        lineHeight    = 18.sp,
        letterSpacing = 2.sp,
    ),
    val labelSmall: TextStyle = TextStyle(
        fontFamily    = MarcellusFontFamily,
        fontWeight    = FontWeight.Medium,
        fontSize      = 12.sp,
        lineHeight    = 15.sp,
        letterSpacing = 2.sp,
    ),

    // ── Body ──────────────────────────────────────────────────────────────────
    val bodyLarge: TextStyle = TextStyle(
        fontFamily    = MulishFontFamily,
        fontWeight    = FontWeight.SemiBold,
        fontSize      = 20.sp,
        lineHeight    = 28.sp,
        letterSpacing = 0.sp,
    ),
    val bodyMedium: TextStyle = TextStyle(
        fontFamily    = MulishFontFamily,
        fontWeight    = FontWeight.Medium,
        fontSize      = 18.sp,
        lineHeight    = 24.sp,
        letterSpacing = 0.sp,
    ),
    val bodySmall: TextStyle = TextStyle(
        fontFamily    = MulishFontFamily,
        fontWeight    = FontWeight.Medium,
        fontSize      = 16.sp,
        lineHeight    = 22.sp,
        letterSpacing = 0.sp,
    ),

    // ── Delta indicator ───────────────────────────────────────────────────────
    val deltaNumber: TextStyle = TextStyle(
        fontFamily    = MulishFontFamily,
        fontWeight    = FontWeight.Bold,
        fontSize      = 22.sp,
        lineHeight    = 28.sp,
        letterSpacing = 0.sp,
    ),
)

// ── NeonVoid instance (default values, override per theme if needed) ──────────
internal val NeonVoidTypography = MagicTypography()
