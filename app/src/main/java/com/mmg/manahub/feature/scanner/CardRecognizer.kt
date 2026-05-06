package com.mmg.manahub.feature.scanner

import android.graphics.PointF
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.mmg.manahub.core.domain.model.DataResult
import com.mmg.manahub.core.domain.repository.CardRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * [ImageAnalysis.Analyzer] that combines OpenCV card edge detection with a TFLite
 * MobileNetV3-Small feature extractor and cosine nearest-neighbour search for
 * on-device, network-free card recognition.
 *
 * Pipeline per frame:
 * 1. Throttle (150 ms between processed frames) and re-entrance guard.
 * 2. [OpenCvCardDetector.detect] — synchronous quad detection on the calling thread.
 * 3. Sort corners (TL, TR, BR, BL) via [sortCorners].
 * 4. Apply rotation permutation (same logic for rot=90/180/270).
 * 5. [warpCard] → 224×224 BGR [Mat] (grayscale Y-plane replicated to 3 channels for V1).
 * 6. [CardEmbeddingModel.extract] → L2-normalised float[576] embedding, or null.
 * 7. [EmbeddingDatabase.findBestMatch] → [CardMatch] or null.
 * 8. [CardRepository.getCardById] on [Dispatchers.IO].
 * 9. Emit [RecognitionResult] via [onResult].
 *
 * If any step from 5 onwards produces null, [RecognitionResult.Detected] is emitted
 * so the overlay remains visible while the ML pipeline is warming up.
 *
 * Mat pool: [warpedBgrMat] and the warp transform matrices are
 * allocated once and reused. Call [release] when the recognizer is no longer needed.
 *
 * @param embeddingDatabase In-memory embedding DB used for cosine nearest-neighbour lookup.
 * @param cardEmbeddingModel TFLite inference wrapper that converts a 224×224 BGR Mat to a
 *                           L2-normalised float[576] embedding.
 * @param cardRepository     Domain repository used to resolve the matched scryfallId.
 * @param scope              [CoroutineScope] for suspending operations.
 * @param onResult           Callback invoked on every frame with the recognition outcome.
 */
class CardRecognizer(
    private val embeddingDatabase: EmbeddingDatabase,
    private val cardEmbeddingModel: CardEmbeddingModel,
    private val cardRepository: CardRepository,
    private val scope: CoroutineScope,
    private val onResult: (RecognitionResult) -> Unit,
) : ImageAnalysis.Analyzer {

    // ── Warp destination size for the ML model ───────────────────────────────
    private val warpSize = 224.0

    // ── Mat pool — allocated once, reused every frame ────────────────────────
    private val warpedBgrMat = Mat()

    // ── OpenCV card detector ─────────────────────────────────────────────────
    private val cardDetector = OpenCvCardDetector()

    // ── Warp transform source/destination points ─────────────────────────────
    private val srcPoints = MatOfPoint2f()
    private val dstPoints = MatOfPoint2f()

    // ── Throttling ───────────────────────────────────────────────────────────
    // 150 ms gives the full pipeline (OpenCV detect + TFLite embed + cosine search)
    // enough headroom to finish before the next frame arrives.  At 6–7 FPS the UI
    // overlay remains smooth while the CPU stays below thermal throttle on mid-range devices.
    private val minIntervalMs = 150L
    @Volatile private var lastProcessedMs = 0L
    private val isProcessing = AtomicBoolean(false)

    // ─────────────────────────────────────────────────────────────────────────
    //  ImageAnalysis.Analyzer entry point
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Entry point called for each camera frame.
     *
     * The [ImageProxy] is closed exactly once in every execution path.
     * Early exits (throttle, re-entrant, no detection) close it synchronously.
     * The coroutine path closes it after the repository call or in the catch block.
     */
    override fun analyze(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()

        // Throttle to max ~6-7 FPS — matches the pipeline budget of 150 ms/frame
        if (now - lastProcessedMs < minIntervalMs) {
            imageProxy.close()
            return
        }

        // Guard against re-entrant calls
        if (!isProcessing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        lastProcessedMs = now

        // Step 1: Detect card quadrilateral (synchronous, on calling thread)
        val detected = try {
            android.os.Trace.beginSection("CardRecognizer.detect")
            try {
                cardDetector.detect(imageProxy)
            } finally {
                android.os.Trace.endSection()
            }
        } catch (e: Exception) {
            android.util.Log.w("CardRecognizer", "detect() threw an exception", e)
            null
        }

        if (detected == null || detected.cornersInImage.size < 4) {
            isProcessing.set(false)
            imageProxy.close()
            onResult(RecognitionResult.NoCard)
            return
        }

        // Step 2: Sort corners (TL, TR, BR, BL) for consistent warp
        val sortedCorners = sortCorners(detected.cornersInImage)

        // Steps 3–8: warp + embedding extraction + lookup (CPU-bound, launched on Default)
        scope.launch(Dispatchers.Default) {
            try {
                // Step 3: Apply rotation permutation so the card is upright in the warp
                val rotation = imageProxy.imageInfo.rotationDegrees
                val rotatedCorners = when (rotation) {
                    90  -> listOf(sortedCorners[3], sortedCorners[0], sortedCorners[1], sortedCorners[2])
                    180 -> listOf(sortedCorners[2], sortedCorners[3], sortedCorners[0], sortedCorners[1])
                    270 -> listOf(sortedCorners[1], sortedCorners[2], sortedCorners[3], sortedCorners[0])
                    else -> sortedCorners  // 0°: no correction needed
                }

                // Clamp corners to the frame boundary so warpPerspective receives valid source points
                // even when EMA smoothing temporarily pushes a corner slightly outside the image.
                val fw = imageProxy.width.toFloat()
                val fh = imageProxy.height.toFloat()
                val clampedCorners = rotatedCorners.map { pt ->
                    PointF(pt.x.coerceIn(0f, fw - 1f), pt.y.coerceIn(0f, fh - 1f))
                }

                // Step 4: Warp to 224×224 BGR (grayscale→3ch for V1)
                android.os.Trace.beginSection("CardRecognizer.warpCard")
                val warpedMat: Mat?
                try {
                    warpedMat = warpCard(imageProxy, clampedCorners)
                } finally {
                    android.os.Trace.endSection()
                }

                if (warpedMat == null) {
                    imageProxy.close()
                    isProcessing.set(false)
                    onResult(RecognitionResult.Detected(sortedCorners))
                    return@launch
                }

                // Step 5: Extract embedding
                android.os.Trace.beginSection("CardRecognizer.embeddingExtract")
                val embedding: FloatArray?
                try {
                    embedding = cardEmbeddingModel.extract(warpedMat)
                } finally {
                    android.os.Trace.endSection()
                }

                if (embedding == null) {
                    android.util.Log.d("CardRecognizer", "embedding extraction returned null")
                    imageProxy.close()
                    isProcessing.set(false)
                    onResult(RecognitionResult.Detected(sortedCorners))
                    return@launch
                }

                // Step 6: Cosine nearest-neighbour lookup
                android.os.Trace.beginSection("CardRecognizer.cosineSearch")
                val match: CardMatch?
                try {
                    match = embeddingDatabase.findBestMatch(embedding)
                } finally {
                    android.os.Trace.endSection()
                }

                if (match == null) {
                    android.util.Log.d("CardRecognizer", "no match above threshold")
                    imageProxy.close()
                    isProcessing.set(false)
                    onResult(RecognitionResult.Detected(sortedCorners))
                    return@launch
                }

                android.util.Log.d(
                    "CardRecognizer",
                    "MATCH: id=${match.scryfallId}, sim=%.4f".format(match.similarity),
                )

                // Step 7: Resolve card from repository (IO-bound)
                android.os.Trace.beginSection("CardRecognizer.repoLookup")
                val cardResult = try {
                    withContext(Dispatchers.IO) {
                        cardRepository.getCardById(match.scryfallId)
                    }
                } finally {
                    android.os.Trace.endSection()
                }

                imageProxy.close()
                isProcessing.set(false)

                when (cardResult) {
                    is DataResult.Success -> {
                        val ambiguous = (match.similarity - match.secondBestSimilarity) < EmbeddingDatabase.AMBIGUITY_GAP
                        onResult(
                            RecognitionResult.Identified(
                                card = cardResult.data,
                                similarity = match.similarity,
                                ambiguous = ambiguous,
                                corners = sortedCorners,
                            ),
                        )
                    }
                    is DataResult.Error -> {
                        // Repository lookup failed — keep overlay visible
                        onResult(RecognitionResult.Detected(sortedCorners))
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("CardRecognizer", "Pipeline exception in warp/embed/lookup", e)
                imageProxy.close()
                isProcessing.set(false)
                onResult(RecognitionResult.NoCard)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Warp — grayscale Y-plane replicated to 3 channels (V1 approach)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Applies perspective warp to the camera frame and produces a 224×224 BGR [Mat].
     *
     * Converts the full YUV_420_888 frame to BGR (preserving colour) before warping,
     * so the embedding matches the colour images used to build the database.
     *
     * @param imageProxy    Current camera frame (must remain open during this call).
     * @param sortedCorners Four corners in TL, TR, BR, BL order (after rotation correction).
     * @return A 224×224 CV_8UC3 BGR [Mat], or null on error.
     */
    private fun warpCard(imageProxy: ImageProxy, sortedCorners: List<PointF>): Mat? {
        return try {
            val width = imageProxy.width
            val height = imageProxy.height

            // Build a full-colour BGR Mat from YUV_420_888 planes.
            // Most Android devices use semi-planar UV (pixel stride = 2).
            val yPlane  = imageProxy.planes[0]
            val uPlane  = imageProxy.planes[1]
            val vPlane  = imageProxy.planes[2]
            val uvPixelStride = uPlane.pixelStride

            val srcBgrMat: Mat
            if (uvPixelStride == 2) {
                // Semi-planar (NV12/NV21): build NV21 (YYYY…VUVU…) and let OpenCV convert.
                val yRowStride = yPlane.rowStride
                val uvRowStride = uPlane.rowStride
                val uvHeight = height / 2
                val uvWidth  = width  / 2

                // duplicate() gives an independent position/limit; clear() opens full capacity range
                val yBuf = (yPlane.buffer.duplicate() as java.nio.ByteBuffer).also { it.clear() }
                val vBuf = (vPlane.buffer.duplicate() as java.nio.ByteBuffer).also { it.clear() }
                val uBuf = (uPlane.buffer.duplicate() as java.nio.ByteBuffer).also { it.clear() }

                val nv21 = ByteArray(width * height + width * uvHeight)

                // Copy Y rows, one bulk read per row to strip stride padding
                for (row in 0 until height) {
                    val srcPos = row * yRowStride
                    if (srcPos + width <= yBuf.limit()) {
                        yBuf.position(srcPos)
                        yBuf.get(nv21, row * width, width)
                    }
                }

                // Interleave V,U for NV21 using absolute get() to avoid limit issues
                var uvOffset = width * height
                val vLimit = vBuf.limit()
                val uLimit = uBuf.limit()
                for (row in 0 until uvHeight) {
                    for (col in 0 until uvWidth) {
                        val idx = row * uvRowStride + col * uvPixelStride
                        nv21[uvOffset++] = if (idx < vLimit) vBuf.get(idx) else 0
                        nv21[uvOffset++] = if (idx < uLimit) uBuf.get(idx) else 0
                    }
                }

                val nv21Mat = Mat(height + uvHeight, width, CvType.CV_8UC1)
                nv21Mat.put(0, 0, nv21)
                srcBgrMat = Mat()
                Imgproc.cvtColor(nv21Mat, srcBgrMat, Imgproc.COLOR_YUV2BGR_NV21)
                nv21Mat.release()
            } else {
                // Fully planar I420 (pixel stride = 1): Y + U + V each contiguous.
                val uvHeight = height / 2
                val yBytes = ByteArray(width * height)
                val uBytes = ByteArray(width * uvHeight / 2)
                val vBytes = ByteArray(width * uvHeight / 2)
                (yPlane.buffer.duplicate() as java.nio.ByteBuffer).also { it.clear() }.get(yBytes)
                (uPlane.buffer.duplicate() as java.nio.ByteBuffer).also { it.clear() }.get(uBytes)
                (vPlane.buffer.duplicate() as java.nio.ByteBuffer).also { it.clear() }.get(vBytes)

                val i420 = yBytes + uBytes + vBytes
                val i420Mat = Mat(height + uvHeight, width, CvType.CV_8UC1)
                i420Mat.put(0, 0, i420)
                srcBgrMat = Mat()
                Imgproc.cvtColor(i420Mat, srcBgrMat, Imgproc.COLOR_YUV2BGR_I420)
                i420Mat.release()
            }

            // Source corners: TL, TR, BR, BL
            srcPoints.fromArray(
                Point(sortedCorners[0].x.toDouble(), sortedCorners[0].y.toDouble()),
                Point(sortedCorners[1].x.toDouble(), sortedCorners[1].y.toDouble()),
                Point(sortedCorners[2].x.toDouble(), sortedCorners[2].y.toDouble()),
                Point(sortedCorners[3].x.toDouble(), sortedCorners[3].y.toDouble()),
            )

            // Destination: canonical 224×224 rectangle
            dstPoints.fromArray(
                Point(0.0, 0.0),
                Point(warpSize, 0.0),
                Point(warpSize, warpSize),
                Point(0.0, warpSize),
            )

            val transformMat = Imgproc.getPerspectiveTransform(srcPoints, dstPoints)
            Imgproc.warpPerspective(srcBgrMat, warpedBgrMat, transformMat, Size(warpSize, warpSize))
            srcBgrMat.release()
            transformMat.release()

            // Clone so the caller owns an independent Mat; warpedBgrMat stays valid for the next frame.
            warpedBgrMat.clone()
        } catch (e: Exception) {
            android.util.Log.w("CardRecognizer", "warpCard() failed", e)
            null
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Corner sorting
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Sorts the four detected corners into (top-left, top-right, bottom-right, bottom-left)
     * by computing each corner's angle relative to the centroid.
     *
     * The x+y / x−y heuristic breaks when two corners share the same diagonal projection
     * (e.g. a card tilted ~45°). The angular sort is robust for any orientation.
     */
    private fun sortCorners(corners: List<PointF>): List<PointF> {
        val cx = corners.map { it.x }.average().toFloat()
        val cy = corners.map { it.y }.average().toFloat()
        // Sort CCW starting from the corner closest to −135° (top-left quadrant).
        val byAngle = corners.sortedBy { Math.atan2((it.y - cy).toDouble(), (it.x - cx).toDouble()) }
        // atan2 order: left(−π), bottom-left(−π/2 .. −π), top-left(−π .. 0), top-right(0 .. π/2), right/bottom-right(π/2 .. π)
        // We need TL, TR, BR, BL. Find the index of TL = corner with minimum (x+y) among the angularly-sorted list.
        val tlIdx = byAngle.indices.minByOrNull { byAngle[it].x + byAngle[it].y }!!
        val n = byAngle.size
        return listOf(
            byAngle[tlIdx],
            byAngle[(tlIdx + 1) % n],
            byAngle[(tlIdx + 2) % n],
            byAngle[(tlIdx + 3) % n],
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Cleanup
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Releases all native OpenCV [Mat] resources.
     * Must be called when the recognizer is no longer needed (e.g., when the camera unbinds).
     */
    fun release() {
        cardDetector.release()
        warpedBgrMat.release()
        srcPoints.release()
        dstPoints.release()
    }
}
