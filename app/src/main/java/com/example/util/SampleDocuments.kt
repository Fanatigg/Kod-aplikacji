package com.example.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface

enum class SampleDocumentType(val title: String, val subtitle: String) {
    ZUS_ZDROWOTNA_TABLE("ZUS Składki Zdrowotne (7 pozycji)", "Układ z pracy użytkownika: kolumna zdrowotna (7 wierszy) oraz kolumna boczna"),
    ZUS_DRA_FULL("ZUS DRA (Duży ZUS 2024)", "Pełne składki ZUS: emerytalna, rentowa, zdrowotna, chorobowa, wypadkowa, FP"),
    ZUS_DRA_PREF("ZUS Mały ZUS Plus", "Preferencyjne składki ubezpieczeniowe dla przedsiębiorcy"),
    INVOICE_EXPENSES("Zestawienie Kosztów / Faktura", "Wydatki firmowe: hosting, księgowość, internet, licencje")
}

object SampleDocuments {

    fun generateSampleBitmap(type: SampleDocumentType): Bitmap {
        return when (type) {
            SampleDocumentType.ZUS_ZDROWOTNA_TABLE -> createZusZdrowotnaTableBitmap()
            SampleDocumentType.ZUS_DRA_FULL -> createZusDraBitmap()
            SampleDocumentType.ZUS_DRA_PREF -> createMalyZusBitmap()
            SampleDocumentType.INVOICE_EXPENSES -> createInvoiceBitmap()
        }
    }

    private fun createZusDraBitmap(): Bitmap {
        val width = 1080
        val height = 1520
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Paper background
        canvas.drawColor(Color.WHITE)

        val borderPaint = Paint().apply {
            color = Color.rgb(203, 213, 225)
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        val headerBgPaint = Paint().apply {
            color = Color.rgb(241, 245, 249)
            style = Paint.Style.FILL
        }
        val tableHeaderPaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            style = Paint.Style.FILL
        }
        val textPaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 34f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val boldPaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 38f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val amountPaint = Paint().apply {
            color = Color.rgb(30, 41, 59)
            textSize = 36f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val subTextPaint = Paint().apply {
            color = Color.rgb(100, 116, 139)
            textSize = 26f
            isAntiAlias = true
        }

        // Outer sheet frame
        canvas.drawRoundRect(RectF(40f, 40f, width - 40f, height - 40f), 16f, 16f, borderPaint)

        // Header block
        canvas.drawRoundRect(RectF(60f, 60f, width - 60f, 220f), 12f, 12f, headerBgPaint)
        canvas.drawText("ZAKŁAD UBEZPIECZEŃ SPOŁECZNYCH", 80f, 120f, boldPaint)
        canvas.drawText("DEKLARACJA ROZLICZENIOWA ZUS DRA", 80f, 165f, textPaint)
        canvas.drawText("Identyfikator: 01 05.2024 | Płatnik: JAN KOWALSKI USŁUGI IT", 80f, 205f, subTextPaint)

        // Table Header
        var currentY = 270f
        canvas.drawRect(RectF(60f, currentY, width - 60f, currentY + 60f), tableHeaderPaint)
        canvas.drawText("Nazwa składki ubezpieczeniowej", 80f, currentY + 42f, boldPaint)
        canvas.drawText("Kwota (PLN)", width - 340f, currentY + 42f, boldPaint)
        currentY += 80f

        val items = listOf(
            Pair("Składka na ubezpieczenie emerytalne", "1 018,48 zł"),
            Pair("Składka na ubezpieczenie rentowe", "417,41 zł"),
            Pair("Składka na ubezpieczenie chorobowe", "127,88 zł"),
            Pair("Składka na ubezpieczenie wypadkowe", "87,13 zł"),
            Pair("Składka na ubezpieczenie zdrowotne", "419,46 zł"),
            Pair("Składka na Fundusz Pracy (FP)", "127,88 zł")
        )

        val rowBorderPaint = Paint().apply {
            color = Color.rgb(241, 245, 249)
            strokeWidth = 2f
        }

        for ((index, item) in items.withIndex()) {
            val rowY = currentY + (index * 95f)
            canvas.drawLine(60f, rowY + 30f, width - 60f, rowY + 30f, rowBorderPaint)
            canvas.drawText("${index + 1}. ${item.first}", 80f, rowY, textPaint)
            canvas.drawText(item.second, width - 340f, rowY, amountPaint)
        }

        currentY += (items.size * 95f) + 40f

        // Total Section Box
        val totalBoxPaint = Paint().apply {
            color = Color.rgb(238, 242, 255)
            style = Paint.Style.FILL
        }
        val totalBorder = Paint().apply {
            color = Color.rgb(99, 102, 241)
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        val totalTextPaint = Paint().apply {
            color = Color.rgb(49, 46, 129)
            textSize = 42f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val totalAmountPaint = Paint().apply {
            color = Color.rgb(30, 64, 175)
            textSize = 44f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }

        canvas.drawRoundRect(RectF(60f, currentY, width - 60f, currentY + 130f), 12f, 12f, totalBoxPaint)
        canvas.drawRoundRect(RectF(60f, currentY, width - 60f, currentY + 130f), 12f, 12f, totalBorder)

        canvas.drawText("ŁĄCZNA KWOTA DO ZAPŁATY:", 90f, currentY + 80f, totalTextPaint)
        canvas.drawText("2 198,24 zł", width - 360f, currentY + 80f, totalAmountPaint)

        // Footer info
        canvas.drawText("Termin płatności: 20 maja 2024 r. Rachunek indywidualny NRS ZUS.", 80f, height - 90f, subTextPaint)

        return bitmap
    }

    private fun createMalyZusBitmap(): Bitmap {
        val width = 1080
        val height = 1400
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawColor(Color.WHITE)

        val borderPaint = Paint().apply {
            color = Color.rgb(203, 213, 225)
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        val headerBgPaint = Paint().apply {
            color = Color.rgb(240, 253, 244)
            style = Paint.Style.FILL
        }
        val textPaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 34f
            isAntiAlias = true
        }
        val boldPaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 38f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val amountPaint = Paint().apply {
            color = Color.rgb(30, 41, 59)
            textSize = 36f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }

        canvas.drawRoundRect(RectF(40f, 40f, width - 40f, height - 40f), 16f, 16f, borderPaint)

        canvas.drawRoundRect(RectF(60f, 60f, width - 60f, 220f), 12f, 12f, headerBgPaint)
        canvas.drawText("ZAKŁAD UBEZPIECZEŃ SPOŁECZNYCH", 80f, 120f, boldPaint)
        canvas.drawText("ROZLICZENIE: MAŁY ZUS PLUS / PREFERENCYJNY", 80f, 165f, textPaint)

        val items = listOf(
            Pair("Składka emerytalna", "381,78 zł"),
            Pair("Składka rentowa", "156,47 zł"),
            Pair("Składka chorobowa dobrowolna", "47,93 zł"),
            Pair("Składka wypadkowa", "32,66 zł"),
            Pair("Składka zdrowotna (skala/liniowy)", "419,46 zł")
        )

        var currentY = 320f
        for ((index, item) in items.withIndex()) {
            val rowY = currentY + (index * 110f)
            canvas.drawText("${index + 1}. ${item.first}", 80f, rowY, textPaint)
            canvas.drawText(item.second, width - 340f, rowY, amountPaint)
        }

        currentY += (items.size * 110f) + 50f
        val totalBoxPaint = Paint().apply {
            color = Color.rgb(240, 253, 244)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(60f, currentY, width - 60f, currentY + 120f), 12f, 12f, totalBoxPaint)
        canvas.drawText("SUMA SKŁADEK:", 90f, currentY + 75f, boldPaint)
        canvas.drawText("1 038,30 zł", width - 360f, currentY + 75f, amountPaint)

        return bitmap
    }

    private fun createInvoiceBitmap(): Bitmap {
        val width = 1080
        val height = 1500
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawColor(Color.WHITE)

        val textPaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 34f
            isAntiAlias = true
        }
        val boldPaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 38f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val amountPaint = Paint().apply {
            color = Color.rgb(30, 41, 59)
            textSize = 36f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }

        canvas.drawText("FAKTURA VAT NR 184/2024", 80f, 120f, boldPaint)
        canvas.drawText("Wydrukowano: 24.10.2024 | Termin płatności: 14 dni", 80f, 170f, textPaint)

        val items = listOf(
            Pair("Hosting w chmurze i domeny", "249,00 zł"),
            Pair("Obsługa księgowa jednoosobowej firmy", "350,00 zł"),
            Pair("Łącze internetowe światłowód", "120,50 zł"),
            Pair("Materiały biurowe i papier", "85,20 zł"),
            Pair("Abonament oprogramowania CAD", "199,00 zł")
        )

        var currentY = 280f
        for ((index, item) in items.withIndex()) {
            val rowY = currentY + (index * 110f)
            canvas.drawText("${index + 1}. ${item.first}", 80f, rowY, textPaint)
            canvas.drawText(item.second, width - 340f, rowY, amountPaint)
        }

        currentY += (items.size * 110f) + 60f
        canvas.drawText("RAZEM DO ZAPŁATY (BRUTTO):", 80f, currentY, boldPaint)
        canvas.drawText("1 003,70 zł", width - 360f, currentY, amountPaint)

        return bitmap
    }

    private fun createZusZdrowotnaTableBitmap(): Bitmap {
        val width = 1080
        val height = 1600
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawColor(Color.WHITE)

        val gridPaint = Paint().apply {
            color = Color.rgb(203, 213, 225)
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        val headerBg = Paint().apply {
            color = Color.rgb(248, 250, 252)
            style = Paint.Style.FILL
        }
        val titlePaint = Paint().apply {
            color = Color.rgb(30, 41, 59)
            textSize = 28f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val numPaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 34f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }

        // Table headers (3 columns)
        val headerTop = 150f
        val headerBottom = 320f
        canvas.drawRect(RectF(40f, headerTop, width - 40f, headerBottom), headerBg)
        canvas.drawRect(RectF(40f, headerTop, width - 40f, headerBottom), gridPaint)

        // Column lines
        canvas.drawLine(380f, headerTop, 380f, 1300f, gridPaint)
        canvas.drawLine(820f, headerTop, 820f, 1300f, gridPaint)

        // Headers text
        canvas.drawText("Podstawa wymiaru", 70f, 210f, titlePaint)
        canvas.drawText("składek na", 70f, 250f, titlePaint)
        canvas.drawText("ubezpieczenia", 70f, 290f, titlePaint)

        canvas.drawText("Podstawa wymiaru", 420f, 200f, titlePaint)
        canvas.drawText("składek na", 420f, 240f, titlePaint)
        canvas.drawText("ubezpieczenia", 420f, 280f, titlePaint)
        canvas.drawText("zdrowotne", 420f, 315f, titlePaint)

        canvas.drawText("Przekroczenie", 840f, 210f, titlePaint)
        canvas.drawText("rocznej...", 840f, 250f, titlePaint)

        // 7 Rows of Zdrowotna values in center column
        val rowHeights = 110f
        val mainColAmounts = listOf(
            "7 162,41 PLN",
            "7 162,41 PLN",
            "7 162,41 PLN",
            "7 162,41 PLN",
            "9 923,69 PLN",
            "7 162,41 PLN",
            "11 407,88 PLN"
        )

        val leftColAmounts = listOf(
            "8 300,40 PLN",
            "8 300,40 PLN",
            "11 500,40 PLN",
            "8 300,40 PLN",
            "13 220,40 PLN",
            "8 300,40 PLN",
            "12 100,00 PLN"
        )

        for (i in 0 until 7) {
            val y = headerBottom + ((i + 1) * rowHeights)
            val lineY = headerBottom + (i * rowHeights)
            canvas.drawLine(40f, y, width - 40f, y, gridPaint)

            // Draw Left Column (Adjacent column that user wanted to exclude)
            canvas.drawText(leftColAmounts[i], 70f, y - 40f, numPaint)

            // Draw Center Column (The 7 rows user wants to sum!)
            canvas.drawText(mainColAmounts[i], 440f, y - 40f, numPaint)
        }

        // Outer borders
        canvas.drawRect(RectF(40f, headerTop, width - 40f, headerBottom + (7 * rowHeights)), gridPaint)

        val footnotePaint = Paint().apply {
            color = Color.rgb(100, 116, 139)
            textSize = 24f
            isAntiAlias = true
        }
        canvas.drawText("zą z dokumentów rozliczeniowych.", 40f, headerBottom + (7 * rowHeights) + 50f, footnotePaint)

        return bitmap
    }
}
