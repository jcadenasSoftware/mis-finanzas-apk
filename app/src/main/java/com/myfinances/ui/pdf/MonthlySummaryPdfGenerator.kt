package com.myfinances.ui.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.myfinances.data.local.dao.RootCategorySpentTotal
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object MonthlySummaryPdfGenerator {

    private const val PAGE_WIDTH  = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN      = 36f
    private const val CONTENT_WIDTH = PAGE_WIDTH - MARGIN * 2   // 523f

    private val COLOR_PRIMARY      = Color.parseColor("#1A56DB")
    private val COLOR_PRIMARY_DARK = Color.parseColor("#1E40AF")
    private val COLOR_INCOME       = Color.parseColor("#16A34A")
    private val COLOR_EXPENSE      = Color.parseColor("#DC2626")
    private val COLOR_INCOME_BG    = Color.parseColor("#F0FDF4")
    private val COLOR_EXPENSE_BG   = Color.parseColor("#FEF2F2")
    private val COLOR_BORDER       = Color.parseColor("#E2E8F0")
    private val COLOR_TEXT         = Color.parseColor("#0F172A")
    private val COLOR_TEXT_MUTED   = Color.parseColor("#64748B")
    private val COLOR_SURFACE      = Color.parseColor("#F8FAFC")
    private val COLOR_WHITE        = Color.WHITE
    private val COLOR_ACCENT_BLUE  = Color.parseColor("#EFF6FF")
    private val COLOR_ACCENT_BORDER = Color.parseColor("#BFDBFE")
    private val COLOR_WARN         = Color.parseColor("#D97706")
    private val COLOR_WARN_BG      = Color.parseColor("#FFFBEB")

    data class BudgetLine(
        val categoryName: String,
        val limitCents: Long,
        val spentCents: Long
    )

    fun generate(
        context: Context,
        monthKey: String,
        incomeCents: Long,
        expenseCents: Long,
        incomeHierarchy: List<com.myfinances.data.local.dao.HierarchyCategoryTotal>,
        expenseHierarchy: List<com.myfinances.data.local.dao.HierarchyCategoryTotal>,
        budgetLines: List<BudgetLine>,
        userName: String
    ): File {
        val cf = NumberFormat.getCurrencyInstance(Locale("es", "CO"))
        val generatedAt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "CO")).format(Date())
        val monthLabel = monthLabel(monthKey)
        val balance = incomeCents - expenseCents
        val totalBudgetLimit = budgetLines.sumOf { it.limitCents }
        val totalBudgetSpent = budgetLines.sumOf { it.spentCents }

        val document = PdfDocument()

        // ── Helpers ────────────────────────────────────────────────────
        fun paint(
            color: Int = COLOR_TEXT,
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
        fun fill(c: Int)  = Paint().apply { color = c; style = Paint.Style.FILL;   isAntiAlias = true }
        fun stroke(c: Int, w: Float = 1f) = Paint().apply { color = c; style = Paint.Style.STROKE; strokeWidth = w; isAntiAlias = true }
        fun fmt(cents: Long) = cf.format(cents / 100.0)

        // ── Pagination state ───────────────────────────────────────────
        val pageBottom = PAGE_HEIGHT - 34f
        var pageIndex  = 0

        fun newPage(): PdfDocument.Page =
            document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex + 1).create())

        var currentPage = newPage()
        var canvas: Canvas = currentPage.canvas
        var y = 0f

        fun Canvas.drawFooter() {
            val fy = PAGE_HEIGHT.toFloat() - 18f
            drawLine(MARGIN, fy - 8f, MARGIN + CONTENT_WIDTH, fy - 8f, stroke(COLOR_BORDER))
            drawText("Mis Finanzas · Reporte confidencial", MARGIN, fy, paint(COLOR_TEXT_MUTED, 7.5f))
            drawText("Pág. ${pageIndex + 1}", PAGE_WIDTH - MARGIN, fy, paint(COLOR_TEXT_MUTED, 7.5f, align = Paint.Align.RIGHT))
        }

        fun Canvas.contHeader() {
            drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 36f, fill(COLOR_PRIMARY))
            drawText("Mis Finanzas · Resumen Mensual · $monthLabel", MARGIN, 24f, paint(COLOR_WHITE, 9f))
        }

        fun checkNewPage(needed: Float) {
            if (y + needed > pageBottom) {
                canvas.drawFooter()
                document.finishPage(currentPage)
                pageIndex++
                currentPage = newPage()
                canvas = currentPage.canvas
                canvas.contHeader()
                y = 44f
            }
        }

        // ── Group hierarchy by root ────────────────────────────────────
        fun groupByRoot(rows: List<com.myfinances.data.local.dao.HierarchyCategoryTotal>)
            : LinkedHashMap<String, Pair<String, List<com.myfinances.data.local.dao.HierarchyCategoryTotal>>> {
            val map = LinkedHashMap<String, Pair<String, MutableList<com.myfinances.data.local.dao.HierarchyCategoryTotal>>>()
            for (r in rows) {
                map.getOrPut(r.rootCategoryId) { r.rootCategoryName to mutableListOf() }
                    .second.add(r)
            }
            // Sort roots by total desc
            val sorted = map.entries.sortedByDescending { e -> e.value.second.sumOf { it.totalCents } }
            return sorted.associateTo(LinkedHashMap()) { it.key to (it.value.first to it.value.second as List<com.myfinances.data.local.dao.HierarchyCategoryTotal>) }
        }

        val incomeGroups  = groupByRoot(incomeHierarchy)
        val expenseGroups = groupByRoot(expenseHierarchy)

        // ── Row heights ────────────────────────────────────────────────
        val rootRowH = 22f   // category (root) header row
        val subRowH  = 18f   // subcategory row
        val budRowH  = 26f   // budget row

        // Helper to draw one hierarchy section
        fun drawHierarchySection(
            groups: LinkedHashMap<String, Pair<String, List<com.myfinances.data.local.dao.HierarchyCategoryTotal>>>,
            accentColor: Int,
            accentBg: Int
        ) {
            if (groups.isEmpty()) {
                checkNewPage(20f)
                canvas.drawText(
                    "Sin movimientos en este período.",
                    MARGIN + CONTENT_WIDTH / 2, y + 14f,
                    paint(COLOR_TEXT_MUTED, 8.5f, align = Paint.Align.CENTER)
                )
                y += 24f
                return
            }

            var rootIdx = 0
            for ((_, pair) in groups) {
                val (rootName, subs) = pair
                val rootTotal = subs.sumOf { it.totalCents }

                // Root row — always keep together with at least 1 sub
                checkNewPage(rootRowH + subRowH)

                // Root header
                canvas.drawRect(MARGIN, y, MARGIN + CONTENT_WIDTH, y + rootRowH, fill(accentBg))
                canvas.drawRect(MARGIN, y, MARGIN + 4f, y + rootRowH, fill(accentColor))
                canvas.drawText(
                    rootName.take(30),
                    MARGIN + 10f, y + rootRowH * 0.72f,
                    paint(COLOR_TEXT, 8.5f, bold = true)
                )
                canvas.drawText(
                    fmt(rootTotal),
                    MARGIN + CONTENT_WIDTH - 6f, y + rootRowH * 0.72f,
                    paint(accentColor, 8.5f, bold = true, align = Paint.Align.RIGHT)
                )
                canvas.drawLine(MARGIN, y + rootRowH, MARGIN + CONTENT_WIDTH, y + rootRowH, stroke(COLOR_BORDER, 0.4f))
                y += rootRowH

                // Subcategory rows
                subs.forEachIndexed { subIdx, sub ->
                    checkNewPage(subRowH + 1f)
                    val subBg = if ((rootIdx + subIdx) % 2 == 0) COLOR_WHITE else COLOR_SURFACE
                    canvas.drawRect(MARGIN, y, MARGIN + CONTENT_WIDTH, y + subRowH, fill(subBg))
                    // Indent marker
                    canvas.drawRect(MARGIN + 4f, y, MARGIN + 6f, y + subRowH, fill(accentColor))

                    val subName = if (sub.subCategoryId == sub.rootCategoryId) "General" else sub.subCategoryName.take(28)
                    canvas.drawText(
                        subName,
                        MARGIN + 14f, y + subRowH * 0.72f,
                        paint(COLOR_TEXT_MUTED, 7.5f)
                    )
                    canvas.drawText(
                        fmt(sub.totalCents),
                        MARGIN + CONTENT_WIDTH - 6f, y + subRowH * 0.72f,
                        paint(COLOR_TEXT, 7.5f, bold = true, align = Paint.Align.RIGHT)
                    )
                    canvas.drawLine(MARGIN + 14f, y + subRowH, MARGIN + CONTENT_WIDTH, y + subRowH, stroke(COLOR_BORDER, 0.3f))
                    y += subRowH
                }
                y += 4f
                rootIdx++
            }
        }

        // ════════════════════════════════════════════════════════════════
        // PAGE 1 — HEADER BANNER
        // ════════════════════════════════════════════════════════════════
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 100f, fill(COLOR_PRIMARY))
        canvas.drawCircle(PAGE_WIDTH + 10f, -10f, 90f, fill(COLOR_PRIMARY_DARK))
        canvas.drawCircle(PAGE_WIDTH - 50f, 115f, 48f, fill(COLOR_PRIMARY_DARK))
        canvas.drawText("Mis Finanzas", MARGIN, 34f, paint(COLOR_WHITE, 18f, bold = true))
        canvas.drawText("Resumen Mensual", MARGIN, 55f, paint(Color.parseColor("#BFDBFE"), 12f))
        canvas.drawText(monthLabel, MARGIN, 73f, paint(COLOR_WHITE, 9f))
        canvas.drawText("Usuario: ${userName.take(32)}", PAGE_WIDTH - MARGIN, 34f, paint(COLOR_WHITE, 9f, align = Paint.Align.RIGHT))
        canvas.drawText("Generado: $generatedAt", PAGE_WIDTH - MARGIN, 52f, paint(Color.parseColor("#BFDBFE"), 8f, align = Paint.Align.RIGHT))
        y = 112f

        // ── 1. TARJETAS INGRESOS / GASTOS / BALANCE ───────────────────
        val cardW = (CONTENT_WIDTH - 10f) / 3f
        listOf(
            Triple("Ingresos", incomeCents,  COLOR_INCOME),
            Triple("Gastos",   expenseCents, COLOR_EXPENSE),
            Triple("Balance",  balance,      if (balance >= 0) COLOR_INCOME else COLOR_EXPENSE)
        ).forEachIndexed { i, (label, amount, color) ->
            val cx = MARGIN + i * (cardW + 5f)
            val rect = RectF(cx, y, cx + cardW, y + 56f)
            canvas.drawRoundRect(RectF(cx + 1f, y + 2f, cx + cardW + 1f, y + 58f), 7f, 7f, fill(COLOR_BORDER))
            canvas.drawRoundRect(rect, 7f, 7f, fill(COLOR_WHITE))
            canvas.drawRoundRect(RectF(cx, y, cx + cardW, y + 4f), 3f, 3f, fill(color))
            canvas.drawText(label, cx + 10f, y + 20f, paint(COLOR_TEXT_MUTED, 8f))
            val rawAmt = fmt(amount)
            val sz = when { rawAmt.length > 18 -> 7f; rawAmt.length > 14 -> 8.5f; rawAmt.length > 10 -> 10f; else -> 11.5f }
            canvas.drawText(rawAmt, cx + 10f, y + 44f, paint(color, sz, bold = true))
        }
        y += 66f

        // Tasa de ahorro
        val savingsRate = if (incomeCents > 0) ((balance * 100.0) / incomeCents).toInt() else 0
        val rateColor = when { savingsRate >= 20 -> COLOR_INCOME; savingsRate >= 0 -> COLOR_WARN; else -> COLOR_EXPENSE }
        val rateBg    = when { savingsRate >= 20 -> COLOR_INCOME_BG; savingsRate >= 0 -> COLOR_WARN_BG; else -> COLOR_EXPENSE_BG }
        canvas.drawRoundRect(RectF(MARGIN, y, MARGIN + 170f, y + 20f), 10f, 10f, fill(rateBg))
        canvas.drawRoundRect(RectF(MARGIN, y, MARGIN + 170f, y + 20f), 10f, 10f, stroke(rateColor, 0.7f))
        canvas.drawText("Tasa de ahorro: $savingsRate%", MARGIN + 85f, y + 14f, paint(rateColor, 8.5f, bold = true, align = Paint.Align.CENTER))
        y += 28f

        // ── 2. CATEGORÍAS DE INGRESOS ─────────────────────────────────
        checkNewPage(36f)
        canvas.drawRect(MARGIN, y, MARGIN + CONTENT_WIDTH, y + 22f, fill(COLOR_INCOME))
        canvas.drawText("INGRESOS POR CATEGORÍA", MARGIN + CONTENT_WIDTH / 2, y + 15f, paint(COLOR_WHITE, 9f, bold = true, align = Paint.Align.CENTER))
        y += 26f

        drawHierarchySection(incomeGroups, COLOR_INCOME, COLOR_INCOME_BG)
        y += 8f

        // ── 3. CATEGORÍAS DE GASTOS ───────────────────────────────────
        checkNewPage(36f)
        canvas.drawRect(MARGIN, y, MARGIN + CONTENT_WIDTH, y + 22f, fill(COLOR_EXPENSE))
        canvas.drawText("GASTOS POR CATEGORÍA", MARGIN + CONTENT_WIDTH / 2, y + 15f, paint(COLOR_WHITE, 9f, bold = true, align = Paint.Align.CENTER))
        y += 26f

        drawHierarchySection(expenseGroups, COLOR_EXPENSE, COLOR_EXPENSE_BG)
        y += 8f

        // ── 4. PRESUPUESTO DEL MES ────────────────────────────────────
        checkNewPage(60f)
        canvas.drawRect(MARGIN, y, MARGIN + CONTENT_WIDTH, y + 22f, fill(COLOR_PRIMARY_DARK))
        canvas.drawText("PRESUPUESTO DEL MES", MARGIN + CONTENT_WIDTH / 2, y + 15f, paint(COLOR_WHITE, 9f, bold = true, align = Paint.Align.CENTER))
        y += 26f

        if (budgetLines.isEmpty()) {
            canvas.drawText("No hay presupuestos configurados.", MARGIN + CONTENT_WIDTH / 2, y + 14f, paint(COLOR_TEXT_MUTED, 8.5f, align = Paint.Align.CENTER))
            y += 24f
        } else {
            val budgetPct      = if (totalBudgetLimit > 0) (totalBudgetSpent * 100 / totalBudgetLimit).toInt() else 0
            val budgetBarColor = when { budgetPct >= 100 -> COLOR_EXPENSE; budgetPct >= 80 -> COLOR_WARN; else -> COLOR_INCOME }

            // Summary row
            canvas.drawText("Límite total: ${fmt(totalBudgetLimit)}", MARGIN, y + 12f, paint(COLOR_TEXT_MUTED, 8f))
            canvas.drawText("Gastado: ${fmt(totalBudgetSpent)}", MARGIN + CONTENT_WIDTH * 0.5f, y + 12f, paint(budgetBarColor, 8f, bold = true))
            canvas.drawText("$budgetPct%", MARGIN + CONTENT_WIDTH, y + 12f, paint(budgetBarColor, 8f, bold = true, align = Paint.Align.RIGHT))
            y += 16f

            // Global bar
            val gfW = (CONTENT_WIDTH * (totalBudgetSpent.toFloat() / totalBudgetLimit.toFloat().coerceAtLeast(1f))).coerceIn(0f, CONTENT_WIDTH)
            canvas.drawRoundRect(RectF(MARGIN, y, MARGIN + CONTENT_WIDTH, y + 7f), 3f, 3f, fill(COLOR_SURFACE))
            canvas.drawRoundRect(RectF(MARGIN, y, MARGIN + gfW, y + 7f), 3f, 3f, fill(budgetBarColor))
            y += 14f

            // Column headers
            val c0 = MARGIN + 6f
            val c1 = MARGIN + CONTENT_WIDTH * 0.40f
            val c2 = MARGIN + CONTENT_WIDTH * 0.58f
            val c3 = MARGIN + CONTENT_WIDTH * 0.76f
            val c4 = MARGIN + CONTENT_WIDTH - 4f
            canvas.drawRect(MARGIN, y, MARGIN + CONTENT_WIDTH, y + 18f, fill(Color.parseColor("#334155")))
            canvas.drawText("Categoría", c0, y + 13f, paint(COLOR_WHITE, 7.5f, bold = true))
            canvas.drawText("Límite",    c1, y + 13f, paint(COLOR_WHITE, 7.5f, bold = true))
            canvas.drawText("Gastado",   c2, y + 13f, paint(COLOR_WHITE, 7.5f, bold = true))
            canvas.drawText("Uso",       c3, y + 13f, paint(COLOR_WHITE, 7.5f, bold = true))
            canvas.drawText("Estado",    c4, y + 13f, paint(COLOR_WHITE, 7.5f, bold = true, align = Paint.Align.RIGHT))
            y += 18f

            budgetLines.forEachIndexed { idx, line ->
                checkNewPage(budRowH + 1f)
                val pct   = if (line.limitCents > 0) (line.spentCents * 100 / line.limitCents).toInt() else 0
                val rc    = when { pct >= 100 -> COLOR_EXPENSE; pct >= 80 -> COLOR_WARN; else -> COLOR_INCOME }
                val rowBg = if (idx % 2 == 0) COLOR_WHITE else COLOR_SURFACE

                canvas.drawRect(MARGIN, y, MARGIN + CONTENT_WIDTH, y + budRowH, fill(rowBg))
                canvas.drawRect(MARGIN, y, MARGIN + 3f, y + budRowH, fill(rc))

                val ty = y + budRowH * 0.64f
                canvas.drawText(line.categoryName.take(22), c0, ty, paint(COLOR_TEXT, 7.5f))
                canvas.drawText(fmt(line.limitCents), c1, ty, paint(COLOR_TEXT_MUTED, 7.5f))
                canvas.drawText(fmt(line.spentCents), c2, ty, paint(rc, 7.5f, bold = true))
                canvas.drawText("$pct%", c3, ty, paint(rc, 7.5f, bold = true))

                // Mini bar
                val mbW  = CONTENT_WIDTH * 0.13f
                val mbX  = c4 - mbW
                val mbFW = (mbW * (line.spentCents.toFloat() / line.limitCents.toFloat().coerceAtLeast(1f))).coerceIn(0f, mbW)
                canvas.drawRoundRect(RectF(mbX, y + budRowH * 0.68f, mbX + mbW, y + budRowH * 0.86f), 2f, 2f, fill(COLOR_SURFACE))
                canvas.drawRoundRect(RectF(mbX, y + budRowH * 0.68f, mbX + mbFW, y + budRowH * 0.86f), 2f, 2f, fill(rc))

                canvas.drawText(
                    when { pct >= 100 -> "Excedido"; pct >= 80 -> "Al límite"; else -> "OK" },
                    c4, ty, paint(rc, 7f, bold = true, align = Paint.Align.RIGHT)
                )
                canvas.drawLine(MARGIN, y + budRowH, MARGIN + CONTENT_WIDTH, y + budRowH, stroke(COLOR_BORDER, 0.3f))
                y += budRowH
            }
        }

        canvas.drawFooter()
        document.finishPage(currentPage)

        val file = File(context.cacheDir, "resumen_mensual_${System.currentTimeMillis()}.pdf")
        file.outputStream().use { document.writeTo(it) }
        document.close()
        return file
    }

    private fun monthLabel(monthKey: String): String {
        return try {
            val parts = monthKey.split("-")
            val year = parts[0].toInt()
            val month = parts[1].toInt()
            val cal = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month - 1)
                set(Calendar.DAY_OF_MONTH, 1)
            }
            val monthName = SimpleDateFormat("MMMM", Locale("es", "CO")).format(cal.time)
                .replaceFirstChar { it.titlecase(Locale("es", "CO")) }
            "$monthName $year"
        } catch (_: Exception) { monthKey }
    }
}
