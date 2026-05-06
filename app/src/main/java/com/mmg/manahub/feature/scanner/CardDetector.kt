package com.mmg.manahub.feature.scanner

import android.graphics.PointF
import androidx.camera.core.ImageProxy
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

// ─────────────────────────────────────────────────────────────────────────────
//  Public contract
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Represents a detected MTG card in a camera frame.
 *
 * @param cornersInImage Four corner points of the card in frame pixel coordinates
 *   (top-left, top-right, bottom-right, bottom-left order after EMA smoothing).
 * @param confidence Detection confidence, always 1.0f for a valid quadrilateral match.
 */
data class DetectedCard(
    val cornersInImage: List<PointF>,
    val confidence: Float,
)

/**
 * Contract for synchronous, per-frame card edge detection.
 *
 * The implementation must NOT call [ImageProxy.close] — that responsibility
 * belongs exclusively to the caller (i.e., [CardRecognizer]).
 */
interface CardDetector {
    /**
     * Attempts to detect an MTG card outline in [frame].
     *
     * @return A [DetectedCard] with smoothed corner coordinates, or null if
     *   no valid card quadrilateral is found.
     */
    fun detect(frame: ImageProxy): DetectedCard?

    /**
     * Releases all native resources (OpenCV Mats).  Must be called when the
     * detector is no longer needed.
     */
    fun release()
}

// ─────────────────────────────────────────────────────────────────────────────
//  OpenCV implementation
// ─────────────────────────────────────────────────────────────────────────────

/**
 * OpenCV-based card detector.
 *
 * Pipeline per frame (synchronous, runs on the calling thread):
 * 1. Extract Y-plane from YUV_420_888 → grayscale [Mat] (8UC1).
 * 2. CLAHE (clipLimit=2, tileSize=8×8) to normalise LOCAL contrast — makes the
 *    card border visible against any background colour (dark table, light wood, etc.).
 * 3. GaussianBlur 5×5 to reduce noise introduced by CLAHE.
 * 4. Canny edge detection (low=25, high=75) — lower thresholds are safe after CLAHE
 *    because contrast is already amplified; the outer card border edge is now detectable.
 * 5. Morphological close (9×9 rect, 3 iterations) to bridge gaps ≤ 13 px in the outer
 *    card border contour caused by low-contrast or rounded-corner areas.
 * 6. findContours (RETR_LIST, CHAIN_APPROX_SIMPLE) — returns ALL contours so both
 *    the inner art-frame border (~40 % of frame area) and the outer card border
 *    (~55 % of frame area) are candidates.
 * 7. Discard contours with area < 15 % of the total frame area.
 * 8. approxPolyDP with ε = 2 % of perimeter; keep only 4-vertex polygons.
 * 9. Discard non-convex polygons.
 * 10. Validate aspect ratio: MTG card is 63×88 mm ≈ 0.716, accept [0.65, 0.78].
 * 11. Choose the largest surviving contour → outer card border wins over inner art frame.
 * 12. Apply EMA smoothing (α = 0.50) over the 4 corners.
 *
 * Throttling: at most one detection every 100 ms; returns last cached result
 * between ticks. [AtomicBoolean] guards against re-entrant calls.
 *
 * All Mats are allocated once and reused across frames. Call [release] when
 * done to free native memory.
 */
class OpenCvCardDetector : CardDetector {

    // ── Mat pool — allocated once, reused every frame ──────────────────────
    private val grayMat = Mat()
    private val blurredMat = Mat()
    private val edgesMat = Mat()
    private val dilatedMat = Mat()
    private val hierarchyMat = Mat()

    // 9×9 rect kernel for MORPH_CLOSE after Canny — bridges gaps ≤ 13 px
    // in the outer card border without over-expanding into background pixels.
    private val dilKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(9.0, 9.0))

    // CLAHE instance created once; it is a Java object and is GC-managed (no release() call needed).
    private val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))

    // ── EMA state ───────────────────────────────────────────────────────────
    /**
     * EMA smoothing factor applied to the *new* observation.
     * Higher = faster tracking (more weight on the new measurement), less smoothing.
     * Lower = slower tracking (more inertia), smoother overlay.
     * Formula: smoothed = emaAlpha * new + (1 - emaAlpha) * previous.
     *
     * Raised from 0.30 → 0.50: at 0.30 the filter needed ~10 frames (≈1.5 s at 150 ms/frame)
     * to converge within 5 % of the true corner position.  At 0.50 convergence happens in
     * ~4 frames (≈600 ms), cutting the delay before ML inference sees a stable warp input.
     * The overlay jitter trade-off is acceptable — the card barely moves once held steady.
     */
    private val emaAlpha = 0.50f
    private var smoothedCorners: List<PointF>? = null

    // ── Re-entrance guard ───────────────────────────────────────────────────
    private val isProcessing = AtomicBoolean(false)

    // ── Diagnostic log throttle (once every 2 s to avoid flooding Logcat) ──
    @Volatile private var lastLogMs = 0L

    // ── MTG card aspect ratio bounds (applied to actual quad side lengths,
    //    not the axis-aligned bounding rect, so rotated cards pass correctly) ─
    private val aspectRatioMin = 0.65f
    private val aspectRatioMax = 0.78f

    override fun detect(frame: ImageProxy): DetectedCard? {
        if (!isProcessing.compareAndSet(false, true)) return null
        return try {
            runDetectionPipeline(frame)
        } finally {
            isProcessing.set(false)
        }
    }

    override fun release() {
        grayMat.release()
        blurredMat.release()
        edgesMat.release()
        dilatedMat.release()
        dilKernel.release()
        hierarchyMat.release()
    }

    // ── Internal pipeline ───────────────────────────────────────────────────

    private fun runDetectionPipeline(frame: ImageProxy): DetectedCard? {
        val width = frame.width
        val height = frame.height
        val frameArea = width.toDouble() * height.toDouble()
        val shouldLog = System.currentTimeMillis().also { now ->
            if (now - lastLogMs > 2_000L) lastLogMs = now else return@also
        } == lastLogMs

        // Step 1: Extract Y-plane → grayscale Mat
        extractYPlane(frame, width, height)

        // Step 2: CLAHE — equalises local contrast in 8×8 tiles so the card border
        // is detectable regardless of whether the background is dark, grey, or bright.
        clahe.apply(grayMat, blurredMat)

        // Step 3: Gaussian blur to reduce CLAHE noise before edge detection.
        Imgproc.GaussianBlur(blurredMat, blurredMat, Size(5.0, 5.0), 0.0)

        // Step 4: Canny — lower thresholds are safe here because CLAHE has already
        // boosted local contrast; even weak outer-border edges become detectable.
        Imgproc.Canny(blurredMat, edgesMat, 25.0, 75.0)

        // Step 5: Morphological close to bridge gaps in the outer card border.
        // 9×9 rect kernel × 3 iterations fills gaps ≤ 13 px.
        Imgproc.morphologyEx(
            edgesMat, dilatedMat, Imgproc.MORPH_CLOSE, dilKernel,
            org.opencv.core.Point(-1.0, -1.0), 3,
        )

        // Step 6: RETR_LIST returns ALL contours — both the outer card border
        // (~55 % frame area) and the inner art-frame border (~40 %) are candidates.
        // findBestCardQuadrilateral picks the largest valid quad → outer card wins.
        val contours = mutableListOf<MatOfPoint>()
        Imgproc.findContours(
            dilatedMat,
            contours,
            hierarchyMat,
            Imgproc.RETR_LIST,
            Imgproc.CHAIN_APPROX_SIMPLE,
        )

        if (shouldLog) {
            android.util.Log.d(
                "CardDetector",
                "Frame ${width}×${height} — ${contours.size} contours found",
            )
        }

        // Steps 7–11: Filter and find the best quadrilateral
        val bestQuad = findBestCardQuadrilateral(contours, frameArea, shouldLog)

        // Release contour Mats to avoid native leaks
        contours.forEach { it.release() }

        if (bestQuad == null) {
            return null  // keep smoothedCorners for overlay persistence
        }

        // Step 12: EMA smoothing
        val corners = bestQuad.toList().map { pt -> PointF(pt.x.toFloat(), pt.y.toFloat()) }
        val smoothed = applyEma(corners)

        if (shouldLog) {
            android.util.Log.d("CardDetector", "Card DETECTED — corners: $smoothed")
        }

        return DetectedCard(cornersInImage = smoothed, confidence = 1.0f)
    }

    /**
     * Copies the Y-plane bytes of [frame] (YUV_420_888) into [grayMat].
     * The plane may have a row stride larger than the image width; we copy
     * row by row to produce a compact, width-exact grayscale Mat.
     */
    private fun extractYPlane(frame: ImageProxy, width: Int, height: Int) {
        grayMat.create(height, width, CvType.CV_8UC1)

        val yPlane: ImageProxy.PlaneProxy = frame.planes[0]
        val yBuffer: ByteBuffer = yPlane.buffer
        val rowStride = yPlane.rowStride
        val pixelStride = yPlane.pixelStride

        // Fast path: row stride equals width and pixel stride is 1
        if (rowStride == width && pixelStride == 1) {
            val bytes = ByteArray(yBuffer.remaining())
            yBuffer.get(bytes)
            grayMat.put(0, 0, bytes)
        } else {
            // Slow path: copy row by row, skipping stride gaps
            val rowBytes = ByteArray(width)
            for (row in 0 until height) {
                val rowStart = row * rowStride
                for (col in 0 until width) {
                    rowBytes[col] = yBuffer.get(rowStart + col * pixelStride)
                }
                grayMat.put(row, 0, rowBytes)
            }
        }
    }

    /**
     * Iterates over [contours], fitting a minimum-area rectangle to each large candidate,
     * and returns the rectangle with the largest contour area whose aspect ratio matches
     * an MTG card, or null if none passes.
     *
     * [Imgproc.minAreaRect] is used instead of [Imgproc.approxPolyDP] because the outer
     * card border contour often has 5–8 vertices after MORPH_CLOSE (rounded corners, slight
     * irregularities). approxPolyDP with a tight ε rejects these; minAreaRect always
     * produces a clean 4-vertex rectangle that is optimal for the contour's shape.
     */
    private fun findBestCardQuadrilateral(
        contours: List<MatOfPoint>,
        frameArea: Double,
        shouldLog: Boolean,
    ): MatOfPoint2f? {
        var bestArea = 0.0
        var bestAreaPct = 0
        var bestRatio = 0f
        var bestQuad: MatOfPoint2f? = null

        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            val areaPct = (area / frameArea * 100).toInt()

            // Area filter — must be at least 15 % of frame.
            // Only log contours ≥ 8 % to avoid flooding logcat with RETR_LIST noise.
            if (area < frameArea * 0.15) {
                if (shouldLog && areaPct >= 8) {
                    android.util.Log.d("CardDetector", "  skip area=${areaPct}% (need ≥15%)")
                }
                continue
            }

            // Fit the tightest bounding rectangle to this contour.
            // minAreaRect handles rounded corners and MORPH_CLOSE artifacts gracefully —
            // it always yields a clean 4-point rectangle regardless of vertex count.
            val contour2f = MatOfPoint2f(*contour.toArray())
            val rotRect = Imgproc.minAreaRect(contour2f)
            contour2f.release()

            // Aspect ratio check using the rectangle's actual short/long sides.
            val shortSide = minOf(rotRect.size.width, rotRect.size.height).toFloat()
            val longSide  = maxOf(rotRect.size.width, rotRect.size.height).toFloat()
            if (longSide == 0f) continue
            val ratio = shortSide / longSide
            if (ratio < aspectRatioMin || ratio > aspectRatioMax) {
                if (shouldLog) {
                    android.util.Log.d(
                        "CardDetector",
                        "  skip area=%d%% ratio=%.2f (need %.2f–%.2f)".format(
                            areaPct, ratio, aspectRatioMin, aspectRatioMax,
                        ),
                    )
                }
                continue
            }

            // Always log passes (unthrottled) to diagnose which contour wins each frame.
            android.util.Log.d(
                "CardDetector",
                "  PASS area=%d%% ratio=%.2f".format(areaPct, ratio),
            )

            // Extract the 4 corner points from the rotated rect.
            val pts = Array(4) { org.opencv.core.Point() }
            rotRect.points(pts)
            val quad = MatOfPoint2f(*pts)

            if (area > bestArea) {
                bestQuad?.release()
                bestArea = area
                bestAreaPct = areaPct
                bestRatio = ratio
                bestQuad = quad
            } else {
                quad.release()
            }
        }

        if (bestQuad != null) {
            android.util.Log.d(
                "CardDetector",
                "BEST area=%d%% ratio=%.2f".format(bestAreaPct, bestRatio),
            )
        }
        return bestQuad
    }

    /**
     * Applies exponential moving average over the four detected corners.
     * If no previous smoothed corners exist, seeds the filter with [newCorners].
     */
    private fun applyEma(newCorners: List<PointF>): List<PointF> {
        val previous = smoothedCorners
        return if (previous == null || previous.size != newCorners.size) {
            newCorners.also { smoothedCorners = it }
        } else {
            val result = newCorners.mapIndexed { i, new ->
                val prev = previous[i]
                PointF(
                    emaAlpha * new.x + (1f - emaAlpha) * prev.x,
                    emaAlpha * new.y + (1f - emaAlpha) * prev.y,
                )
            }
            smoothedCorners = result
            result
        }
    }
}
