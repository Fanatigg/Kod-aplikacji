package com.example.viewmodel

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.analyzer.TextRecognitionAnalyzer
import com.example.model.DetectedAmount
import com.example.model.SavedCalculation
import com.example.util.SampleDocumentType
import com.example.util.SampleDocuments
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DocumentSource {
    LIVE_CAMERA,
    FROZEN_FRAME,
    SAMPLE_DOCUMENT,
    GALLERY_PHOTO
}

data class ScannerUiState(
    val isCameraPermissionGranted: Boolean = false,
    val source: DocumentSource = DocumentSource.LIVE_CAMERA,
    val frozenBitmap: Bitmap? = null,
    val detectedAmounts: List<DetectedAmount> = emptyList(),
    val totalSum: Double = 0.0,
    val isTorchOn: Boolean = false,
    val currentSampleType: SampleDocumentType? = null,
    val savedHistory: List<SavedCalculation> = emptyList(),
    val showManualAddDialog: Boolean = false,
    val editingAmount: DetectedAmount? = null,
    val showSaveDialog: Boolean = false,
    val showHistoryDialog: Boolean = false,
    val showSamplePicker: Boolean = false,
    val isPercentageEnabled: Boolean = true, // Always active for ZUS Zdrowotna
    val selectedPercentage: Double = 9.0,   // Standard 9% Zdrowotna
    val percentageAmount: Double = 0.0,
    val isTargetFrameEnabled: Boolean = false, // Target column frame
    val targetFrameRect: RectF = RectF(0.20f, 0.12f, 0.82f, 0.85f),
    val toastMessage: String? = null
)

class ScannerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private var viewportWidth: Float = 0f
    private var viewportHeight: Float = 0f

    // Internal tracked item for temporal stabilization & jitter elimination
    private data class TrackedItem(
        val amount: DetectedAmount,
        val missedFrames: Int = 0
    )

    private val trackedLiveItems = mutableListOf<TrackedItem>()

    fun setViewportSize(width: Float, height: Float) {
        if (width > 0 && height > 0) {
            viewportWidth = width
            viewportHeight = height
        }
    }

    val analyzer = TextRecognitionAnalyzer { amounts, imgW, imgH ->
        onLiveAmountsDetected(amounts, imgW, imgH)
    }

    fun onPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(isCameraPermissionGranted = granted) }
    }

    private fun ScannerUiState.withUpdatedAmounts(newAmounts: List<DetectedAmount>): ScannerUiState {
        val sum = newAmounts.filter { it.isSelected }.sumOf { it.amountValue }
        return copy(
            detectedAmounts = newAmounts,
            totalSum = sum,
            percentageAmount = (sum * selectedPercentage) / 100.0
        )
    }

    private fun onLiveAmountsDetected(newAmounts: List<DetectedAmount>, imgWidth: Int, imgHeight: Int) {
        if (_uiState.value.source != DocumentSource.LIVE_CAMERA) return

        val viewW = viewportWidth
        val viewH = viewportHeight

        // Map camera normalized coordinates to visible viewport coordinates using FILL_CENTER
        val visibleAmounts = if (viewW > 0 && viewH > 0 && imgWidth > 0 && imgHeight > 0) {
            val scale = maxOf(viewW / imgWidth.toFloat(), viewH / imgHeight.toFloat())
            val scaledW = imgWidth * scale
            val scaledH = imgHeight * scale
            val offsetX = (viewW - scaledW) / 2f
            val offsetY = (viewH - scaledH) / 2f

            newAmounts.mapNotNull { item ->
                val r = item.normalizedRect ?: return@mapNotNull item
                val leftPx = r.left * scaledW + offsetX
                val rightPx = r.right * scaledW + offsetX
                val topPx = r.top * scaledH + offsetY
                val bottomPx = r.bottom * scaledH + offsetY

                val centerX = (leftPx + rightPx) / 2f
                val centerY = (topPx + bottomPx) / 2f

                // Discard numbers outside visible camera viewfinder!
                if (centerX < 0 || centerX > viewW || centerY < 0 || centerY > viewH) {
                    return@mapNotNull null
                }

                // If target column frame is enabled, discard numbers outside the column
                if (_uiState.value.isTargetFrameEnabled) {
                    val frame = _uiState.value.targetFrameRect
                    val normCenterX = centerX / viewW
                    val normCenterY = centerY / viewH
                    if (!frame.contains(normCenterX, normCenterY)) {
                        return@mapNotNull null
                    }
                }

                val viewportNormRect = RectF(
                    (leftPx / viewW).coerceIn(0f, 1f),
                    (topPx / viewH).coerceIn(0f, 1f),
                    (rightPx / viewW).coerceIn(0f, 1f),
                    (bottomPx / viewH).coerceIn(0f, 1f)
                )

                item.copy(normalizedRect = viewportNormRect)
            }
        } else {
            newAmounts
        }

        // Apply temporal stabilization & EMA smoothing across frames to eliminate jitter
        val updatedTracked = mutableListOf<TrackedItem>()
        val matchedExistingIndices = mutableSetOf<Int>()

        for (incoming in visibleAmounts) {
            val incRect = incoming.normalizedRect
            var bestIdx = -1
            var bestDist = Float.MAX_VALUE

            for (i in trackedLiveItems.indices) {
                if (i in matchedExistingIndices) continue
                val existing = trackedLiveItems[i].amount
                // Must have the exact same or very close amount value
                if (kotlin.math.abs(existing.amountValue - incoming.amountValue) < 0.01) {
                    val exRect = existing.normalizedRect
                    if (incRect != null && exRect != null) {
                        val dx = incRect.centerX() - exRect.centerX()
                        val dy = incRect.centerY() - exRect.centerY()
                        val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                        // Within proximity threshold
                        if (dist < 0.08f && dist < bestDist) {
                            bestDist = dist
                            bestIdx = i
                        }
                    }
                }
            }

            if (bestIdx != -1) {
                matchedExistingIndices.add(bestIdx)
                val existing = trackedLiveItems[bestIdx].amount
                val exRect = existing.normalizedRect
                val smoothedRect = if (exRect != null && incRect != null) {
                    // Exponential moving average: 65% existing + 35% incoming
                    RectF(
                        exRect.left * 0.65f + incRect.left * 0.35f,
                        exRect.top * 0.65f + incRect.top * 0.35f,
                        exRect.right * 0.65f + incRect.right * 0.35f,
                        exRect.bottom * 0.65f + incRect.bottom * 0.35f
                    )
                } else incRect

                val smoothedItem = incoming.copy(
                    id = existing.id, // STABLE ID preserves Compose state and prevents jitter!
                    isSelected = existing.isSelected,
                    normalizedRect = smoothedRect
                )
                updatedTracked.add(TrackedItem(smoothedItem, missedFrames = 0))
            } else {
                // New detected amount
                updatedTracked.add(TrackedItem(incoming, missedFrames = 0))
            }
        }

        // Handle existing items that were not seen in this single frame (give 1 frame grace period)
        for (i in trackedLiveItems.indices) {
            if (i !in matchedExistingIndices) {
                val missed = trackedLiveItems[i].missedFrames + 1
                if (missed < 2) {
                    updatedTracked.add(trackedLiveItems[i].copy(missedFrames = missed))
                }
            }
        }

        trackedLiveItems.clear()
        trackedLiveItems.addAll(updatedTracked)

        val finalAmounts = trackedLiveItems.map { it.amount }

        _uiState.update { current ->
            // Also keep any custom user-added amounts
            val customAmounts = current.detectedAmounts.filter { it.isCustom }
            val combined = (finalAmounts + customAmounts).distinctBy { it.id }
            current.withUpdatedAmounts(combined)
        }
    }

    /**
     * Freezes current camera preview or image.
     */
    fun freezeFrame(bitmap: Bitmap?) {
        if (bitmap == null) return
        trackedLiveItems.clear()

        _uiState.update {
            it.copy(
                source = DocumentSource.FROZEN_FRAME,
                frozenBitmap = bitmap,
                isTorchOn = false
            )
        }

        // Run thorough OCR on the frozen high-resolution bitmap
        analyzer.analyzeBitmap(bitmap) { detected ->
            _uiState.update { current ->
                val customAmounts = current.detectedAmounts.filter { it.isCustom }
                val combined = (detected + customAmounts).distinctBy { it.id }
                current.withUpdatedAmounts(combined)
            }
        }
    }

    /**
     * Resumes live camera scanning.
     */
    fun resumeLiveScanning() {
        trackedLiveItems.clear()
        _uiState.update {
            it.copy(
                source = DocumentSource.LIVE_CAMERA,
                frozenBitmap = null,
                currentSampleType = null
            )
        }
    }

    /**
     * Toggles inclusion of a detected amount in the total sum.
     */
    fun toggleAmountSelection(id: String) {
        _uiState.update { current ->
            val updated = current.detectedAmounts.map { item ->
                if (item.id == id) item.copy(isSelected = !item.isSelected) else item
            }
            current.withUpdatedAmounts(updated)
        }
    }

    fun selectAll() {
        _uiState.update { current ->
            val updated = current.detectedAmounts.map { it.copy(isSelected = true) }
            current.withUpdatedAmounts(updated)
        }
    }

    fun deselectAll() {
        _uiState.update { current ->
            val updated = current.detectedAmounts.map { it.copy(isSelected = false) }
            current.withUpdatedAmounts(updated)
        }
    }

    /**
     * Selects ONLY amounts that fall inside a touch drag rectangle (deselecting all others).
     */
    fun selectAmountsInRect(rect: RectF) {
        _uiState.update { current ->
            val updated = current.detectedAmounts.map { item ->
                val normRect = item.normalizedRect
                val isInside = normRect != null && (
                    rect.contains(normRect.centerX(), normRect.centerY()) ||
                    RectF.intersects(rect, normRect)
                )
                item.copy(isSelected = isInside)
            }
            val countSelected = updated.count { it.isSelected }
            current.withUpdatedAmounts(updated).copy(
                toastMessage = "Zaznaczono $countSelected pozycji w wybranym obszarze"
            )
        }
    }

    fun toggleTargetFrame() {
        _uiState.update { current ->
            val nextState = !current.isTargetFrameEnabled
            current.copy(
                isTargetFrameEnabled = nextState,
                toastMessage = if (nextState) "Włączono celownik kolumny" else "Wyłączono celownik kolumny"
            )
        }
    }

    /**
     * Loads a built-in realistic test document (e.g. ZUS DRA).
     */
    fun loadSampleDocument(type: SampleDocumentType) {
        viewModelScope.launch {
            val bitmap = SampleDocuments.generateSampleBitmap(type)
            _uiState.update {
                it.copy(
                    source = DocumentSource.SAMPLE_DOCUMENT,
                    currentSampleType = type,
                    frozenBitmap = bitmap,
                    detectedAmounts = emptyList(),
                    totalSum = 0.0,
                    percentageAmount = 0.0
                )
            }

            analyzer.analyzeBitmap(bitmap) { detected ->
                _uiState.update { current ->
                    current.withUpdatedAmounts(detected).copy(
                        toastMessage = "Załadowano dokument testowy ZUS (${detected.size} kwot)"
                    )
                }
            }
        }
    }

    /**
     * Loads an image from user's gallery / photo picker.
     */
    fun loadGalleryBitmap(bitmap: Bitmap) {
        _uiState.update {
            it.copy(
                source = DocumentSource.GALLERY_PHOTO,
                currentSampleType = null,
                frozenBitmap = bitmap,
                detectedAmounts = emptyList(),
                totalSum = 0.0,
                percentageAmount = 0.0
            )
        }

        analyzer.analyzeBitmap(bitmap) { detected ->
            _uiState.update { current ->
                current.withUpdatedAmounts(detected).copy(
                    toastMessage = "Zeskanowano zdjęcie z galerii (${detected.size} kwot)"
                )
            }
        }
    }

    fun toggleTorch() {
        _uiState.update { it.copy(isTorchOn = !it.isTorchOn) }
    }

    fun addManualAmount(value: Double, label: String) {
        val newAmount = DetectedAmount(
            rawText = DetectedAmount.formatAmount(value),
            amountValue = value,
            label = label,
            isCustom = true,
            isSelected = true
        )
        _uiState.update { current ->
            val updated = current.detectedAmounts + newAmount
            current.withUpdatedAmounts(updated).copy(showManualAddDialog = false)
        }
    }

    fun editAmount(id: String, newValue: Double, newLabel: String) {
        _uiState.update { current ->
            val updated = current.detectedAmounts.map { item ->
                if (item.id == id) {
                    item.copy(
                        amountValue = newValue,
                        formattedValue = DetectedAmount.formatAmount(newValue),
                        label = newLabel
                    )
                } else item
            }
            current.withUpdatedAmounts(updated).copy(editingAmount = null)
        }
    }

    fun deleteAmount(id: String) {
        _uiState.update { current ->
            val updated = current.detectedAmounts.filterNot { it.id == id }
            current.withUpdatedAmounts(updated)
        }
    }

    fun saveCurrentCalculation(title: String) {
        val current = _uiState.value
        val calculation = SavedCalculation(
            title = title,
            totalAmount = current.totalSum,
            items = current.detectedAmounts.filter { it.isSelected }
        )
        _uiState.update {
            it.copy(
                savedHistory = listOf(calculation) + it.savedHistory,
                showSaveDialog = false,
                toastMessage = "Zapisano do historii: ${DetectedAmount.formatAmount(current.totalSum)}"
            )
        }
    }

    fun deleteHistoryItem(id: String) {
        _uiState.update { it.copy(savedHistory = it.savedHistory.filterNot { item -> item.id == id }) }
    }

    fun restoreCalculation(calculation: SavedCalculation) {
        _uiState.update {
            it.copy(
                detectedAmounts = calculation.items,
                totalSum = calculation.totalAmount,
                showHistoryDialog = false,
                toastMessage = "Wczytano: ${calculation.title}"
            )
        }
    }

    fun generateClipboardSummary(): String {
        val current = _uiState.value
        val selected = current.detectedAmounts.filter { it.isSelected }
        val sb = StringBuilder()
        sb.appendLine("Podsumowanie składek / kwot:")
        selected.forEachIndexed { index, item ->
            val label = item.label ?: "Pozycja ${index + 1}"
            sb.appendLine("• $label: ${item.formattedValue}")
        }
        sb.appendLine("---------------------------")
        sb.appendLine("RAZEM: ${DetectedAmount.formatAmount(current.totalSum)}")
        if (current.isPercentageEnabled) {
            sb.appendLine("Wyliczone ${current.selectedPercentage}%: ${DetectedAmount.formatAmount(current.percentageAmount)}")
        }
        return sb.toString()
    }

    fun togglePercentageCalculator() {
        _uiState.update {
            val enabled = !it.isPercentageEnabled
            it.copy(
                isPercentageEnabled = enabled,
                percentageAmount = (it.totalSum * it.selectedPercentage) / 100.0
            )
        }
    }

    fun setSelectedPercentage(rate: Double) {
        _uiState.update {
            it.copy(
                selectedPercentage = rate,
                percentageAmount = (it.totalSum * rate) / 100.0
            )
        }
    }

    fun setManualAddDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(showManualAddDialog = visible) }
    }

    fun setEditingAmount(amount: DetectedAmount?) {
        _uiState.update { it.copy(editingAmount = amount) }
    }

    fun setSaveDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(showSaveDialog = visible) }
    }

    fun setHistoryDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(showHistoryDialog = visible) }
    }

    fun setSamplePickerVisible(visible: Boolean) {
        _uiState.update { it.copy(showSamplePicker = visible) }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        analyzer.close()
    }
}
