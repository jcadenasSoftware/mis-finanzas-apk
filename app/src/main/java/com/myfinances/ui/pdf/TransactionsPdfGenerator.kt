package com.myfinances.ui.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.myfinances.data.local.dao.TransactionWithDetails
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TransactionsPdfGenerator {

    private const val PAGE_WIDTH = 595   // A4 @ 72dpi
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val CONTENT_WIDTH = PAGE_WIDTH - MARGIN * 2

    // Brand colors
    private val COLOR_PRIMARY = Color.parseColor("#1A56DB")
    private val COLOR_INCOME = Color.parseColor("#16A34A")
    private val COLOR_EXPENSE = Color.parseColor("#DC2626")
    private val COLOR_BG_HEADER = Color.parseColor("#1A56DB")
    private val COLOR_ROW_ALT = Color.parseColor("#F8FAFC")
    private val COLOR_ROW_NORMAL = Color.WHITE
    private val COLOR_BORDER = Color.parseColor("#E2E8F0")
    private val COLOR_TEXT_PRIMARY = Color.parseColor("#0F172A")
    private val COLOR_TEXT_SECONDARY = Color.parseColor("#64748B")
    private val COLOR_INCOME_BG = Color.parseColor("#F0FDF4")
    private val COLOR_EXPENSE_BG = Color.parseColor("#FEF2F2")

    fun generate(
        context: Context,
        transactions: List<TransactionWithDetails>,
        fromDate: Date,
        toDate: Date,
        userName: String
    ): File {
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale("es", "CO"))
        val dateTimeFormat = SimpleDateFormat("dd/MM/yyyy", Locale("es", "CO"))
        val currencyFormat = NumberFormat.getCurrencyInstance(Locale("es", "CO"))
        val generatedAt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "CO")).format(Date())

        val totalIncome = transactions.filter { it.kind == "INCOME" }.sumOf { it.amountCents }
        val totalExpense = transactions.filter { it.kind == "EXPENSE" }.sumOf { it.amountCents }
        val balance = totalIncome - totalExpense

        val document = PdfDocument()

        // ── Paint helpers ──────────────────────────────────────────────
        fun paint(
            color: Int = COLOR_TEXT_PRIMARY,
            size: Float = 10f,
            bold: Boolean = false,
            align: Paint.Align = Paint.Align.LEFT
        ) = Paint().apply {
            this.color = color
            textSize = size
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            textAlign = align
            isAntiAlias = true
        }

        fun fillPaint(color: Int) = Paint().apply {
            this.color = color
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        fun strokePaint(color: Int, width: Float = 1f) = Paint().apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = width
            isAntiAlias = true
        }

        // Paginate transactions
        val rowHeight = 28f
        val headerSectionHeight = 230f   // space reserved for cover/summary on first page
        val tableHeaderHeight = 26f
        val colHeaderHeight = 22f

        val rowsPerFirstPage = ((PAGE_HEIGHT - headerSectionHeight - tableHeaderHeight - colHeaderHeight - MARGIN * 2) / rowHeight).toInt()
        val rowsPerNextPage = ((PAGE_HEIGHT - MARGIN * 2 - tableHeaderHeight - colHeaderHeight) / rowHeight).toInt()

        val pages = mutableListOf<List<TransactionWithDetails>>()
        if (transactions.isEmpty()) {
            pages.add(emptyList())
        } else {
            pages.add(transactions.take(rowsPerFirstPage))
            var offset = rowsPerFirstPage
            while (offset < transactions.size) {
                pages.add(transactions.subList(offset, minOf(offset + rowsPerNextPage, transactions.size)))
                offset += rowsPerNextPage
            }
        }

        val totalPages = pages.size

        pages.forEachIndexed { pageIndex, pageRows ->
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex + 1).create()
            val page = document.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            var y = MARGIN

            // ── HEADER BANNER (first page only) ────────────────────────
            if (pageIndex == 0) {
                // Blue header background
                canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 110f, fillPaint(COLOR_BG_HEADER))

                // App name
                canvas.drawText(
                    "Mis Finanzas",
                    MARGIN,
                    38f,
                    paint(Color.WHITE, 20f, bold = true)
                )

                // Report title
                canvas.drawText(
                    "Reporte de Transacciones",
                    MARGIN,
                    62f,
                    paint(Color.parseColor("#BFDBFE"), 13f)
                )

                // Period
                canvas.drawText(
                    "${dateFormat.format(fromDate)}  –  ${dateFormat.format(toDate)}",
                    MARGIN,
                    82f,
                    paint(Color.WHITE, 10f)
                )

                // Generated at — right aligned
                canvas.drawText(
                    "Generado: $generatedAt",
                    PAGE_WIDTH - MARGIN,
                    82f,
                    paint(Color.parseColor("#BFDBFE"), 9f, align = Paint.Align.RIGHT)
                )

                // User
                canvas.drawText(
                    "Usuario: $userName",
                    PAGE_WIDTH - MARGIN,
                    38f,
                    paint(Color.WHITE, 10f, align = Paint.Align.RIGHT)
                )

                y = 126f

                // ── SUMMARY CARDS ──────────────────────────────────────
                val cardW = (CONTENT_WIDTH - 12f) / 3f
                val cards = listOf(
                    Triple("Ingresos", totalIncome, COLOR_INCOME),
                    Triple("Gastos", totalExpense, COLOR_EXPENSE),
                    Triple("Balance", balance, if (balance >= 0) COLOR_INCOME else COLOR_EXPENSE)
                )

                cards.forEachIndexed { i, (label, amount, color) ->
                    val cx = MARGIN + i * (cardW + 6f)
                    val cardRect = RectF(cx, y, cx + cardW, y + 64f)

                    // Card shadow (simulated with slightly offset rect)
                    canvas.drawRoundRect(
                        RectF(cx + 1f, y + 2f, cx + cardW + 1f, y + 66f),
                        8f, 8f,
                        fillPaint(Color.parseColor("#E2E8F0"))
                    )
                    canvas.drawRoundRect(cardRect, 8f, 8f, fillPaint(Color.WHITE))
                    canvas.drawRoundRect(cardRect, 8f, 8f, strokePaint(COLOR_BORDER))

                    // Color accent bar at top of card
                    canvas.drawRoundRect(RectF(cx, y, cx + cardW, y + 4f), 4f, 4f, fillPaint(color))

                    canvas.drawText(label, cx + 12f, y + 22f, paint(COLOR_TEXT_SECONDARY, 9f))
                    val amountText = currencyFormat.format(amount / 100.0)
                    val amountSize = when {
                        amountText.length > 16 -> 9f
                        amountText.length > 12 -> 11f
                        else -> 13f
                    }
                    canvas.drawText(amountText, cx + 12f, y + 50f, paint(color, amountSize, bold = true))
                }

                y += 80f

                // Total transactions count badge
                val countText = "${transactions.size} transacciones"
                val badgePaint = fillPaint(Color.parseColor("#EFF6FF"))
                canvas.drawRoundRect(RectF(MARGIN, y, MARGIN + 160f, y + 20f), 10f, 10f, badgePaint)
                canvas.drawRoundRect(RectF(MARGIN, y, MARGIN + 160f, y + 20f), 10f, 10f, strokePaint(Color.parseColor("#BFDBFE")))
                canvas.drawText(countText, MARGIN + 10f, y + 14f, paint(COLOR_PRIMARY, 9f, bold = true))

                y += 30f
            } else {
                // ── CONTINUATION HEADER ────────────────────────────────
                canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 40f, fillPaint(COLOR_BG_HEADER))
                canvas.drawText("Mis Finanzas · Reporte de Transacciones", MARGIN, 26f, paint(Color.WHITE, 10f))
                canvas.drawText(
                    "Pág. ${pageIndex + 1} / $totalPages",
                    PAGE_WIDTH - MARGIN,
                    26f,
                    paint(Color.parseColor("#BFDBFE"), 9f, align = Paint.Align.RIGHT)
                )
                y = 56f
            }

            // ── TABLE HEADER ────────────────────────────────────────────
            canvas.drawText(
                "Detalle de transacciones",
                MARGIN,
                y + 14f,
                paint(COLOR_TEXT_PRIMARY, 11f, bold = true)
            )
            y += 24f

            // Column header background
            canvas.drawRect(MARGIN, y, MARGIN + CONTENT_WIDTH, y + colHeaderHeight, fillPaint(Color.parseColor("#1E40AF")))

            // Column headers
            val col = columnPositions()
            canvas.drawText("Fecha", MARGIN + col.date + 4f, y + 15f, paint(Color.WHITE, 8f, bold = true))
            canvas.drawText("Tipo", MARGIN + col.type + 4f, y + 15f, paint(Color.WHITE, 8f, bold = true))
            canvas.drawText("Categoría", MARGIN + col.category + 4f, y + 15f, paint(Color.WHITE, 8f, bold = true))
            canvas.drawText("Cuenta", MARGIN + col.account + 4f, y + 15f, paint(Color.WHITE, 8f, bold = true))
            canvas.drawText("Monto", MARGIN + col.amount + col.amountW - 4f, y + 15f, paint(Color.WHITE, 8f, bold = true, align = Paint.Align.RIGHT))
            y += colHeaderHeight

            // ── ROWS ───────────────────────────────────────────────────
            if (pageRows.isEmpty() && pageIndex == 0) {
                y += 20f
                canvas.drawText(
                    "No hay transacciones en el período seleccionado.",
                    MARGIN + CONTENT_WIDTH / 2,
                    y,
                    paint(COLOR_TEXT_SECONDARY, 10f, align = Paint.Align.CENTER)
                )
            } else {
                // Global row index for alternating colors
                val globalOffset = pages.take(pageIndex).sumOf { it.size }

                pageRows.forEachIndexed { localIndex, t ->
                    val globalIndex = globalOffset + localIndex
                    val isIncome = t.kind == "INCOME"
                    val bgColor = if (globalIndex % 2 == 0) COLOR_ROW_NORMAL else COLOR_ROW_ALT

                    canvas.drawRect(MARGIN, y, MARGIN + CONTENT_WIDTH, y + rowHeight, fillPaint(bgColor))

                    // Left accent line per type
                    canvas.drawRect(
                        MARGIN, y, MARGIN + 3f, y + rowHeight,
                        fillPaint(if (isIncome) COLOR_INCOME else COLOR_EXPENSE)
                    )

                    val textY = y + rowHeight * 0.64f
                    val dateStr = dateTimeFormat.format(Date(t.occurredAtEpochSec * 1000))

                    canvas.drawText(dateStr, MARGIN + col.date + 6f, textY, paint(COLOR_TEXT_SECONDARY, 8f))

                    // Type pill
                    val typeLabel = if (isIncome) "Ingreso" else "Gasto"
                    val typeBg = if (isIncome) COLOR_INCOME_BG else COLOR_EXPENSE_BG
                    val typeColor = if (isIncome) COLOR_INCOME else COLOR_EXPENSE
                    val pillX = MARGIN + col.type + 4f
                    canvas.drawRoundRect(RectF(pillX, y + 6f, pillX + 44f, y + rowHeight - 6f), 6f, 6f, fillPaint(typeBg))
                    canvas.drawText(typeLabel, pillX + 22f, textY, paint(typeColor, 7.5f, bold = true, align = Paint.Align.CENTER))

                    // Category (truncate)
                    val catName = t.categoryName.take(18)
                    canvas.drawText(catName, MARGIN + col.category + 4f, textY, paint(COLOR_TEXT_PRIMARY, 8f))

                    // Account (truncate)
                    val accName = t.accountName.take(16)
                    canvas.drawText(accName, MARGIN + col.account + 4f, textY, paint(COLOR_TEXT_SECONDARY, 8f))

                    // Amount right-aligned
                    val amountText = currencyFormat.format(t.amountCents / 100.0)
                    canvas.drawText(
                        amountText,
                        MARGIN + col.amount + col.amountW - 4f,
                        textY,
                        paint(if (isIncome) COLOR_INCOME else COLOR_EXPENSE, 8f, bold = true, align = Paint.Align.RIGHT)
                    )

                    // Bottom border
                    canvas.drawLine(MARGIN, y + rowHeight, MARGIN + CONTENT_WIDTH, y + rowHeight, strokePaint(COLOR_BORDER, 0.5f))

                    y += rowHeight
                }
            }

            // ── FOOTER ─────────────────────────────────────────────────
            val footerY = PAGE_HEIGHT - 24f
            canvas.drawLine(MARGIN, footerY - 10f, MARGIN + CONTENT_WIDTH, footerY - 10f, strokePaint(COLOR_BORDER))
            canvas.drawText(
                "Mis Finanzas · Reporte confidencial",
                MARGIN,
                footerY,
                paint(COLOR_TEXT_SECONDARY, 8f)
            )
            canvas.drawText(
                "Pág. ${pageIndex + 1} / $totalPages",
                PAGE_WIDTH - MARGIN,
                footerY,
                paint(COLOR_TEXT_SECONDARY, 8f, align = Paint.Align.RIGHT)
            )

            document.finishPage(page)
        }

        val file = File(context.cacheDir, "transacciones_${System.currentTimeMillis()}.pdf")
        file.outputStream().use { document.writeTo(it) }
        document.close()
        return file
    }

    private data class Columns(
        val date: Float, val type: Float, val category: Float,
        val account: Float, val amount: Float, val amountW: Float
    )

    private fun columnPositions(): Columns {
        val dateW = 58f
        val typeW = 54f
        val categoryW = 110f
        val accountW = 100f
        val amountW = 93f  // remaining

        return Columns(
            date = 0f,
            type = dateW,
            category = dateW + typeW,
            account = dateW + typeW + categoryW,
            amount = dateW + typeW + categoryW + accountW,
            amountW = amountW
        )
    }
}
