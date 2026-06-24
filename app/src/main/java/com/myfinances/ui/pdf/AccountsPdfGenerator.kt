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
import com.jcadenas.xpendz.data.local.entity.AccountEntity
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AccountsPdfGenerator {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val CONTENT_WIDTH = PAGE_WIDTH - MARGIN * 2

    private val COLOR_PRIMARY     = Color.parseColor("#1A56DB")
    private val COLOR_PRIMARY_DARK = Color.parseColor("#1E40AF")
    private val COLOR_INCOME      = Color.parseColor("#16A34A")
    private val COLOR_SURFACE     = Color.parseColor("#F8FAFC")
    private val COLOR_BORDER      = Color.parseColor("#E2E8F0")
    private val COLOR_TEXT        = Color.parseColor("#0F172A")
    private val COLOR_TEXT_MUTED  = Color.parseColor("#64748B")
    private val COLOR_WHITE       = Color.WHITE
    private val COLOR_ACCENT_BLUE = Color.parseColor("#EFF6FF")
    private val COLOR_ACCENT_BORDER = Color.parseColor("#BFDBFE")

    // Account type colors
    private val typeColors = mapOf(
        "BANK"      to Color.parseColor("#1A56DB"),
        "CASH"      to Color.parseColor("#D97706"),
        "SAVINGS"   to Color.parseColor("#16A34A"),
        "VIRTUAL_WALLET" to Color.parseColor("#2563EB"),
        "DIGITAL_ACCOUNT" to Color.parseColor("#0EA5E9"),

        // Legacy aliases
        "CHECKING"  to Color.parseColor("#1A56DB"),
        "CREDIT"    to Color.parseColor("#1A56DB"),
        "INVESTMENT" to Color.parseColor("#16A34A"),
        "OTHER"     to Color.parseColor("#64748B")
    )
    private val typeBgColors = mapOf(
        "BANK"      to Color.parseColor("#EFF6FF"),
        "CASH"      to Color.parseColor("#FFFBEB"),
        "SAVINGS"   to Color.parseColor("#F0FDF4"),
        "VIRTUAL_WALLET" to Color.parseColor("#EFF6FF"),
        "DIGITAL_ACCOUNT" to Color.parseColor("#E0F2FE"),

        // Legacy aliases
        "CHECKING"  to Color.parseColor("#EFF6FF"),
        "CREDIT"    to Color.parseColor("#EFF6FF"),
        "INVESTMENT" to Color.parseColor("#F0FDF4"),
        "OTHER"     to Color.parseColor("#F8FAFC")
    )
    private val typeLabels = mapOf(
        "BANK"           to "Banco",
        "CASH"           to "Efectivo",
        "SAVINGS"        to "Ahorro",
        "VIRTUAL_WALLET" to "Billetera virtual",
        "DIGITAL_ACCOUNT" to "Cuenta digital",

        // Legacy aliases
        "CHECKING"   to "Banco",
        "CREDIT"     to "Banco",
        "INVESTMENT" to "Ahorro",
        "OTHER"      to "Banco"
    )

    data class AccountWithBalance(
        val account: AccountEntity,
        val balanceCents: Long
    )

    fun generate(
        context: Context,
        accounts: List<AccountWithBalance>,
        userName: String
    ): File {
        val currencyFormat = NumberFormat.getCurrencyInstance(Locale("es", "CO"))
        val generatedAt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "CO")).format(Date())

        // Sort accounts by balance descending (highest to lowest)
        val sortedAccounts = accounts.sortedByDescending { it.balanceCents }

        val totalBalance = sortedAccounts.sumOf { it.balanceCents }
        val positiveAccounts = sortedAccounts.count { it.balanceCents >= 0 }

        val document = PdfDocument()

        // ── Paint helpers ──────────────────────────────────────────────
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

        fun fill(color: Int) = Paint().apply {
            this.color = color
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        fun stroke(color: Int, width: Float = 1f) = Paint().apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = width
            isAntiAlias = true
        }

        // ── Pagination ─────────────────────────────────────────────────
        val cardHeight = 48f
        val cardSpacing = 6f
        val headerHeight = 240f
        val cardsPerFirstPage = ((PAGE_HEIGHT - headerHeight - MARGIN) / (cardHeight + cardSpacing)).toInt()
        val cardsPerNextPage = ((PAGE_HEIGHT - MARGIN * 2 - 56f) / (cardHeight + cardSpacing)).toInt()

        val pages = mutableListOf<List<AccountWithBalance>>()
        if (sortedAccounts.isEmpty()) {
            pages.add(emptyList())
        } else {
            pages.add(sortedAccounts.take(cardsPerFirstPage))
            var offset = cardsPerFirstPage
            while (offset < sortedAccounts.size) {
                pages.add(sortedAccounts.subList(offset, minOf(offset + cardsPerNextPage, sortedAccounts.size)))
                offset += cardsPerNextPage
            }
        }
        val totalPages = pages.size

        pages.forEachIndexed { pageIndex, pageAccounts ->
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex + 1).create()
            val page = document.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            var y = 0f

            if (pageIndex == 0) {
                // ── HEADER BANNER ──────────────────────────────────────
                canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 110f, fill(COLOR_PRIMARY))

                drawLogo(canvas, context, MARGIN, 14f, 28f)

                // App name
                canvas.drawText(
                    "Xpendz",
                    MARGIN + 38f,
                    38f,
                    paint(COLOR_WHITE, 20f, bold = true)
                )

                // Report title
                canvas.drawText(
                    "Balance de Cuentas",
                    MARGIN, 62f,
                    paint(Color.parseColor("#BFDBFE"), 13f)
                )
                canvas.drawText(
                    "Generado: $generatedAt",
                    PAGE_WIDTH - MARGIN, 62f,
                    paint(Color.parseColor("#BFDBFE"), 9f, align = Paint.Align.RIGHT)
                )
                canvas.drawText(
                    "Usuario: $userName",
                    PAGE_WIDTH - MARGIN, 38f,
                    paint(COLOR_WHITE, 10f, align = Paint.Align.RIGHT)
                )
                canvas.drawText(
                    "Estado actual de todas las cuentas",
                    MARGIN, 82f,
                    paint(COLOR_WHITE, 9f)
                )

                y = 126f

                // ── SUMMARY CARDS ────────────────────────────────────────
                val gap = 8f
                val cardW = (CONTENT_WIDTH - (gap * 2f)) / 3f
                val cardH = 58f
                val radius = 8f
                
                // Balance total card
                val totalCardX = MARGIN
                val totalColor = if (totalBalance >= 0) COLOR_INCOME else Color.parseColor("#DC2626")
                canvas.drawRoundRect(RectF(totalCardX + 1f, y + 2f, totalCardX + cardW, y + cardH + 2f), radius, radius, fill(COLOR_BORDER))
                canvas.drawRoundRect(RectF(totalCardX, y, totalCardX + cardW, y + cardH), radius, radius, fill(COLOR_WHITE))
                canvas.drawRoundRect(RectF(totalCardX, y, totalCardX + cardW, y + cardH), radius, radius, stroke(COLOR_BORDER, 0.6f))
                canvas.drawRect(totalCardX, y, totalCardX + cardW, y + 4f, fill(totalColor))
                canvas.drawText("Balance total", totalCardX + 10f, y + 22f, paint(COLOR_TEXT_MUTED, 8.5f))
                val totalText = currencyFormat.format(totalBalance / 100.0)
                val totalSize = when { totalText.length > 16 -> 10f; totalText.length > 12 -> 11f; else -> 12.5f }
                canvas.drawText(totalText, totalCardX + 10f, y + 43f, paint(totalColor, totalSize, bold = true))
                
                // Cuentas activas card
                val accountsCardX = MARGIN + cardW + gap
                canvas.drawRoundRect(RectF(accountsCardX + 1f, y + 2f, accountsCardX + cardW, y + cardH + 2f), radius, radius, fill(COLOR_BORDER))
                canvas.drawRoundRect(RectF(accountsCardX, y, accountsCardX + cardW, y + cardH), radius, radius, fill(COLOR_WHITE))
                canvas.drawRoundRect(RectF(accountsCardX, y, accountsCardX + cardW, y + cardH), radius, radius, stroke(COLOR_BORDER, 0.6f))
                canvas.drawRect(accountsCardX, y, accountsCardX + cardW, y + 4f, fill(COLOR_PRIMARY))
                canvas.drawText("Cuentas activas", accountsCardX + 10f, y + 22f, paint(COLOR_TEXT_MUTED, 8.5f))
                canvas.drawText(sortedAccounts.size.toString(), accountsCardX + 10f, y + 43f, paint(COLOR_PRIMARY, 12.5f, bold = true))
                
                // Saldo positivo card
                val positiveCardX = MARGIN + (cardW + gap) * 2
                canvas.drawRoundRect(RectF(positiveCardX + 1f, y + 2f, positiveCardX + cardW, y + cardH + 2f), radius, radius, fill(COLOR_BORDER))
                canvas.drawRoundRect(RectF(positiveCardX, y, positiveCardX + cardW, y + cardH), radius, radius, fill(COLOR_WHITE))
                canvas.drawRoundRect(RectF(positiveCardX, y, positiveCardX + cardW, y + cardH), radius, radius, stroke(COLOR_BORDER, 0.6f))
                canvas.drawRect(positiveCardX, y, positiveCardX + cardW, y + 4f, fill(COLOR_INCOME))
                canvas.drawText("Saldo positivo", positiveCardX + 10f, y + 22f, paint(COLOR_TEXT_MUTED, 8.5f))
                canvas.drawText(positiveAccounts.toString(), positiveCardX + 10f, y + 43f, paint(COLOR_INCOME, 12.5f, bold = true))
                
                y += 76f

                // ── SECTION TITLE ──────────────────────────────────────
                canvas.drawText("Detalle por cuenta", MARGIN, y + 14f, paint(COLOR_TEXT, 11f, bold = true))
                y += 28f

            } else {
                // ── CONTINUATION HEADER ────────────────────────────────
                canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 40f, fill(COLOR_PRIMARY))
                
                drawLogo(canvas, context, MARGIN, 11f, 16f)
                canvas.drawText("Xpendz · Balance de Cuentas", MARGIN + 24f, 26f, paint(COLOR_WHITE, 10f))
                canvas.drawText(
                    "Pág. ${pageIndex + 1} / $totalPages",
                    PAGE_WIDTH - MARGIN, 26f,
                    paint(Color.parseColor("#BFDBFE"), 9f, align = Paint.Align.RIGHT)
                )
                y = 56f
            }

            // ── ACCOUNT CARDS ──────────────────────────────────────────
            if (pageAccounts.isEmpty() && pageIndex == 0) {
                canvas.drawText(
                    "No hay cuentas registradas.",
                    MARGIN + CONTENT_WIDTH / 2, y + 30f,
                    paint(COLOR_TEXT_MUTED, 10f, align = Paint.Align.CENTER)
                )
            } else {
                pageAccounts.forEach { (account, balanceCents) ->
                    val cx = MARGIN
                    val cw = CONTENT_WIDTH
                    val h = 48f
                    val cardRect = RectF(cx, y, cx + cw, y + h)
                    val radius = 6f

                    // Rounded card background with subtle border
                    canvas.drawRoundRect(cardRect, radius, radius, fill(COLOR_WHITE))
                    canvas.drawRoundRect(cardRect, radius, radius, stroke(COLOR_BORDER, 0.4f))

                    // Account type color accent (left bar)
                    val accentColor = try {
                        account.colorHex?.let { Color.parseColor(it) }
                            ?: typeColors[account.type.uppercase()]
                            ?: COLOR_TEXT_MUTED
                    } catch (_: Exception) {
                        typeColors[account.type.uppercase()] ?: COLOR_TEXT_MUTED
                    }
                    canvas.drawRoundRect(RectF(cx, y, cx + 4f, y + h), radius, radius, fill(accentColor))

                    // Avatar background
                    canvas.drawRoundRect(RectF(cx + 16f, y + 12f, cx + 38f, y + 34f), 5f, 5f, fill(Color.argb(46, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))))
                    // First letter of account name
                    val initial = account.name.take(1).uppercase()
                    canvas.drawText(
                        initial, cx + 27f, y + 20f,
                        paint(accentColor, 10f, bold = true, align = Paint.Align.CENTER)
                    )

                    // Account name and type
                    canvas.drawText(
                        account.name,
                        cx + 48f, y + 26f,
                        paint(COLOR_TEXT, 9.5f, bold = true)
                    )
                    val typeLabel = typeLabels[account.type.uppercase()] ?: account.type
                    canvas.drawText(
                        typeLabel,
                        cx + 48f, y + 14f,
                        paint(COLOR_TEXT_MUTED, 7.5f)
                    )

                    // Balance and percentage
                    val balanceText = currencyFormat.format(balanceCents / 100.0)
                    val balanceColor = if (balanceCents >= 0) COLOR_INCOME else Color.parseColor("#DC2626")
                    canvas.drawText(
                        balanceText,
                        cx + cw - 10f, y + 10f,
                        paint(balanceColor, 9.5f, bold = true, align = Paint.Align.RIGHT)
                    )

                    val pct = if (totalBalance == 0L) 0f else Math.abs(balanceCents).toFloat() / Math.max(1L, Math.abs(totalBalance)).toFloat()
                    val pctText = String.format(Locale("es", "CO"), "%.1f%%", pct * 100)
                    canvas.drawText(
                        pctText,
                        cx + cw - 10f, y + 22f,
                        paint(COLOR_TEXT_MUTED, 7.5f, align = Paint.Align.RIGHT)
                    )

                    // Progress bar
                    val barW = 100f
                    canvas.drawRoundRect(RectF(cx + cw - barW - 10f, y + 30f, cx + cw - 10f, y + 34f), 2f, 2f, fill(COLOR_SURFACE))
                    val fillBarW = Math.max(4f, barW * Math.min(1f, pct))
                    canvas.drawRoundRect(RectF(cx + cw - barW - 10f, y + 30f, cx + cw - barW - 10f + fillBarW, y + 34f), 2f, 2f, fill(accentColor))

                    y += h + cardSpacing
                }
            }

            // ── FOOTER ─────────────────────────────────────────────────
            val footerY = PAGE_HEIGHT - 24f
            canvas.drawLine(MARGIN, footerY - 10f, MARGIN + CONTENT_WIDTH, footerY - 10f, stroke(COLOR_BORDER))
            canvas.drawText(
                "Xpendz · Reporte confidencial",
                MARGIN, footerY,
                paint(COLOR_TEXT_MUTED, 8f)
            )
            canvas.drawText(
                "Pág. ${pageIndex + 1} / $totalPages",
                PAGE_WIDTH - MARGIN, footerY,
                paint(COLOR_TEXT_MUTED, 8f, align = Paint.Align.RIGHT)
            )

            document.finishPage(page)
        }

        val file = File(context.cacheDir, "balance_cuentas_${System.currentTimeMillis()}.pdf")
        file.outputStream().use { document.writeTo(it) }
        document.close()
        return file
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
