package com.mmg.manahub.feature.scanner.domain.model

/**
 * A single OCR text-line candidate for the card name, produced and scored by
 * `CardOcrAnalyzer.extractFromResult` within the
 * [com.mmg.manahub.feature.scanner.domain.ScannerZone] band.
 *
 * @property text            Cleaned candidate text (OCR-artefact substitutions applied — see
 *                            `CardOcrAnalyzer.cleanOcrText`).
 * @property score            Combined heuristic score: relative line height (dominant term) plus
 *                            horizontal/vertical centring, minus dash / rules-text-length /
 *                            keyword penalties. **Not gated on in WS2.A** — `CardRecognizer`
 *                            currently reads only [text]. WS2.B will use [score] to gate network
 *                            lookups on OCR confidence (pre-resolution stability, negative cache).
 * @property lineHeightRatio Line bounding-box height divided by the tallest surviving candidate's
 *                            height in the same frame, in the range `(0, 1]`. `1.0` means this
 *                            line is the tallest candidate — card names are expected to be the
 *                            largest text inside the name zone.
 */
data class OcrCandidate(
    val text: String,
    val score: Float,
    val lineHeightRatio: Float,
)
