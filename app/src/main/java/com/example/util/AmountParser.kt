package com.example.util

import android.graphics.Rect
import android.graphics.RectF
import com.example.model.DetectedAmount
import java.util.regex.Pattern

object AmountParser {

    // Regex for matching Polish monetary amounts:
    // Handles:
    // 1) 1 018,48 zł or 1 018,48 PLN or 1018,48 or 1.018,48
    // 2) 750,5 zł or 750,5 złotych or 750,5
    // 3) 500 zł / 500 PLN / 500 złotych / 500,00
    // 4) 1 234.56 or 1234.56
    private val AMOUNT_PATTERN = Pattern.compile(
        """(?i)(?<![\w.,-])(\d{1,3}(?:[ \t.]\d{3})*(?:[,\.]\d{1,2})(?:\s*(?:zł|zl|pln|złotych|zlotych))?|\d+(?:[,\.]\d{1,2})(?:\s*(?:zł|zl|pln|złotych|zlotych))?|\d{1,6}(?:\s*(?:zł|zl|pln|złotych|zlotych)))(?![\w.,-])"""
    )

    // Patterns to exclude (dates, phone numbers, years, percentages, accounts)
    private val DATE_PATTERN = Pattern.compile("""\b(?:\d{2}[./-]\d{2}[./-]\d{4}|\d{4}[./-]\d{2}[./-]\d{2}|\d{2}[./-]\d{4})\b""")
    private val PERCENT_PATTERN = Pattern.compile("""\d+(?:[,\.]\d+)?\s*%""")
    private val YEAR_ONLY_PATTERN = Pattern.compile("""\b(?:19\d\d|20[2-3]\d)\b""")
    private val LONG_NUMBER_PATTERN = Pattern.compile("""\b\d{7,}\b""")

    /**
     * Extracts amounts from an ML Kit Text.Line using individual word elements
     * to pinpoint the exact bounding box of each amount (preventing multi-column merging).
     */
    fun extractAmountsFromLine(
        line: com.google.mlkit.vision.text.Text.Line,
        imageWidth: Int,
        imageHeight: Int,
        contextLabel: String? = null
    ): List<DetectedAmount> {
        val trimmed = line.text.trim()
        if (trimmed.isEmpty()) return emptyList()

        if (DATE_PATTERN.matcher(trimmed).matches() || PERCENT_PATTERN.matcher(trimmed).matches()) {
            return emptyList()
        }

        val results = mutableListOf<DetectedAmount>()
        val matcher = AMOUNT_PATTERN.matcher(trimmed)
        val inferredLabel = contextLabel ?: findContextLabel(trimmed)

        val elements = line.elements

        while (matcher.find()) {
            val matchedGroup = matcher.group(1)?.trim() ?: continue
            val startIdx = matcher.start(1)
            val endIdx = matcher.end(1)

            val lowerMatched = matchedGroup.lowercase()
            val hasCurrency = lowerMatched.contains("zł") || lowerMatched.contains("zl") ||
                    lowerMatched.contains("pln") || lowerMatched.contains("zlot")

            if (!hasCurrency && YEAR_ONLY_PATTERN.matcher(matchedGroup).matches()) {
                continue
            }

            val digitsOnly = matchedGroup.filter { it.isDigit() }
            if (digitsOnly.length > 8 && !matchedGroup.contains(",") && !matchedGroup.contains(".")) {
                continue
            }

            val parsedValue = parseAmountValue(matchedGroup) ?: continue
            if (parsedValue <= 0.009 || parsedValue > 100_000_000.0) {
                continue
            }

            val hasTwoDecimals = matchedGroup.contains(Regex("""[,\.]\d{2}(?!\d)"""))
            // If it has only 1 decimal place (e.g. 750,5) or integer without currency,
            // require parsedValue >= 10.0 to prevent matching version numbers (e.g. 2.1) or bullet points (e.g. 1.5)
            if (!hasTwoDecimals && !hasCurrency) {
                if (parsedValue < 10.0) continue
            }

            // Find elements that overlap with this character range in trimmed line
            var preciseBox: Rect? = null
            var currentPos = 0

            for (element in elements) {
                val elemText = element.text
                val elemBox = element.boundingBox
                val elemLen = elemText.length

                val elemStart = trimmed.indexOf(elemText, currentPos).let { if (it >= 0) it else currentPos }
                val elemEnd = elemStart + elemLen
                currentPos = elemEnd

                // Check overlap
                if (elemEnd > startIdx && elemStart < endIdx && elemBox != null) {
                    if (preciseBox == null) {
                        preciseBox = Rect(elemBox)
                    } else {
                        preciseBox.union(elemBox)
                    }
                }
            }

            val effectiveBox = preciseBox ?: line.boundingBox

            val normRect = if (effectiveBox != null && imageWidth > 0 && imageHeight > 0) {
                RectF(
                    effectiveBox.left.toFloat() / imageWidth,
                    effectiveBox.top.toFloat() / imageHeight,
                    effectiveBox.right.toFloat() / imageWidth,
                    effectiveBox.bottom.toFloat() / imageHeight
                )
            } else null

            results.add(
                DetectedAmount(
                    rawText = matchedGroup,
                    amountValue = parsedValue,
                    normalizedRect = normRect,
                    label = inferredLabel
                )
            )
        }

        return results
    }

    /**
     * Extracts all valid monetary amounts from a given text line or block.
     */
    fun extractAmountsFromText(
        text: String,
        boundingBox: Rect?,
        imageWidth: Int,
        imageHeight: Int,
        contextLabel: String? = null
    ): List<DetectedAmount> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()

        // Quick check: if the entire text is a date, phone number or percentage, ignore
        if (DATE_PATTERN.matcher(trimmed).matches() || PERCENT_PATTERN.matcher(trimmed).matches()) {
            return emptyList()
        }

        val results = mutableListOf<DetectedAmount>()
        val matcher = AMOUNT_PATTERN.matcher(trimmed)

        // Try to identify context from the text itself if not provided
        val inferredLabel = contextLabel ?: findContextLabel(trimmed)

        while (matcher.find()) {
            val matchedGroup = matcher.group(1)?.trim() ?: continue

            val lowerMatched = matchedGroup.lowercase()
            val hasCurrency = lowerMatched.contains("zł") || lowerMatched.contains("zl") ||
                    lowerMatched.contains("pln") || lowerMatched.contains("zlot")

            if (!hasCurrency && YEAR_ONLY_PATTERN.matcher(matchedGroup).matches()) {
                continue
            }

            // Filter out long identification numbers (PESEL, NIP, bank accounts)
            val digitsOnly = matchedGroup.filter { it.isDigit() }
            if (digitsOnly.length > 8 && !matchedGroup.contains(",") && !matchedGroup.contains(".")) {
                continue
            }

            val parsedValue = parseAmountValue(matchedGroup) ?: continue
            // Exclude zero or negative or absurdly large values
            if (parsedValue <= 0.009 || parsedValue > 100_000_000.0) {
                continue
            }

            val hasTwoDecimals = matchedGroup.contains(Regex("""[,\.]\d{2}(?!\d)"""))
            // If it has only 1 decimal place (e.g. 750,5) or integer without currency,
            // require parsedValue >= 10.0 to prevent matching version numbers (e.g. 2.1) or bullet points (e.g. 1.5)
            if (!hasTwoDecimals && !hasCurrency) {
                if (parsedValue < 10.0) continue
            }

            // Compute normalized bounding box
            val normRect = if (boundingBox != null && imageWidth > 0 && imageHeight > 0) {
                RectF(
                    boundingBox.left.toFloat() / imageWidth,
                    boundingBox.top.toFloat() / imageHeight,
                    boundingBox.right.toFloat() / imageWidth,
                    boundingBox.bottom.toFloat() / imageHeight
                )
            } else null

            results.add(
                DetectedAmount(
                    rawText = matchedGroup,
                    amountValue = parsedValue,
                    normalizedRect = normRect,
                    label = inferredLabel
                )
            )
        }

        return results
    }

    /**
     * Parses numeric value from a string like "1 018,48 zł", "750,5 złotych", or "419.46".
     */
    fun parseAmountValue(amountStr: String): Double? {
        try {
            // Strip currency symbols and whitespace
            val clean = amountStr
                .replace("złotych", "", ignoreCase = true)
                .replace("zlotych", "", ignoreCase = true)
                .replace("złote", "", ignoreCase = true)
                .replace("zlote", "", ignoreCase = true)
                .replace("zł", "", ignoreCase = true)
                .replace("zl", "", ignoreCase = true)
                .replace("pln", "", ignoreCase = true)
                .replace("€", "")
                .replace("$", "")
                .replace(" ", "")
                .replace("\u00A0", "") // non-breaking space
                .trim()

            if (clean.isEmpty()) return null

            // Determine decimal separator
            // In Polish: 1.018,48 -> '.' is thousands, ',' is decimal
            // Or 1 018,48 -> ',' is decimal
            // Or 1018.48 -> '.' is decimal
            val normalized = if (clean.contains(",") && clean.contains(".")) {
                // Determine which comes last
                val lastComma = clean.lastIndexOf(',')
                val lastDot = clean.lastIndexOf('.')
                if (lastComma > lastDot) {
                    // e.g. 1.018,48
                    clean.replace(".", "").replace(',', '.')
                } else {
                    // e.g. 1,018.48
                    clean.replace(",", "")
                }
            } else if (clean.contains(",")) {
                clean.replace(',', '.')
            } else {
                clean
            }

            return normalized.toDoubleOrNull()
        } catch (_: Exception) {
            return null
        }
    }

    /**
     * Identifies known ZUS / invoice keywords to label the amount.
     */
    fun findContextLabel(text: String): String? {
        val lower = text.lowercase()
        return when {
            lower.contains("emerytaln") -> "Składka emerytalna"
            lower.contains("rentow") -> "Składka rentowa"
            lower.contains("chorobow") -> "Składka chorobowa"
            lower.contains("wypadkow") -> "Składka wypadkowa"
            lower.contains("zdrowotn") -> "Składka zdrowotna"
            lower.contains("fundusz pracy") || lower.contains(" fp") || lower.startsWith("fp ") -> "Fundusz Pracy"
            lower.contains("fgśp") || lower.contains("fgsp") -> "FGŚP"
            lower.contains("fep") -> "FEP"
            lower.contains("razem") || lower.contains("suma") -> "Razem"
            lower.contains("do zapłaty") || lower.contains("do zaplaty") -> "Do zapłaty"
            lower.contains("brutto") -> "Kwota brutto"
            lower.contains("netto") -> "Kwota netto"
            lower.contains("vat") -> "Podatek VAT"
            else -> null
        }
    }
}
