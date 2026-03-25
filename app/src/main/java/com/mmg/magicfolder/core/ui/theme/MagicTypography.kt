package com.mmg.magicfolder.core.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mmg.magicfolder.R

// ── Font families cargadas desde res/font/ ────────────────────────────────────

val CinzelFontFamily = FontFamily(
    Font(R.font.cinzel_regular,   FontWeight.Normal),
    Font(R.font.cinzel_semibold,  FontWeight.SemiBold),
    Font(R.font.cinzel_bold,      FontWeight.Bold),
    Font(R.font.cinzel_extrabold, FontWeight.ExtraBold),
    Font(R.font.cinzel_black,     FontWeight.Black),
)

val RajdhaniFontFamily = FontFamily(
    Font(R.font.rajdhani_light,    FontWeight.Light),
    Font(R.font.rajdhani_regular,  FontWeight.Normal),
    Font(R.font.rajdhani_medium,   FontWeight.Medium),
    Font(R.font.rajdhani_semibold, FontWeight.SemiBold),
    Font(R.font.rajdhani_bold,     FontWeight.Bold),
)

val ManaFontFamily = FontFamily(
    Font(R.font.mana, FontWeight.Normal)
)

// ── Escala tipográfica ────────────────────────────────────────────────────────

/**
 * Typography scale for the MagicFolder design system.
 *
 * Hierarchy:
 *  displayLarge / displayMedium — CinzelFontFamily Black, section titles, victory screen
 *  lifeNumber / lifeNumberMd   — CinzelFontFamily Black, main life-total numeral
 *  titleLarge / titleMedium    — CinzelFontFamily, card titles, feature headers
 *  labelLarge … labelSmall     — CinzelFontFamily, nav tabs, chips, badges
 *  bodyLarge … bodySmall       — RajdhaniFontFamily, prose, descriptions, metadata
 *  deltaNumber                 — RajdhaniFontFamily, floating +N / −N delta indicator
 */
data class MagicTypography(

    // ── Display ───────────────────────────────────────────────────────────────
    val displayLarge: TextStyle = TextStyle(
        fontFamily    = CinzelFontFamily,
        fontWeight    = FontWeight.Black,
        fontSize      = 40.sp,
        lineHeight    = 48.sp,
        letterSpacing = 1.sp,
    ),
    val displayMedium: TextStyle = TextStyle(
        fontFamily    = CinzelFontFamily,
        fontWeight    = FontWeight.Black,
        fontSize      = 32.sp,
        lineHeight    = 40.sp,
        letterSpacing = 0.5.sp,
    ),

    // ── Life numerals ─────────────────────────────────────────────────────────
    val lifeNumber: TextStyle = TextStyle(
        fontFamily    = CinzelFontFamily,
        fontWeight    = FontWeight.Black,
        fontSize      = 88.sp,
        lineHeight    = 88.sp,
        letterSpacing = (-2).sp,
    ),
    val lifeNumberMd: TextStyle = TextStyle(
        fontFamily    = CinzelFontFamily,
        fontWeight    = FontWeight.Black,
        fontSize      = 64.sp,
        lineHeight    = 64.sp,
        letterSpacing = (-1).sp,
    ),

    // ── Titles ────────────────────────────────────────────────────────────────
    val titleLarge: TextStyle = TextStyle(
        fontFamily    = CinzelFontFamily,
        fontWeight    = FontWeight.Bold,
        fontSize      = 20.sp,
        lineHeight    = 28.sp,
        letterSpacing = 1.sp,
    ),
    val titleMedium: TextStyle = TextStyle(
        fontFamily    = CinzelFontFamily,
        fontWeight    = FontWeight.SemiBold,
        fontSize      = 16.sp,
        lineHeight    = 24.sp,
        letterSpacing = 1.sp,
    ),

    // ── Labels ────────────────────────────────────────────────────────────────
    val labelLarge: TextStyle = TextStyle(
        fontFamily    = CinzelFontFamily,
        fontWeight    = FontWeight.SemiBold,
        fontSize      = 12.sp,
        lineHeight    = 16.sp,
        letterSpacing = 3.sp,
    ),
    val labelMedium: TextStyle = TextStyle(
        fontFamily    = CinzelFontFamily,
        fontWeight    = FontWeight.SemiBold,
        fontSize      = 10.sp,
        lineHeight    = 14.sp,
        letterSpacing = 2.sp,
    ),
    val labelSmall: TextStyle = TextStyle(
        fontFamily    = CinzelFontFamily,
        fontWeight    = FontWeight.Medium,
        fontSize      = 9.sp,
        lineHeight    = 12.sp,
        letterSpacing = 2.sp,
    ),

    // ── Body ──────────────────────────────────────────────────────────────────
    val bodyLarge: TextStyle = TextStyle(
        fontFamily    = RajdhaniFontFamily,
        fontWeight    = FontWeight.Medium,
        fontSize      = 16.sp,
        lineHeight    = 24.sp,
        letterSpacing = 0.sp,
    ),
    val bodyMedium: TextStyle = TextStyle(
        fontFamily    = RajdhaniFontFamily,
        fontWeight    = FontWeight.Normal,
        fontSize      = 14.sp,
        lineHeight    = 20.sp,
        letterSpacing = 0.sp,
    ),
    val bodySmall: TextStyle = TextStyle(
        fontFamily    = RajdhaniFontFamily,
        fontWeight    = FontWeight.Normal,
        fontSize      = 12.sp,
        lineHeight    = 16.sp,
        letterSpacing = 0.sp,
    ),

    // ── Delta indicator ───────────────────────────────────────────────────────
    val deltaNumber: TextStyle = TextStyle(
        fontFamily    = RajdhaniFontFamily,
        fontWeight    = FontWeight.Bold,
        fontSize      = 22.sp,
        lineHeight    = 28.sp,
        letterSpacing = 0.sp,
    ),
)

// ── NeonVoid instance (default values, override per theme if needed) ──────────
internal val NeonVoidTypography = MagicTypography()
