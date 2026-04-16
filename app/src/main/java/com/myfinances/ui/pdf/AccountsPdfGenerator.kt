package com.myfinances.ui.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.myfinances.data.local.entity.AccountEntity
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
        "SAVINGS"   to Color.parseColor("#16A34A"),
        "CHECKING"  to Color.parseColor("#1A56DB"),
        "CASH"      to Color.parseColor("#D97706"),
        "CREDIT"    to Color.parseColor("#DC2626"),
        "INVESTMENT" to Color.parseColor("#7C3AED"),
        "OTHER"     to Color.parseColor("#64748B")
    )
    private val typeBgColors = mapOf(
        "SAVINGS"   to Color.parseColor("#F0FDF4"),
        "CHECKING"  to Color.parseColor("#EFF6FF"),
        "CASH"      to Color.parseColor("#FFFBEB"),
        "CREDIT"    to Color.parseColor("#FEF2F2"),
        "INVESTMENT" to Color.parseColor("#F5F3FF"),
        "OTHER"     to Color.parseColor("#F8FAFC")
    )
    private val typeLabels = mapOf(
        "SAVINGS"    to "Ahorros",
        "CHECKING"   to "Corriente",
        "CASH"       to "Efectivo",
        "CREDIT"     to "Crédito",
        "INVESTMENT" to "Inversión",
        "OTHER"      to "Otra"
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

        val totalBalance = accounts.sumOf { it.balanceCents }
        val positiveAccounts = accounts.count { it.balanceCents >= 0 }

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
        val cardHeight = 74f
        val cardSpacing = 10f
        val headerHeight = 240f
        val cardsPerFirstPage = ((PAGE_HEIGHT - headerHeight - MARGIN) / (cardHeight + cardSpacing)).toInt()
        val cardsPerNextPage = ((PAGE_HEIGHT - MARGIN * 2 - 56f) / (cardHeight + cardSpacing)).toInt()

        val pages = mutableListOf<List<AccountWithBalance>>()
        if (accounts.isEmpty()) {
            pages.add(emptyList())
        } else {
            pages.add(accounts.take(cardsPerFirstPage))
            var offset = cardsPerFirstPage
            while (offset < accounts.size) {
                pages.add(accounts.subList(offset, minOf(offset + cardsPerNextPage, accounts.size)))
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

                // Decorative circle top-right
                canvas.drawCircle(PAGE_WIDTH.toFloat() + 10f, -10f, 90f, fill(Color.parseColor("#1E40AF")))
                canvas.drawCircle(PAGE_WIDTH - 60f, 120f, 50f, fill(Color.parseColor("#1E40AF")))

                canvas.drawText("Mis Finanzas", MARGIN, 38f, paint(COLOR_WHITE, 20f, bold = true))
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

                // ── SUMMARY BAR ────────────────────────────────────────
                // Total balance card (full width)
                val totalCardRect = RectF(MARGIN, y, MARGIN + CONTENT_WIDTH, y + 60f)
                canvas.drawRoundRect(RectF(MARGIN + 1f, y + 2f, MARGIN + CONTENT_WIDTH + 1f, y + 62f), 10f, 10f, fill(COLOR_BORDER))
                canvas.drawRoundRect(totalCardRect, 10f, 10f, fill(COLOR_WHITE))
                canvas.drawRoundRect(totalCardRect, 10f, 10f, stroke(COLOR_BORDER))
                // Left color accent bar
                canvas.drawRoundRect(RectF(MARGIN, y, MARGIN + 4f, y + 60f), 4f, 4f, fill(COLOR_PRIMARY))

                canvas.drawText("Balance total", MARGIN + 16f, y + 22f, paint(COLOR_TEXT_MUTED, 9f))
                val totalText = currencyFormat.format(totalBalance / 100.0)
                val totalSize = when { totalText.length > 16 -> 14f; totalText.length > 12 -> 16f; else -> 20f }
                canvas.drawText(
                    totalText, MARGIN + 16f, y + 48f,
                    paint(if (totalBalance >= 0) COLOR_INCOME else Color.parseColor("#DC2626"), totalSize, bold = true)
                )
                // Accounts count badge
                val badgeText = "${accounts.size} cuentas"
                canvas.drawRoundRect(
                    RectF(PAGE_WIDTH - MARGIN - 90f, y + 18f, PAGE_WIDTH - MARGIN - 10f, y + 38f),
                    10f, 10f, fill(COLOR_ACCENT_BLUE)
                )
                canvas.drawRoundRect(
                    RectF(PAGE_WIDTH - MARGIN - 90f, y + 18f, PAGE_WIDTH - MARGIN - 10f, y + 38f),
                    10f, 10f, stroke(COLOR_ACCENT_BORDER)
                )
                canvas.drawText(
                    badgeText,
                    PAGE_WIDTH - MARGIN - 50f, y + 33f,
                    paint(COLOR_PRIMARY, 9f, bold = true, align = Paint.Align.CENTER)
                )

                y += 76f

                // ── SECTION TITLE ──────────────────────────────────────
                canvas.drawText("Detalle por cuenta", MARGIN, y + 14f, paint(COLOR_TEXT, 11f, bold = true))
                y += 28f

            } else {
                // ── CONTINUATION HEADER ────────────────────────────────
                canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 40f, fill(COLOR_PRIMARY))
                canvas.drawText(
                    "Mis Finanzas · Balance de Cuentas",
                    MARGIN, 26f,
                    paint(COLOR_WHITE, 10f)
                )
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
                    val cardRect = RectF(cx, y, cx + cw, y + cardHeight)

                    // Shadow
                    canvas.drawRoundRect(
                        RectF(cx + 1f, y + 2f, cx + cw + 1f, y + cardHeight + 2f),
                        10f, 10f, fill(COLOR_BORDER)
                    )
                    // Card background
                    canvas.drawRoundRect(cardRect, 10f, 10f, fill(COLOR_WHITE))
                    canvas.drawRoundRect(cardRect, 10f, 10f, stroke(COLOR_BORDER, 0.8f))

                    // Account type color accent (left bar)
                    val accentColor = try {
                        account.colorHex?.let { Color.parseColor(it) }
                            ?: typeColors[account.type.uppercase()]
                            ?: COLOR_TEXT_MUTED
                    } catch (_: Exception) {
                        typeColors[account.type.uppercase()] ?: COLOR_TEXT_MUTED
                    }
                    canvas.drawRoundRect(RectF(cx, y, cx + 5f, y + cardHeight), 4f, 4f, fill(accentColor))

                    // Account icon circle
                    val iconCx = cx + 28f
                    val iconCy = y + cardHeight / 2
                    canvas.drawCircle(iconCx, iconCy, 18f, fill(Color.argb(25, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))))
                    // First letter of account name
                    val initial = account.name.take(1).uppercase()
                    canvas.drawText(
                        initial, iconCx, iconCy + 5f,
                        paint(accentColor, 14f, bold = true, align = Paint.Align.CENTER)
                    )

                    // Account name
                    canvas.drawText(
                        account.name,
                        cx + 54f, y + 26f,
                        paint(COLOR_TEXT, 11f, bold = true)
                    )

                    // Type pill
                    val typeLabel = typeLabels[account.type.uppercase()] ?: account.type
                    val typeBg = typeBgColors[account.type.uppercase()] ?: COLOR_SURFACE
                    val pillW = typeLabel.length * 5.8f + 14f
                    canvas.drawRoundRect(
                        RectF(cx + 54f, y + 34f, cx + 54f + pillW, y + 50f),
                        8f, 8f, fill(typeBg)
                    )
                    canvas.drawText(
                        typeLabel,
                        cx + 54f + pillW / 2, y + 46f,
                        paint(accentColor, 7.5f, bold = true, align = Paint.Align.CENTER)
                    )

                    // Currency chip
                    canvas.drawRoundRect(
                        RectF(cx + 54f + pillW + 8f, y + 34f, cx + 54f + pillW + 8f + 36f, y + 50f),
                        8f, 8f, fill(COLOR_ACCENT_BLUE)
                    )
                    canvas.drawText(
                        account.currency,
                        cx + 54f + pillW + 8f + 18f, y + 46f,
                        paint(COLOR_PRIMARY, 7.5f, bold = true, align = Paint.Align.CENTER)
                    )

                    // Balance (right side)
                    val balanceText = currencyFormat.format(balanceCents / 100.0)
                    val balanceColor = if (balanceCents >= 0) COLOR_INCOME else Color.parseColor("#DC2626")
                    val balSize = when { balanceText.length > 16 -> 9f; balanceText.length > 12 -> 10f; else -> 12f }
                    canvas.drawText(
                        balanceText,
                        cx + cw - 12f, y + 34f,
                        paint(balanceColor, balSize, bold = true, align = Paint.Align.RIGHT)
                    )
                    canvas.drawText(
                        "Saldo actual",
                        cx + cw - 12f, y + 50f,
                        paint(COLOR_TEXT_MUTED, 7.5f, align = Paint.Align.RIGHT)
                    )

                    y += cardHeight + cardSpacing
                }
            }

            // ── FOOTER ─────────────────────────────────────────────────
            val footerY = PAGE_HEIGHT - 24f
            canvas.drawLine(MARGIN, footerY - 10f, MARGIN + CONTENT_WIDTH, footerY - 10f, stroke(COLOR_BORDER))
            canvas.drawText(
                "Mis Finanzas · Reporte confidencial",
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
}
