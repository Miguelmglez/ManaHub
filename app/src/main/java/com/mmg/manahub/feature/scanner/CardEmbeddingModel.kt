package com.mmg.manahub.feature.scanner

// COMMENTED OUT — replaced by ML Kit OCR. See CardRecognizer for the new pipeline.
/*
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.opencv.core.Mat
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * TFLite wrapper for MobileNetV3-Small feature extraction.
 *
 * Loads `mobilenet_v3_small.tflite` from assets and runs inference to produce a
 * 576-dimensional L2-normalised feature vector from a 224×224 BGR [Mat].
 *
 * **Input contract:**
 * - An OpenCV [Mat] in BGR colour space (CV_8UC3), 224×224 pixels.
 * - Internally converted BGR→RGB, then normalised to [0, 1] float32.
 * - Laid out as NHWC: [batch=1, height=224, width=224, channels=3].
 *
 * **Output contract:**
 * - A [FloatArray] of size 1024 (global average pool output, no classifier head).
 * - L2-normalised so that `dot(a, b) == cosine_similarity(a, b)` at query time.
 *
 * Buffers are allocated once in the constructor and reused across calls to avoid
 * per-frame GC pressure. The [Interpreter] is released in [close].
 *
 * @param context Application context used to open the `.tflite` asset.
 * @throws IllegalStateException if `mobilenet_v3_small.tflite` is not present in assets.
 */
@Singleton
class CardEmbeddingModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : AutoCloseable {

    companion object {
        private const val MODEL_FILE = "mobilenet_v3_small.tflite"
        private const val INPUT_SIZE = 224
        private const val OUTPUT_DIMS = 1024
        private const val TAG = "CardEmbeddingModel"
    }

    private val interpreter: Interpreter

    // Reusable input buffer: 1 × 224 × 224 × 3 × 4 bytes
    private val inputBuffer: ByteBuffer = ByteBuffer
        .allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4)
        .also { it.order(ByteOrder.nativeOrder()) }

    // Reusable output buffer: 1 × 576 × 4 bytes
    private val outputBuffer: ByteBuffer = ByteBuffer
        .allocateDirect(1 * OUTPUT_DIMS * 4)
        .also { it.order(ByteOrder.nativeOrder()) }

    init {
        val modelBuffer = loadModelBuffer()
        interpreter = Interpreter(modelBuffer)
        android.util.Log.i(TAG, "TFLite interpreter loaded — model: $MODEL_FILE, outputDims: $OUTPUT_DIMS")
    }

    /**
     * Runs MobileNetV3-Small inference on [bgrMat] and returns an L2-normalised embedding.
     *
     * @param bgrMat A 224×224 CV_8UC3 BGR [Mat] produced by [CardRecognizer.warpCard].
     * @return L2-normalised [FloatArray] of size 1024, or null if inference fails.
     */
    fun extract(bgrMat: Mat): FloatArray? {
        return try {
            fillInputBuffer(bgrMat)
            inputBuffer.rewind()
            outputBuffer.rewind()

            interpreter.run(inputBuffer, outputBuffer)

            outputBuffer.rewind()
            val rawOutput = FloatArray(OUTPUT_DIMS) { outputBuffer.float }
            l2Normalize(rawOutput)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "extract() failed", e)
            null
        }
    }

    override fun close() {
        try {
            interpreter.close()
            android.util.Log.d(TAG, "TFLite interpreter released")
        } catch (e: Exception) {
            android.util.Log.w(TAG, "close() failed", e)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Loads the TFLite model from assets into a direct [ByteBuffer].
     *
     * @throws IllegalStateException if the model file is absent in assets.
     */
    private fun loadModelBuffer(): ByteBuffer {
        val assetFd = try {
            context.assets.openFd(MODEL_FILE)
        } catch (e: Exception) {
            throw IllegalStateException(
                "$MODEL_FILE not found in assets. Download it from TF Hub: " +
                    "https://tfhub.dev/google/lite-model/imagenet/mobilenet_v3_small_100_224/feature_vector/5/default/1",
                e,
            )
        }
        val size = assetFd.declaredLength
        val buffer = ByteBuffer.allocateDirect(size.toInt()).order(ByteOrder.nativeOrder())
        assetFd.createInputStream().use { it.channel.read(buffer) }
        buffer.rewind()
        return buffer
    }

    /**
     * Fills [inputBuffer] from [bgrMat] with the preprocessing pipeline expected by the model:
     * 1. Convert BGR → RGB (swap R and B channels).
     * 2. Normalise each channel byte to [0, 1] float32.
     * 3. Write in NHWC order: row-major, RGB interleaved.
     */
    private fun fillInputBuffer(bgrMat: Mat) {
        inputBuffer.rewind()
        val rowBytes = ByteArray(INPUT_SIZE * 3)
        for (row in 0 until INPUT_SIZE) {
            bgrMat.get(row, 0, rowBytes)
            for (col in 0 until INPUT_SIZE) {
                val base = col * 3
                // BGR → RGB: swap index 0 (B) and 2 (R)
                val r = (rowBytes[base + 2].toInt() and 0xFF) / 255.0f
                val g = (rowBytes[base + 1].toInt() and 0xFF) / 255.0f
                val b = (rowBytes[base + 0].toInt() and 0xFF) / 255.0f
                inputBuffer.putFloat(r)
                inputBuffer.putFloat(g)
                inputBuffer.putFloat(b)
            }
        }
    }

    /**
     * Divides each element of [vector] by its L2 norm in-place and returns it.
     * If the norm is zero (all-zero vector), the vector is returned unchanged.
     */
    private fun l2Normalize(vector: FloatArray): FloatArray {
        var sumOfSquares = 0.0f
        for (v in vector) sumOfSquares += v * v
        val norm = sqrt(sumOfSquares)
        if (norm > 0f) {
            for (i in vector.indices) vector[i] /= norm
        }
        return vector
    }
}
*/
