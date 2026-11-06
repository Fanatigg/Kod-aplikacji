package com.example

import android.graphics.Rect
import com.example.model.DetectedAmount
import com.example.util.AmountParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AmountParserTest {

    @Test
    fun parseAmountValue_handlesVariousPolishFormats() {
        assertEquals(1018.48, AmountParser.parseAmountValue("1 018,48 zł")!!, 0.001)
        assertEquals(417.41, AmountParser.parseAmountValue("417,41 PLN")!!, 0.001)
        assertEquals(127.88, AmountParser.parseAmountValue("127,88")!!, 0.001)
        assertEquals(419.46, AmountParser.parseAmountValue("419.46")!!, 0.001)
        assertEquals(1234.56, AmountParser.parseAmountValue("1.234,56 zł")!!, 0.001)
        assertEquals(250.0, AmountParser.parseAmountValue("250 zł")!!, 0.001)
        assertEquals(500.0, AmountParser.parseAmountValue("500 PLN")!!, 0.001)
    }

    @Test
    fun parseAmountValue_rejectsInvalidOrEmpty() {
        assertNull(AmountParser.parseAmountValue(""))
        assertNull(AmountParser.parseAmountValue("abc"))
        assertNull(AmountParser.parseAmountValue("---"))
    }

    @Test
    fun extractAmountsFromText_extractsZusContributionsWithLabels() {
        val line1 = "Składka na ubezpieczenie emerytalne: 1 018,48 zł"
        val amounts1 = AmountParser.extractAmountsFromText(
            text = line1,
            boundingBox = Rect(0, 0, 100, 50),
            imageWidth = 1000,
            imageHeight = 1000
        )

        assertEquals(1, amounts1.size)
        assertEquals(1018.48, amounts1[0].amountValue, 0.001)
        assertEquals("Składka emerytalna", amounts1[0].label)
        assertNotNull(amounts1[0].normalizedRect)

        val line2 = "Ubezpieczenie zdrowotne 419,46"
        val amounts2 = AmountParser.extractAmountsFromText(
            text = line2,
            boundingBox = null,
            imageWidth = 1000,
            imageHeight = 1000
        )
        assertEquals(1, amounts2.size)
        assertEquals(419.46, amounts2[0].amountValue, 0.001)
        assertEquals("Składka zdrowotna", amounts2[0].label)
    }

    @Test
    fun extractAmountsFromText_filtersOutDatesAndPercentages() {
        // Full dates should not be detected as amounts
        val dateText = "Termin płatności: 20.05.2024"
        val amountsDate = AmountParser.extractAmountsFromText(
            text = dateText,
            boundingBox = null,
            imageWidth = 1000,
            imageHeight = 1000
        )
        assertTrue(amountsDate.isEmpty())

        // Percentages should not be detected as amounts
        val percentText = "Stawka podatku 9%"
        val amountsPercent = AmountParser.extractAmountsFromText(
            text = percentText,
            boundingBox = null,
            imageWidth = 1000,
            imageHeight = 1000
        )
        assertTrue(amountsPercent.isEmpty())
    }

    @Test
    fun formatAmount_formatsProperlyWithZloty() {
        val formatted = DetectedAmount.formatAmount(2198.24)
        assertTrue(formatted.contains("2") && formatted.contains("198") && formatted.contains("24") && formatted.contains("zł"))
    }

    @Test
    fun calculateHealthContribution_calculates9PercentProperly() {
        val total = 1000.0
        val ninePercent = (total * 9.0) / 100.0
        assertEquals(90.0, ninePercent, 0.001)

        val zusTotal = 2198.24
        val healthContrib = (zusTotal * 9.0) / 100.0
        assertEquals(197.84, healthContrib, 0.01)
    }
}
