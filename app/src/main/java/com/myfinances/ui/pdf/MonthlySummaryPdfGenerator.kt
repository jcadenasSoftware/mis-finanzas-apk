package com.jcadenas.xpendz.ui.pdf

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.jcadenas.xpendz.R
import com.jcadenas.xpendz.data.local.dao.RootCategorySpentTotal
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
        val categoryId: String,
        val categoryName: String,
        val rootCategoryId: String,
        val rootCategoryName: String,
        val limitCents: Long,
        val spentCents: Long
    )

    data class LoanLine(
        val counterpartyName: String,
        val principalCents: Long,
        val paidCents: Long,
        val type: String // "LENT" or "BORROWED"
    ) {
        val remainingCents: Long get() = principalCents - paidCents
        val progress: Float get() = if (principalCents > 0) paidCents.toFloat() / principalCents.toFloat() else 0f
    }

    data class GoalLine(
        val name: String,
        val targetCents: Long,
        val currentCents: Long,
        val targetDateEpochSec: Long
    ) {
        val remainingCents: Long get() = targetCents - currentCents
        val progress: Float get() = if (targetCents > 0) currentCents.toFloat() / targetCents.toFloat() else 0f
    }

    fun generate(
        context: Context,
        monthKey: String,
        incomeCents: Long,
        expenseCents: Long,
        incomeHierarchy: List<com.jcadenas.xpendz.data.local.dao.HierarchyCategoryTotal>,
        expenseHierarchy: List<com.jcadenas.xpendz.data.local.dao.HierarchyCategoryTotal>,
        budgetLines: List<BudgetLine>,
        loanLines: List<LoanLine>,
        goalLines: List<GoalLine>,
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
            drawText("Xpendz · Reporte confidencial", MARGIN, fy, paint(COLOR_TEXT_MUTED, 7.5f))
            drawText("Pág. ${pageIndex + 1}", PAGE_WIDTH - MARGIN, fy, paint(COLOR_TEXT_MUTED, 7.5f, align = Paint.Align.RIGHT))
        }

        fun Canvas.contHeader() {
            drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 36f, fill(COLOR_PRIMARY))
            drawLogo(canvas, context, MARGIN, 10f, 16f)
            drawText("Xpendz · Resumen Mensual · $monthLabel", MARGIN + 24f, 24f, paint(COLOR_WHITE, 9f))
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
        fun groupByRoot(rows: List<com.jcadenas.xpendz.data.local.dao.HierarchyCategoryTotal>)
            : LinkedHashMap<String, Pair<String, List<com.jcadenas.xpendz.data.local.dao.HierarchyCategoryTotal>>> {
            val map = LinkedHashMap<String, Pair<String, MutableList<com.jcadenas.xpendz.data.local.dao.HierarchyCategoryTotal>>>()
            for (r in rows) {
                map.getOrPut(r.rootCategoryId) { r.rootCategoryName to mutableListOf() }
                    .second.add(r)
            }
            // Sort roots by total desc
            val sorted = map.entries.sortedByDescending { e -> e.value.second.sumOf { it.totalCents } }
            return sorted.associateTo(LinkedHashMap()) { it.key to (it.value.first to it.value.second as List<com.jcadenas.xpendz.data.local.dao.HierarchyCategoryTotal>) }
        }

        val incomeGroups  = groupByRoot(incomeHierarchy)
        val expenseGroups = groupByRoot(expenseHierarchy)

        // ── Row heights ────────────────────────────────────────────────
        val rootRowH = 22f   // category (root) header row
        val subRowH  = 18f   // subcategory row
        val budRowH  = 26f   // budget row

        // Helper to draw one hierarchy section
        fun drawHierarchySection(
            groups: LinkedHashMap<String, Pair<String, List<com.jcadenas.xpendz.data.local.dao.HierarchyCategoryTotal>>>,
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
        
        drawLogo(canvas, context, MARGIN, 14f, 28f)
        
        // App name
        canvas.drawText(
            "Xpendz",
            MARGIN + 38f,
            38f,
            paint(COLOR_WHITE, 18f, bold = true)
        )
        
        // Report title
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

            // Group by root category
            val budgetGroups = budgetLines.groupBy { it.rootCategoryId }
            for ((_, group) in budgetGroups) {
                val rootName = group.first().rootCategoryName
                val rootLimit = group.sumOf { it.limitCents }
                val rootSpent = group.sumOf { it.spentCents }
                val rootPct = if (rootLimit > 0) (rootSpent * 100 / rootLimit).toInt() else 0
                val rootColor = when { rootPct >= 100 -> COLOR_EXPENSE; rootPct >= 80 -> COLOR_WARN; else -> COLOR_INCOME }

                // Root row
                checkNewPage(rootRowH + subRowH * group.size)
                canvas.drawRect(MARGIN, y, MARGIN + CONTENT_WIDTH, y + rootRowH, fill(COLOR_EXPENSE_BG))
                canvas.drawRect(MARGIN, y, MARGIN + 4f, y + rootRowH, fill(COLOR_EXPENSE))
                canvas.drawText(rootName.take(30), MARGIN + 10f, y + rootRowH * 0.72f, paint(COLOR_TEXT, 8.5f, bold = true))
                canvas.drawText(fmt(rootLimit), c1, y + rootRowH * 0.72f, paint(COLOR_TEXT_MUTED, 8.5f))
                canvas.drawText(fmt(rootSpent), c2, y + rootRowH * 0.72f, paint(rootColor, 8.5f, bold = true))
                canvas.drawText("$rootPct%", c3, y + rootRowH * 0.72f, paint(rootColor, 8.5f, bold = true))
                canvas.drawText(
                    when { rootPct >= 100 -> "Excedido"; rootPct >= 80 -> "Al límite"; else -> "OK" },
                    c4, y + rootRowH * 0.72f, paint(rootColor, 8f, bold = true, align = Paint.Align.RIGHT)
                )
                y += rootRowH

                // Subcategory rows
                group.forEach { line ->
                    checkNewPage(subRowH + 1f)
                    val pct = if (line.limitCents > 0) (line.spentCents * 100 / line.limitCents).toInt() else 0
                    val rc = when { pct >= 100 -> COLOR_EXPENSE; pct >= 80 -> COLOR_WARN; else -> COLOR_INCOME }
                    val rowBg = COLOR_WHITE

                    canvas.drawRect(MARGIN, y, MARGIN + CONTENT_WIDTH, y + subRowH, fill(rowBg))
                    canvas.drawText("  ${line.categoryName.take(28)}", c0, y + subRowH * 0.64f, paint(COLOR_TEXT, 7.5f))
                    canvas.drawText(fmt(line.limitCents), c1, y + subRowH * 0.64f, paint(COLOR_TEXT_MUTED, 7.5f))
                    canvas.drawText(fmt(line.spentCents), c2, y + subRowH * 0.64f, paint(rc, 7.5f, bold = true))
                    canvas.drawText("$pct%", c3, y + subRowH * 0.64f, paint(rc, 7.5f, bold = true))

                    // Mini bar
                    val mbW = CONTENT_WIDTH * 0.13f
                    val mbX = c4 - mbW
                    val mbFW = (mbW * (line.spentCents.toFloat() / line.limitCents.toFloat().coerceAtLeast(1f))).coerceIn(0f, mbW)
                    canvas.drawRoundRect(RectF(mbX, y + subRowH * 0.68f, mbX + mbW, y + subRowH * 0.86f), 2f, 2f, fill(COLOR_SURFACE))
                    canvas.drawRoundRect(RectF(mbX, y + subRowH * 0.68f, mbX + mbFW, y + subRowH * 0.86f), 2f, 2f, fill(rc))

                    canvas.drawText(
                        when { pct >= 100 -> "Excedido"; pct >= 80 -> "Al límite"; else -> "OK" },
                        c4, y + subRowH * 0.64f, paint(rc, 7f, bold = true, align = Paint.Align.RIGHT)
                    )
                    canvas.drawLine(MARGIN, y + subRowH, MARGIN + CONTENT_WIDTH, y + subRowH, stroke(COLOR_BORDER, 0.3f))
                    y += subRowH
                }
            }
        }

        // ── 5. PRÉSTAMOS ACTIVOS ────────────────────────────────────────
        checkNewPage(60f)
        canvas.drawRect(MARGIN, y, MARGIN + CONTENT_WIDTH, y + 22f, fill(COLOR_PRIMARY))
        canvas.drawText("PRÉSTAMOS ACTIVOS", MARGIN + CONTENT_WIDTH / 2, y + 15f, paint(COLOR_WHITE, 9f, bold = true, align = Paint.Align.CENTER))
        y += 26f

        if (loanLines.isEmpty()) {
            canvas.drawText("No hay préstamos activos.", MARGIN + CONTENT_WIDTH / 2, y + 14f, paint(COLOR_TEXT_MUTED, 8.5f, align = Paint.Align.CENTER))
            y += 24f
        } else {
            // Group by type
            val lentLoans = loanLines.filter { it.type == "LENT" }
            val borrowedLoans = loanLines.filter { it.type == "BORROWED" }

            if (lentLoans.isNotEmpty()) {
                checkNewPage(20f)
                canvas.drawText("Préstamos otorgados", MARGIN, y + 12f, paint(COLOR_PRIMARY, 8.5f, bold = true))
                y += 16f

                lentLoans.forEach { loan ->
                    checkNewPage(24f)
                    val rowBg = COLOR_WHITE
                    canvas.drawRect(MARGIN, y, MARGIN + CONTENT_WIDTH, y + 20f, fill(rowBg))
                    canvas.drawRect(MARGIN, y, MARGIN + 3f, y + 20f, fill(COLOR_PRIMARY))

                    canvas.drawText(loan.counterpartyName.take(25), MARGIN + 10f, y + 14f, paint(COLOR_TEXT, 7.5f))
                    canvas.drawText(fmt(loan.paidCents), MARGIN + CONTENT_WIDTH * 0.5f, y + 14f, paint(COLOR_INCOME, 7.5f, bold = true))
                    canvas.drawText(fmt(loan.remainingCents), MARGIN + CONTENT_WIDTH * 0.75f, y + 14f, paint(COLOR_WARN, 7.5f, bold = true))
                    canvas.drawText("${(loan.progress * 100).toInt()}%", MARGIN + CONTENT_WIDTH - 6f, y + 14f, paint(COLOR_TEXT_MUTED, 7.5f, align = Paint.Align.RIGHT))

                    // Progress bar
                    val barW = CONTENT_WIDTH * 0.2f
                    val barX = MARGIN + CONTENT_WIDTH - barW - 10f
                    canvas.drawRoundRect(RectF(barX, y + 16f, barX + barW, y + 18f), 2f, 2f, fill(COLOR_SURFACE))
                    canvas.drawRoundRect(RectF(barX, y + 16f, barX + barW * loan.progress, y + 18f), 2f, 2f, fill(COLOR_INCOME))

                    canvas.drawLine(MARGIN, y + 20f, MARGIN + CONTENT_WIDTH, y + 20f, stroke(COLOR_BORDER, 0.3f))
                    y += 20f
                }
                y += 6f
            }

            if (borrowedLoans.isNotEmpty()) {
                checkNewPage(20f)
                canvas.drawText("Préstamos recibidos", MARGIN, y + 12f, paint(COLOR_PRIMARY, 8.5f, bold = true))
                y += 16f

                borrowedLoans.forEach { loan ->
                    checkNewPage(24f)
                    val rowBg = COLOR_WHITE
                    canvas.drawRect(MARGIN, y, MARGIN + CONTENT_WIDTH, y + 20f, fill(rowBg))
                    canvas.drawRect(MARGIN, y, MARGIN + 3f, y + 20f, fill(COLOR_EXPENSE))

                    canvas.drawText(loan.counterpartyName.take(25), MARGIN + 10f, y + 14f, paint(COLOR_TEXT, 7.5f))
                    canvas.drawText(fmt(loan.paidCents), MARGIN + CONTENT_WIDTH * 0.5f, y + 14f, paint(COLOR_EXPENSE, 7.5f, bold = true))
                    canvas.drawText(fmt(loan.remainingCents), MARGIN + CONTENT_WIDTH * 0.75f, y + 14f, paint(COLOR_WARN, 7.5f, bold = true))
                    canvas.drawText("${(loan.progress * 100).toInt()}%", MARGIN + CONTENT_WIDTH - 6f, y + 14f, paint(COLOR_TEXT_MUTED, 7.5f, align = Paint.Align.RIGHT))

                    // Progress bar
                    val barW = CONTENT_WIDTH * 0.2f
                    val barX = MARGIN + CONTENT_WIDTH - barW - 10f
                    canvas.drawRoundRect(RectF(barX, y + 16f, barX + barW, y + 18f), 2f, 2f, fill(COLOR_SURFACE))
                    canvas.drawRoundRect(RectF(barX, y + 16f, barX + barW * loan.progress, y + 18f), 2f, 2f, fill(COLOR_EXPENSE))

                    canvas.drawLine(MARGIN, y + 20f, MARGIN + CONTENT_WIDTH, y + 20f, stroke(COLOR_BORDER, 0.3f))
                    y += 20f
                }
            }
        }

        // ── 6. METAS ACTIVAS ───────────────────────────────────────────
        checkNewPage(60f)
        canvas.drawRect(MARGIN, y, MARGIN + CONTENT_WIDTH, y + 22f, fill(COLOR_PRIMARY))
        canvas.drawText("METAS ACTIVAS", MARGIN + CONTENT_WIDTH / 2, y + 15f, paint(COLOR_WHITE, 9f, bold = true, align = Paint.Align.CENTER))
        y += 26f

        if (goalLines.isEmpty()) {
            canvas.drawText("No hay metas activas.", MARGIN + CONTENT_WIDTH / 2, y + 14f, paint(COLOR_TEXT_MUTED, 8.5f, align = Paint.Align.CENTER))
            y += 24f
        } else {
            goalLines.forEach { goal ->
                checkNewPage(24f)
                val rowBg = COLOR_WHITE
                canvas.drawRect(MARGIN, y, MARGIN + CONTENT_WIDTH, y + 20f, fill(rowBg))
                canvas.drawRect(MARGIN, y, MARGIN + 3f, y + 20f, fill(COLOR_PRIMARY))

                canvas.drawText(goal.name.take(25), MARGIN + 10f, y + 14f, paint(COLOR_TEXT, 7.5f))
                canvas.drawText(fmt(goal.currentCents), MARGIN + CONTENT_WIDTH * 0.5f, y + 14f, paint(COLOR_INCOME, 7.5f, bold = true))
                canvas.drawText(fmt(goal.remainingCents), MARGIN + CONTENT_WIDTH * 0.75f, y + 14f, paint(COLOR_WARN, 7.5f, bold = true))
                canvas.drawText("${(goal.progress * 100).toInt()}%", MARGIN + CONTENT_WIDTH - 6f, y + 14f, paint(COLOR_TEXT_MUTED, 7.5f, align = Paint.Align.RIGHT))

                // Progress bar
                val barW = CONTENT_WIDTH * 0.2f
                val barX = MARGIN + CONTENT_WIDTH - barW - 10f
                canvas.drawRoundRect(RectF(barX, y + 16f, barX + barW, y + 18f), 2f, 2f, fill(COLOR_SURFACE))
                canvas.drawRoundRect(RectF(barX, y + 16f, barX + barW * goal.progress, y + 18f), 2f, 2f, fill(COLOR_PRIMARY))

                canvas.drawLine(MARGIN, y + 20f, MARGIN + CONTENT_WIDTH, y + 20f, stroke(COLOR_BORDER, 0.3f))
                y += 20f
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

    private fun drawLogo(canvas: Canvas, context: Context, x: Float, y: Float, size: Float) {
        try {
            val logo = BitmapFactory.decodeResource(context.resources, R.drawable.ic_launcher) ?: return
            canvas.drawBitmap(logo, null, RectF(x, y, x + size, y + size), null)
        } catch (_: Exception) {
            // Ignore logo rendering issues and continue generating the PDF.
        }
    }
}
