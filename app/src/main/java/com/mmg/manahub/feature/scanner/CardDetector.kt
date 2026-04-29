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
 * 2. GaussianBlur 5×5.
 * 3. Canny edge detection (low=50, high=150).
 * 4. findContours (RETR_EXTERNAL, CHAIN_APPROX_SIMPLE).
 * 5. Discard contours with area < 15 % of the total frame area.
 * 6. approxPolyDP with ε = 2 % of perimeter; keep only 4-vertex polygons.
 * 7. Validate aspect ratio: MTG card is 63×88 mm ≈ 0.716, accept ±15 % → [0.608, 0.823].
 * 8. Choose the largest surviving contour.
 * 9. Apply EMA smoothing (α = 0.65) over the 4 corners.
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
    private val hierarchyMat = Mat()

    // ── EMA state ───────────────────────────────────────────────────────────
    /**
     * EMA smoothing factor applied to the *new* observation.
     * Higher = faster tracking (more weight on the new measurement), less smoothing.
     * Lower = slower tracking (more inertia), smoother overlay.
     * Formula: smoothed = emaAlpha * new + (1 - emaAlpha) * previous.
     */
    private val emaAlpha = 0.65f
    private var smoothedCorners: List<PointF>? = null

    // ── Throttling ──────────────────────────────────────────────────────────
    private val minIntervalMs = 100L
    @Volatile private var lastProcessedMs = 0L
    private val isProcessing = AtomicBoolean(false)

    /** Last result returned while throttling is active. */
    @Volatile private var lastResult: DetectedCard? = null

    // ── MTG card aspect ratio bounds ────────────────────────────────────────
    private val aspectRatioMin = 0.608f   // 0.716 - 15 %
    private val aspectRatioMax = 0.823f   // 0.716 + 15 %

    override fun detect(frame: ImageProxy): DetectedCard? {
        // Throttle: return cached result if called too soon
        val now = System.currentTimeMillis()
        if (now - lastProcessedMs < minIntervalMs) {
            return lastResult
        }

        // Guard against re-entrance (synchronous, but protects if called from
        // multiple threads simultaneously — e.g., during a config change).
        if (!isProcessing.compareAndSet(false, true)) {
            return lastResult
        }

        return try {
            lastProcessedMs = now
            lastResult = runDetectionPipeline(frame)
            lastResult
        } finally {
            isProcessing.set(false)
        }
    }

    override fun release() {
        grayMat.release()
        blurredMat.release()
        edgesMat.release()
        hierarchyMat.release()
    }

    // ── Internal pipeline ───────────────────────────────────────────────────

    private fun runDetectionPipeline(frame: ImageProxy): DetectedCard? {
        val width = frame.width
        val height = frame.height
        val frameArea = width.toDouble() * height.toDouble()

        // Step 1: Extract Y-plane → grayscale Mat
        extractYPlane(frame, width, height)

        // Step 2: Gaussian blur
        Imgproc.GaussianBlur(grayMat, blurredMat, Size(5.0, 5.0), 0.0)

        // Step 3: Canny edge detection
        Imgproc.Canny(blurredMat, edgesMat, 50.0, 150.0)

        // Step 4: Find external contours
        val contours = mutableListOf<MatOfPoint>()
        Imgproc.findContours(
            edgesMat,
            contours,
            hierarchyMat,
            Imgproc.RETR_EXTERNAL,
            Imgproc.CHAIN_APPROX_SIMPLE,
        )

        // Steps 5–7: Filter and find the best quadrilateral
        val bestQuad = findBestCardQuadrilateral(contours, frameArea)

        // Release contour Mats to avoid native leaks
        contours.forEach { it.release() }

        if (bestQuad == null) {
            // Reset smoothing when no card is found so stale corners are not
            // shown after the card disappears from the frame.
            smoothedCorners = null
            return null
        }

        // Step 8: EMA smoothing
        val corners = bestQuad.toList().map { pt -> PointF(pt.x.toFloat(), pt.y.toFloat()) }
        val smoothed = applyEma(corners)

        // Step 9: Build result
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
     * Iterates over [contours], applying area and aspect-ratio filters, and
     * returns the quadrilateral with the largest area, or null if none passes.
     */
    private fun findBestCardQuadrilateral(
        contours: List<MatOfPoint>,
        frameArea: Double,
    ): MatOfPoint2f? {
        var bestArea = 0.0
        var bestQuad: MatOfPoint2f? = null

        for (contour in contours) {
            // Step 5: Area filter — must be at least 15 % of frame
            val area = Imgproc.contourArea(contour)
            if (area < frameArea * 0.15) continue

            // Step 6: Polygon approximation — keep only quadrilaterals
            val contour2f = MatOfPoint2f(*contour.toArray())
            val perimeter = Imgproc.arcLength(contour2f, true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(contour2f, approx, 0.02 * perimeter, true)
            contour2f.release()

            if (approx.rows() != 4) {
                approx.release()
                continue
            }

            // Step 7: Aspect ratio validation
            val rect = Imgproc.boundingRect(MatOfPoint(*approx.toArray()))
            val shorter = minOf(rect.width, rect.height).toFloat()
            val longer = maxOf(rect.width, rect.height).toFloat()
            if (longer == 0f) {
                approx.release()
                continue
            }
            val ratio = shorter / longer
            if (ratio < aspectRatioMin || ratio > aspectRatioMax) {
                approx.release()
                continue
            }

            // Keep the quad with the largest contour area
            if (area > bestArea) {
                bestQuad?.release()
                bestArea = area
                bestQuad = approx
            } else {
                approx.release()
            }
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
            // First detection or size mismatch — seed with raw corners
            newCorners.also { smoothedCorners = it }
        } else {
            // EMA: smoothed = emaAlpha * new + (1 - emaAlpha) * previous
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
