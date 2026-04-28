package com.mmg.manahub.feature.scanner

import android.graphics.PointF
import androidx.annotation.VisibleForTesting
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.DataResult
import com.mmg.manahub.core.domain.repository.CardRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

// ─────────────────────────────────────────────────────────────────────────────
//  Recognition result sealed interface
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Represents the outcome of a single frame processed by [CardRecognizer].
 */
sealed interface RecognitionResult {
    /** No card quadrilateral detected in the frame. */
    data object NoCard : RecognitionResult

    /**
     * A card outline was detected but the hash lookup returned no match
     * (either the database is empty or no card is close enough).
     *
     * @property corners Four corner points in frame pixel coordinates.
     */
    data class Detected(val corners: List<PointF>) : RecognitionResult

    /**
     * A card was detected and successfully identified via pHash lookup.
     *
     * @property card       Resolved domain [Card] from [CardRepository].
     * @property confidence Recognition confidence in [0, 1]: `1 - distance / 32`.
     * @property ambiguous  True when the gap between the best and second-best match is < 5 bits.
     * @property corners    Four corner points in frame pixel coordinates.
     */
    data class Identified(
        val card: Card,
        val confidence: Float,
        val ambiguous: Boolean,
        val corners: List<PointF>,
    ) : RecognitionResult
}

// ─────────────────────────────────────────────────────────────────────────────
//  CardRecognizer
// ─────────────────────────────────────────────────────────────────────────────

/**
 * [ImageAnalysis.Analyzer] that combines OpenCV card edge detection with
 * perceptual-hash (pHash) lookup for on-device, network-free card recognition.
 *
 * Pipeline per frame:
 * 1. [OpenCvCardDetector.detect] — finds the card quadrilateral.
 * 2. Sort corners (TL, TR, BR, BL) for a consistent warp transform.
 * 3. [Imgproc.getPerspectiveTransform] + [Imgproc.warpPerspective] → 488×680 px card image.
 * 4. Crop art zone: rows 8%–53%, cols 7%–93%.
 * 5. Resize to 64×64, convert to float32, apply 2D DCT.
 * 6. Take top-left 16×16 sub-matrix (256 coefficients), compute median, threshold → 256-bit hash.
 *    Bit packing: MSB first, matching Python `int.to_bytes(32, 'big')`.
 * 7. [HashDatabase.findBestMatch] → [HashMatch] or null.
 * 8. If matched: fetch [Card] from [CardRepository] on [Dispatchers.IO].
 * 9. Report [RecognitionResult] via [onResult] callback.
 *
 * Throttling: maximum 10 FPS (100 ms between frames processed).
 * Mat pool: all OpenCV Mats are allocated once and reused. Call [release] when done.
 *
 * @param hashDatabase   Singleton hash database loaded from assets.
 * @param cardRepository Domain repository used to resolve the matched scryfallId.
 * @param scope          [CoroutineScope] for suspending hash lookup and IO operations.
 * @param onResult       Callback invoked on every frame with the recognition outcome.
 */
class CardRecognizer(
    private val hashDatabase: HashDatabase,
    private val cardRepository: CardRepository,
    private val scope: CoroutineScope,
    private val onResult: (RecognitionResult) -> Unit,
) : ImageAnalysis.Analyzer {

    // ── Warp destination size ────────────────────────────────────────────────
    private val warpWidth = 488.0
    private val warpHeight = 680.0

    // ── Art crop ratios ──────────────────────────────────────────────────────
    private val artRowStart = 0.08
    private val artRowEnd = 0.53
    private val artColStart = 0.07
    private val artColEnd = 0.93

    // ── DCT hash parameters ──────────────────────────────────────────────────
    private val dctSize = 64
    private val dctSubSize = 16  // top-left sub-matrix for hash

    // ── Mat pool — allocated once, reused every frame ────────────────────────
    private val warpedMat = Mat()
    private val grayMat = Mat()
    private val floatMat = Mat()
    private val dctMat = Mat()
    private val croppedMat = Mat()
    private val resizedMat = Mat()

    // ── OpenCV card detector ─────────────────────────────────────────────────
    private val cardDetector = OpenCvCardDetector()

    // ── Warp transform matrices (recreated each frame, small allocation) ─────
    private val srcPoints = MatOfPoint2f()
    private val dstPoints = MatOfPoint2f()

    // ── Throttling ───────────────────────────────────────────────────────────
    private val minIntervalMs = 100L
    @Volatile private var lastProcessedMs = 0L
    private val isProcessing = AtomicBoolean(false)

    /**
     * Entry point called by [FrameMetadataAnalyzer] for each camera frame.
     *
     * Full pipeline:
     * 1. Throttle: frames arriving faster than [minIntervalMs] are dropped and the
     *    [ImageProxy] is closed immediately.
     * 2. Re-entrancy guard: if a previous frame is still being processed the new
     *    frame is dropped.
     * 3. [OpenCvCardDetector.detect] — synchronous quad detection on the calling thread.
     * 4. Corner sort (TL, TR, BR, BL) via [sortCorners].
     * 5–7. Warp + pHash computation + [HashDatabase.findBestMatch] — launched on
     *    [Dispatchers.Default] to avoid blocking the camera executor.
     * 8. [CardRepository.getCardById] — IO-bound, switched to [Dispatchers.IO].
     * 9. [onResult] is invoked on whichever dispatcher step 5 ran on.
     *
     * The [ImageProxy] is closed **exactly once** in every execution path:
     * - Early exits (throttle, re-entrant, no detection) close it on the calling thread.
     * - All paths inside the coroutine close it before or after the repository call,
     *   with the coroutine's top-level `catch` as the final safety net.
     *
     * [android.os.Trace] sections instrument the four most expensive sub-steps:
     * `CardRecognizer.detect`, `CardRecognizer.warpAndHash`,
     * `CardRecognizer.hammingLookup`, and `CardRecognizer.repoLookup`.
     */
    override fun analyze(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()

        // Throttle to max 10 FPS
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

        // Step 1: Detect card quadrilateral
        val detected = try {
            android.os.Trace.beginSection("CardRecognizer.detect")
            try {
                cardDetector.detect(imageProxy)
            } finally {
                android.os.Trace.endSection()
            }
        } catch (_: Exception) {
            null
        }

        if (detected == null || detected.cornersInImage.size < 4) {
            isProcessing.set(false)
            imageProxy.close()
            onResult(RecognitionResult.NoCard)
            return
        }

        val corners = detected.cornersInImage

        // Step 2: Sort corners (TL, TR, BR, BL) for consistent warp
        val sortedCorners = sortCorners(corners)

        // Steps 3–7: warp + hash computation + lookup (all CPU-bound, launched on Default)
        scope.launch(Dispatchers.Default) {
            try {
                val hash: LongArray?
                android.os.Trace.beginSection("CardRecognizer.warpAndHash")
                try {
                    hash = computeHash(imageProxy, sortedCorners)
                } finally {
                    android.os.Trace.endSection()
                }

                if (hash == null) {
                    imageProxy.close()
                    isProcessing.set(false)
                    onResult(RecognitionResult.Detected(sortedCorners))
                    return@launch
                }

                // Step 7: Hash lookup
                val match: HashMatch?
                android.os.Trace.beginSection("CardRecognizer.hammingLookup")
                try {
                    match = hashDatabase.findBestMatch(hash)
                } finally {
                    android.os.Trace.endSection()
                }

                if (match == null) {
                    imageProxy.close()
                    isProcessing.set(false)
                    onResult(RecognitionResult.Detected(sortedCorners))
                    return@launch
                }

                // Step 8: Resolve card from repository (IO-bound)
                val cardResult: DataResult<Card>
                android.os.Trace.beginSection("CardRecognizer.repoLookup")
                try {
                    cardResult = kotlinx.coroutines.withContext(Dispatchers.IO) {
                        cardRepository.getCardById(match.scryfallId)
                    }
                } finally {
                    android.os.Trace.endSection()
                }

                imageProxy.close()
                isProcessing.set(false)

                when (cardResult) {
                    is DataResult.Success -> {
                        val confidence = 1f - match.distance / 32f
                        val ambiguous = (match.secondBestDistance - match.distance) < 5
                        onResult(
                            RecognitionResult.Identified(
                                card = cardResult.data,
                                confidence = confidence,
                                ambiguous = ambiguous,
                                corners = sortedCorners,
                            ),
                        )
                    }
                    is DataResult.Error -> {
                        // Repository lookup failed — report as detected-only
                        onResult(RecognitionResult.Detected(sortedCorners))
                    }
                }
            } catch (_: Exception) {
                imageProxy.close()
                isProcessing.set(false)
                onResult(RecognitionResult.NoCard)
            }
        }
    }

    /**
     * Performs warp perspective and DCT-based perceptual hash computation on [imageProxy].
     *
     * Steps:
     * 1. Warp the card to [warpWidth]×[warpHeight] using [warpCard].
     * 2. Convert to grayscale (handles 1-, 3-, and 4-channel source Mats).
     * 3. Crop the art zone: rows [[artRowStart], [artRowEnd]) × cols [[artColStart], [artColEnd]).
     * 4. Resize to [dctSize]×[dctSize] and convert to float32.
     * 5. Apply 2D DCT via [Core.dct].
     * 6. Extract the top-left [dctSubSize]×[dctSubSize] sub-matrix (256 coefficients).
     * 7. Compute the median of those 256 values.
     * 8. Threshold each value against the median and pack into a [LongArray] of size 4.
     *
     * **Bit-packing convention (MSB first):**
     * Bit `i` (0-indexed, left-to-right in the 256-element flat array) maps to
     * `longs[i / 64]` at bit position `(63 - i % 64)`. This matches Python's
     * `int.from_bytes(hash_bytes, 'big')` convention used when the hash database
     * was generated, ensuring Hamming distance comparisons against stored hashes
     * yield correct results.
     *
     * This function must **not** close [imageProxy]; that is the caller's responsibility.
     *
     * @param imageProxy    Current camera frame. Must remain open for the duration of the call.
     * @param sortedCorners Four corners of the detected card in TL, TR, BR, BL order.
     * @return 256-bit hash as a [LongArray] of size 4, or null if any step fails.
     */
    private fun computeHash(imageProxy: ImageProxy, sortedCorners: List<PointF>): LongArray? {
        return try {
            // Step 3: Warp perspective to 488×680
            val warpMat = warpCard(imageProxy, sortedCorners) ?: return null

            // Convert warped BGR/Gray to grayscale
            when (warpMat.channels()) {
                1 -> warpMat.copyTo(grayMat)
                3 -> Imgproc.cvtColor(warpMat, grayMat, Imgproc.COLOR_BGR2GRAY)
                4 -> Imgproc.cvtColor(warpMat, grayMat, Imgproc.COLOR_BGRA2GRAY)
                else -> return null
            }

            // Step 4: Crop art zone (rows 8%–53%, cols 7%–93%)
            val rows = grayMat.rows()
            val cols = grayMat.cols()
            val rowStart = (rows * artRowStart).toInt()
            val rowEnd = (rows * artRowEnd).toInt()
            val colStart = (cols * artColStart).toInt()
            val colEnd = (cols * artColEnd).toInt()

            if (rowEnd <= rowStart || colEnd <= colStart) return null

            val artRoi = org.opencv.core.Rect(colStart, rowStart, colEnd - colStart, rowEnd - rowStart)
            grayMat.submat(artRoi).copyTo(croppedMat)

            // Step 5: Resize to 64×64 and convert to float32
            Imgproc.resize(croppedMat, resizedMat, Size(dctSize.toDouble(), dctSize.toDouble()))
            resizedMat.convertTo(floatMat, CvType.CV_32F)

            // Steps 6–8: DCT → sub-matrix → median threshold → bit packing
            computePHashFromMat(floatMat)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Computes a 256-bit perceptual hash from a grayscale float32 [Mat] at 64×64 px.
     *
     * Steps:
     * 1. Apply 2D DCT to [grayFloat32Mat].
     * 2. Extract the top-left 16×16 sub-matrix (256 DCT coefficients).
     * 3. Compute the median of those 256 values.
     * 4. Threshold: bit = 1 when coefficient > median, 0 otherwise.
     * 5. Pack into 4 longs, MSB first — matches Python `int.to_bytes(32, 'big')`.
     *
     * Exposed as `internal` so unit tests in the same Gradle module can call it directly
     * without requiring a real camera frame or warp perspective step.
     *
     * @param grayFloat32Mat A 64×64 [CvType.CV_32F] single-channel [Mat].
     * @return 256-bit hash packed as [LongArray] of size 4, or null on failure.
     */
    @VisibleForTesting
    internal fun computePHashFromMat(grayFloat32Mat: Mat): LongArray? {
        return try {
            // Apply 2D DCT
            Core.dct(grayFloat32Mat, dctMat)

            // Extract top-left 16×16 sub-matrix (256 coefficients)
            val subRoi = org.opencv.core.Rect(0, 0, dctSubSize, dctSubSize)
            val subMat = dctMat.submat(subRoi)

            val values = FloatArray(dctSubSize * dctSubSize)
            subMat.get(0, 0, values)
            subMat.release()

            // Compute median
            val sorted = values.clone().also { it.sort() }
            val median = if (sorted.size % 2 == 0) {
                (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2f
            } else {
                sorted[sorted.size / 2]
            }

            // Threshold and pack into 4 longs (MSB first — matches Python int.to_bytes(32,'big'))
            val longs = LongArray(4)
            for (i in values.indices) {
                if (values[i] > median) {
                    // Bit i maps to longs[i/64] bit (63 - i%64) — MSB first
                    longs[i / 64] = longs[i / 64] or (1L shl (63 - i % 64))
                }
            }

            longs
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Applies perspective warp to the camera frame using the detected card corners.
     *
     * Source: the 4 sorted corners in frame pixel space (TL, TR, BR, BL).
     * Destination: canonical rectangle [0,0]–[488,680].
     *
     * The Y-plane (grayscale) from YUV_420_888 is used directly to avoid
     * colour conversion overhead.
     *
     * @return Warped [Mat] in 8UC1, or null if the frame planes are unavailable.
     */
    private fun warpCard(imageProxy: ImageProxy, sortedCorners: List<PointF>): Mat? {
        val width = imageProxy.width
        val height = imageProxy.height

        // Extract Y-plane as grayscale source
        val yPlane: ImageProxy.PlaneProxy = imageProxy.planes[0]
        val yBuffer: ByteBuffer = yPlane.buffer
        val rowStride = yPlane.rowStride
        val pixelStride = yPlane.pixelStride

        val srcMat = Mat(height, width, CvType.CV_8UC1)
        if (rowStride == width && pixelStride == 1) {
            val bytes = ByteArray(yBuffer.remaining())
            yBuffer.get(bytes)
            srcMat.put(0, 0, bytes)
        } else {
            val rowBytes = ByteArray(width)
            for (row in 0 until height) {
                val rowStart = row * rowStride
                for (col in 0 until width) {
                    rowBytes[col] = yBuffer.get(rowStart + col * pixelStride)
                }
                srcMat.put(row, 0, rowBytes)
            }
        }

        // Source corners: TL, TR, BR, BL
        srcPoints.fromArray(
            Point(sortedCorners[0].x.toDouble(), sortedCorners[0].y.toDouble()),
            Point(sortedCorners[1].x.toDouble(), sortedCorners[1].y.toDouble()),
            Point(sortedCorners[2].x.toDouble(), sortedCorners[2].y.toDouble()),
            Point(sortedCorners[3].x.toDouble(), sortedCorners[3].y.toDouble()),
        )

        // Destination corners: canonical card rectangle
        dstPoints.fromArray(
            Point(0.0, 0.0),
            Point(warpWidth, 0.0),
            Point(warpWidth, warpHeight),
            Point(0.0, warpHeight),
        )

        val transformMat = Imgproc.getPerspectiveTransform(srcPoints, dstPoints)
        Imgproc.warpPerspective(
            srcMat,
            warpedMat,
            transformMat,
            Size(warpWidth, warpHeight),
        )
        srcMat.release()
        transformMat.release()

        return warpedMat
    }

    /**
     * Sorts the four detected corners into (top-left, top-right, bottom-right, bottom-left) order.
     *
     * - Top-left:     minimum (x + y)
     * - Bottom-right: maximum (x + y)
     * - Top-right:    maximum (x − y)
     * - Bottom-left:  minimum (x − y)
     */
    private fun sortCorners(corners: List<PointF>): List<PointF> {
        val topLeft = corners.minByOrNull { it.x + it.y }!!
        val bottomRight = corners.maxByOrNull { it.x + it.y }!!
        val topRight = corners.maxByOrNull { it.x - it.y }!!
        val bottomLeft = corners.minByOrNull { it.x - it.y }!!
        return listOf(topLeft, topRight, bottomRight, bottomLeft)
    }

    /**
     * Releases all native OpenCV [Mat] resources.
     * Must be called when the recognizer is no longer needed (e.g., when the camera unbinds).
     */
    fun release() {
        cardDetector.release()
        warpedMat.release()
        grayMat.release()
        floatMat.release()
        dctMat.release()
        croppedMat.release()
        resizedMat.release()
        srcPoints.release()
        dstPoints.release()
    }
}
