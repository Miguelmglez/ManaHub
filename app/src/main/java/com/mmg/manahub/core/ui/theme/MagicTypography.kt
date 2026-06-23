package com.mmg.manahub.core.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.mmg.manahub.R

// ── Font families loaded from res/font/ ──────────────────────────────────────
// These stay in :app because they reference Android R.font.* resources.
// Once the CMP resource system (Res) replaces R.font, they move to :shared:core-ui.

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

// ── NeonVoid typography instance (applies Marcellus + Mulish font families) ──
// The data class MagicTypography is now in :shared:core-ui (commonMain). This
// instance wires the Android font families into it — the default MagicTypography()
// uses system fonts; this instance overrides with the project's custom fonts.
internal val NeonVoidTypography = MagicTypography(
    displayLarge  = MagicTypography().displayLarge.copy(fontFamily = MarcellusFontFamily),
    displayMedium = MagicTypography().displayMedium.copy(fontFamily = MarcellusFontFamily),
    lifeNumber    = MagicTypography().lifeNumber.copy(fontFamily = MarcellusFontFamily),
    lifeNumberMd  = MagicTypography().lifeNumberMd.copy(fontFamily = MarcellusFontFamily),
    titleLarge    = MagicTypography().titleLarge.copy(fontFamily = MarcellusFontFamily),
    titleMedium   = MagicTypography().titleMedium.copy(fontFamily = MarcellusFontFamily),
    labelLarge    = MagicTypography().labelLarge.copy(fontFamily = MarcellusFontFamily),
    labelMedium   = MagicTypography().labelMedium.copy(fontFamily = MarcellusFontFamily),
    labelSmall    = MagicTypography().labelSmall.copy(fontFamily = MarcellusFontFamily),
    bodyLarge     = MagicTypography().bodyLarge.copy(fontFamily = MulishFontFamily),
    bodyMedium    = MagicTypography().bodyMedium.copy(fontFamily = MulishFontFamily),
    bodySmall     = MagicTypography().bodySmall.copy(fontFamily = MulishFontFamily),
    deltaNumber   = MagicTypography().deltaNumber.copy(fontFamily = MulishFontFamily),
)
