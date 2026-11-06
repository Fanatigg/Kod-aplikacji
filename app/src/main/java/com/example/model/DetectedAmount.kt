package com.example.model

import android.graphics.RectF
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import java.util.UUID

/**
 * Represents a recognized monetary amount from camera OCR or manual entry.
 *
 * @property id Unique identifier for diffing and toggling selection.
 * @property rawText Raw OCR string that matched (e.g. "1 018,48 zł").
 * @property amountValue Parsed numeric value (e.g. 1018.48).
 * @property formattedValue Formatted Polish monetary string (e.g. "1 018,48 zł").
 * @property normalizedRect Relative bounding box (0f..1f) in image coordinate space.
 * @property label Context label if detected nearby (e.g. "Składka emerytalna", "Ubezp. zdrowotne").
 * @property isSelected Whether this amount is included in the live running total.
 * @property isCustom True if manually added by the user.
 */
data class DetectedAmount(
    val id: String = UUID.randomUUID().toString(),
    val rawText: String,
    val amountValue: Double,
    val formattedValue: String = formatAmount(amountValue),
    val normalizedRect: RectF? = null,
    val label: String? = null,
    val isSelected: Boolean = true,
    val isCustom: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        private val plSymbols = DecimalFormatSymbols(Locale.forLanguageTag("pl-PL")).apply {
            groupingSeparator = ' '
            decimalSeparator = ','
        }
        private val formatter = DecimalFormat("#,##0.00 zł", plSymbols)

        fun formatAmount(value: Double): String {
            return formatter.format(value)
        }
    }
}

/**
 * Saved calculation for user history.
 */
data class SavedCalculation(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val totalAmount: Double,
    val items: List<DetectedAmount>,
    val timestamp: Long = System.currentTimeMillis()
)
