package com.mmg.manahub.core.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── Typography scale ─────────────────────────────────────────────────────────

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
 *
 * Default values use system-default font families (no custom fonts). Platform-specific
 * font families are applied by [createMagicTypography] in the platform source sets
 * (e.g. `androidMain` provides R.font.* resources, `wasmJsMain` uses web fonts).
 */
data class MagicTypography(

    // ── Display ───────────────────────────────────────────────────────────────
    val displayLarge: TextStyle = TextStyle(
        fontWeight    = FontWeight.Black,
        fontSize      = 34.sp,
        lineHeight    = 42.sp,
        letterSpacing = 1.sp,
    ),
    val displayMedium: TextStyle = TextStyle(
        fontWeight    = FontWeight.Black,
        fontSize      = 28.sp,
        lineHeight    = 36.sp,
        letterSpacing = 0.5.sp,
    ),

    // ── Life numerals ─────────────────────────────────────────────────────────
    val lifeNumber: TextStyle = TextStyle(
        fontWeight    = FontWeight.Black,
        fontSize      = 88.sp,
        lineHeight    = 88.sp,
        letterSpacing = (-2).sp,
    ),
    val lifeNumberMd: TextStyle = TextStyle(
        fontWeight    = FontWeight.Black,
        fontSize      = 64.sp,
        lineHeight    = 64.sp,
        letterSpacing = (-1).sp,
    ),

    // ── Titles ────────────────────────────────────────────────────────────────
    val titleLarge: TextStyle = TextStyle(
        fontWeight    = FontWeight.Bold,
        fontSize      = 20.sp,
        lineHeight    = 28.sp,
        letterSpacing = 1.sp,
    ),
    val titleMedium: TextStyle = TextStyle(
        fontWeight    = FontWeight.SemiBold,
        fontSize      = 16.sp,
        lineHeight    = 24.sp,
        letterSpacing = 1.sp,
    ),

    // ── Labels ────────────────────────────────────────────────────────────────
    val labelLarge: TextStyle = TextStyle(
        fontWeight    = FontWeight.Bold,
        fontSize      = 14.sp,
        lineHeight    = 18.sp,
        letterSpacing = 3.sp,
    ),
    val labelMedium: TextStyle = TextStyle(
        fontWeight    = FontWeight.SemiBold,
        fontSize      = 12.sp,
        lineHeight    = 16.sp,
        letterSpacing = 2.sp,
    ),
    val labelSmall: TextStyle = TextStyle(
        fontWeight    = FontWeight.Medium,
        fontSize      = 11.sp,
        lineHeight    = 14.sp,
        letterSpacing = 2.sp,
    ),

    // ── Body ──────────────────────────────────────────────────────────────────
    val bodyLarge: TextStyle = TextStyle(
        fontWeight    = FontWeight.SemiBold,
        fontSize      = 16.sp,
        lineHeight    = 24.sp,
        letterSpacing = 0.sp,
    ),
    val bodyMedium: TextStyle = TextStyle(
        fontWeight    = FontWeight.Medium,
        fontSize      = 14.sp,
        lineHeight    = 20.sp,
        letterSpacing = 0.sp,
    ),
    val bodySmall: TextStyle = TextStyle(
        fontWeight    = FontWeight.Medium,
        fontSize      = 12.sp,
        lineHeight    = 18.sp,
        letterSpacing = 0.sp,
    ),

    // ── Delta indicator ───────────────────────────────────────────────────────
    val deltaNumber: TextStyle = TextStyle(
        fontWeight    = FontWeight.Bold,
        fontSize      = 22.sp,
        lineHeight    = 28.sp,
        letterSpacing = 0.sp,
    ),
)
