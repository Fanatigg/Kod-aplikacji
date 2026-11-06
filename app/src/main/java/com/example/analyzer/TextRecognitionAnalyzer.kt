package com.example.analyzer

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.model.DetectedAmount
import com.example.util.AmountParser
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class TextRecognitionAnalyzer(
    private val onAmountsDetected: (List<DetectedAmount>, Int, Int) -> Unit
) : ImageAnalysis.Analyzer {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    private var lastAnalysisTimestamp = 0L
    private val analysisThrottleMs = 250L
    @Volatile
    private var isBusy = false

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val currentTimestamp = System.currentTimeMillis()
        if (isBusy || (currentTimestamp - lastAnalysisTimestamp) < analysisThrottleMs) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        isBusy = true
        lastAnalysisTimestamp = currentTimestamp

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

        // Rotation can invert width and height
        val isRotated = rotationDegrees == 90 || rotationDegrees == 270
        val imgWidth = if (isRotated) imageProxy.height else imageProxy.width
        val imgHeight = if (isRotated) imageProxy.width else imageProxy.height

        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                val detected = extractAmountsFromVisionText(visionText, imgWidth, imgHeight)
                onAmountsDetected(detected, imgWidth, imgHeight)
            }
            .addOnFailureListener {
                // Ignore or log error
            }
            .addOnCompleteListener {
                isBusy = false
                imageProxy.close()
            }
    }

    /**
     * Analyzes a static Bitmap (for frozen frame or demo documents or gallery).
     */
    fun analyzeBitmap(bitmap: Bitmap, onResult: (List<DetectedAmount>) -> Unit) {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                val detected = extractAmountsFromVisionText(visionText, bitmap.width, bitmap.height)
                onResult(detected)
            }
            .addOnFailureListener {
                onResult(emptyList())
            }
    }

    private fun extractAmountsFromVisionText(
        visionText: Text,
        imageWidth: Int,
        imageHeight: Int
    ): List<DetectedAmount> {
        val allAmounts = mutableListOf<DetectedAmount>()

        for (block in visionText.textBlocks) {
            var pendingLabel: String? = null

            for (line in block.lines) {
                val lineText = line.text
                val lineBox: Rect? = line.boundingBox

                // Check if this line is purely a label (e.g. "Składka emerytalna")
                val foundLabel = AmountParser.findContextLabel(lineText)
                if (foundLabel != null && !lineText.any { it.isDigit() }) {
                    pendingLabel = foundLabel
                    continue
                }

                val effectiveLabel = foundLabel ?: pendingLabel
                val lineAmounts = AmountParser.extractAmountsFromLine(
                    line = line,
                    imageWidth = imageWidth,
                    imageHeight = imageHeight,
                    contextLabel = effectiveLabel
                )

                if (lineAmounts.isNotEmpty()) {
                    allAmounts.addAll(lineAmounts)
                    pendingLabel = null // Reset after consumption
                }
            }
        }

        // Deduplicate amounts only if their bounding boxes physically overlap heavily
        // (distinct table rows with identical amounts must NEVER be dropped)
        return deduplicateAmounts(allAmounts)
    }

    private fun deduplicateAmounts(list: List<DetectedAmount>): List<DetectedAmount> {
        val unique = mutableListOf<DetectedAmount>()
        for (item in list) {
            val duplicate = unique.find { existing ->
                val r1 = existing.normalizedRect
                val r2 = item.normalizedRect
                if (r1 != null && r2 != null) {
                    val overlapLeft = maxOf(r1.left, r2.left)
                    val overlapRight = minOf(r1.right, r2.right)
                    val overlapTop = maxOf(r1.top, r2.top)
                    val overlapBottom = minOf(r1.bottom, r2.bottom)

                    if (overlapRight > overlapLeft && overlapBottom > overlapTop) {
                        val overlapArea = (overlapRight - overlapLeft) * (overlapBottom - overlapTop)
                        val area1 = r1.width() * r1.height()
                        val area2 = r2.width() * r2.height()
                        val minArea = minOf(area1, area2)
                        // Only consider duplicate if overlap is > 60% of smaller box
                        minArea > 0f && (overlapArea / minArea) > 0.6f
                    } else {
                        false
                    }
                } else {
                    false
                }
            }
            if (duplicate == null) {
                unique.add(item)
            }
        }
        return unique
    }

    fun close() {
        recognizer.close()
    }
}
