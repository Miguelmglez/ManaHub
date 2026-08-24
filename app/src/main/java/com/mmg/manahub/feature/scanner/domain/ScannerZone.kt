package com.mmg.manahub.feature.scanner.domain

/**
 * Single source of truth for the on-screen "card name" scan zone, shared between
 * [com.mmg.manahub.feature.scanner.data.CardOcrAnalyzer] (which restricts ML Kit OCR candidates
 * to this band of the camera frame) and `NameZoneIndicator` in
 * [com.mmg.manahub.feature.scanner.presentation.ScannerScreen] (which draws the matching guide on
 * screen). Living in `domain/` keeps this a neutral dependency for both — neither the `data/` nor
 * the `presentation/` package has to depend on the other's package to share it.
 *
 * Before this (WS2.A, 2026-08-24) the analyzer used its own `0.30f…0.60f` band with no horizontal
 * constraint at all, computed against a floating pseudo-height (`max` of detected text bottoms,
 * not the real frame height) — it never matched what the indicator actually drew. See finding F3
 * in `docs/plans/scanner-reliability-plan.md`.
 *
 * ### Fractions
 * [TOP]/[BOTTOM] are fractions of frame/screen **height**; [WIDTH] is a fraction of frame/screen
 * **width**, centred horizontally. [TOLERANCE] is an analyzer-only margin (applied on both sides
 * of [TOP]/[BOTTOM]/[WIDTH]) to tolerate slight tilt, hand shake, and any residual mismatch
 * between the `PreviewView` (`FILL_CENTER`) crop and the `ImageAnalysis` frame — see the
 * `AspectRatioStrategy` note on the camera-binding `LaunchedEffect` in `ScannerScreen.kt`. The
 * on-screen indicator draws the exact [TOP]/[BOTTOM]/[WIDTH] fractions, with no tolerance applied.
 */
object ScannerZone {
    /** Top edge of the name zone, as a fraction of frame/screen height. */
    const val TOP = 0.40f

    /** Bottom edge of the name zone, as a fraction of frame/screen height. */
    const val BOTTOM = 0.52f

    /** Width of the name zone, as a fraction of frame/screen width, centred horizontally. */
    const val WIDTH = 0.72f

    /**
     * Analyzer-only tolerance margin applied around [TOP]/[BOTTOM]/[WIDTH]. Not used by the
     * on-screen indicator.
     */
    const val TOLERANCE = 0.04f
}
