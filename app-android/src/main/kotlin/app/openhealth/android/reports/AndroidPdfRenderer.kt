package app.openhealth.android.reports

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.compose.ui.graphics.toArgb
import app.openhealth.core.seam.PdfDocumentSpec
import app.openhealth.core.seam.PdfParagraph
import app.openhealth.core.seam.PdfSeam
import app.openhealth.core.seam.PdfSection
import app.openhealth.core.seam.PdfTable
import app.openhealth.core.seam.PdfTextSize
import app.openhealth.ui.theme.OhReportPaper
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * The PDF seam on Android (`BUILD-ROADMAP.md` §B12; `DESIGN.md` §8.7). A4 portrait at 72
 * points per inch, white paper, black text and hairlines: reports are always light even
 * when the app is dark. Everything is laid out on the device with `android.graphics.pdf`,
 * so a report is produced offline and never touches a network or a print service.
 */
class AndroidPdfRenderer : PdfSeam {
    override fun render(spec: PdfDocumentSpec): ByteArray {
        val writer = Writer(spec)
        writer.title(spec)
        spec.sections.forEach { writer.section(it) }
        writer.finish()

        val bytes = ByteArrayOutputStream()
        writer.document.writeTo(bytes)
        writer.document.close()
        return bytes.toByteArray()
    }

    @Suppress("TooManyFunctions", "MagicNumber")
    private class Writer(private val spec: PdfDocumentSpec) {
        val document = PdfDocument()
        private val titlePaint = paint(PdfTextSize.TITLE, bold = true)
        private val headingPaint = paint(PdfTextSize.HEADING, bold = true)
        private val bodyPaint = paint(PdfTextSize.BODY)
        private val bodyBoldPaint = paint(PdfTextSize.BODY, bold = true)
        private val captionPaint = paint(PdfTextSize.CAPTION)
        private val rulePaint = Paint().apply {
            color = OhReportPaper.rule.toArgb()
            strokeWidth = 0.5f
        }
        private var pageNumber = 0
        private var page: PdfDocument.Page = newPage()
        private var canvas: Canvas = page.canvas
        private var y = MARGIN

        fun title(spec: PdfDocumentSpec) {
            y = wrapped(spec.title, titlePaint, y)
            spec.subtitle?.let {
                y += GAP_SMALL
                y = wrapped(it, captionPaint, y)
            }
            y += GAP_SMALL
            y = wrapped(spec.generatedAt, captionPaint, y)
            y += GAP
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, rulePaint)
            y += GAP
        }

        fun section(section: PdfSection) {
            section.heading?.let { heading ->
                ensure(HEADING_HEIGHT)
                y = wrapped(heading, headingPaint, y)
                y += GAP_SMALL
            }
            section.paragraphs.forEach { paragraph ->
                paragraph(paragraph)
                y += GAP_SMALL
            }
            section.table?.let { table ->
                table(table)
            }
            y += GAP
        }

        fun finish() {
            footer()
            document.finishPage(page)
        }

        private fun paragraph(paragraph: PdfParagraph) {
            val paint = when {
                paragraph.bold -> bodyBoldPaint
                paragraph.size == PdfTextSize.HEADING -> headingPaint
                paragraph.size == PdfTextSize.CAPTION -> captionPaint
                else -> bodyPaint
            }
            wrap(paragraph.text, paint, CONTENT_WIDTH).forEach { line ->
                ensure(lineHeight(paint))
                canvas.drawText(line, MARGIN, y + paint.textSize, paint)
                y += lineHeight(paint)
            }
        }

        private fun table(table: PdfTable) {
            if (table.columns.isEmpty()) return
            val weights = table.weights
                ?.takeIf { it.size == table.columns.size }
                ?: List(table.columns.size) { 1f }
            val total = weights.sum().takeIf { it > 0f } ?: 1f
            val widths = weights.map { it / total * CONTENT_WIDTH }
            row(table.columns, widths, bodyBoldPaint)
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, rulePaint)
            y += 2f
            table.rows.forEach { cells ->
                row(cells, widths, bodyPaint)
                canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, rulePaint)
                y += 2f
            }
        }

        private fun row(cells: List<String>, widths: List<Float>, paint: Paint) {
            val wrappedCells = cells.mapIndexed { index, cell ->
                wrap(
                    cell,
                    paint,
                    max(1f, (widths.getOrElse(index) { widths.last() }) - CELL_PADDING),
                )
            }
            val lines = wrappedCells.maxOfOrNull { it.size } ?: 1
            val height = lines * lineHeight(paint) + CELL_PADDING
            if (y + height > PAGE_HEIGHT - MARGIN - FOOTER_HEIGHT) {
                newPage()
            }
            var x = MARGIN
            wrappedCells.forEachIndexed { index, cellLines ->
                var cellY = y + CELL_PADDING / 2f
                cellLines.forEach { line ->
                    canvas.drawText(line, x, cellY + paint.textSize, paint)
                    cellY += lineHeight(paint)
                }
                x += widths.getOrElse(index) { 0f }
            }
            y += height
        }

        private fun wrapped(text: String, paint: Paint, startY: Float): Float {
            var cursor = startY
            wrap(text, paint, CONTENT_WIDTH).forEach { line ->
                ensure(lineHeight(paint))
                canvas.drawText(line, MARGIN, cursor + paint.textSize, paint)
                cursor += lineHeight(paint)
            }
            return cursor
        }

        private fun ensure(height: Float) {
            if (y + height > PAGE_HEIGHT - MARGIN - FOOTER_HEIGHT) newPage()
        }

        private fun newPage(): PdfDocument.Page {
            if (pageNumber > 0) {
                footer()
                document.finishPage(page)
            }
            pageNumber += 1
            val created = document.startPage(
                PdfDocument.PageInfo.Builder(
                    PAGE_WIDTH.toInt(),
                    PAGE_HEIGHT.toInt(),
                    pageNumber,
                ).create(),
            )
            page = created
            canvas = created.canvas
            y = MARGIN
            return created
        }

        private fun footer() {
            val lines = wrap("${spec.footer}   ·   Page $pageNumber", captionPaint, CONTENT_WIDTH)
            var footerY = PAGE_HEIGHT - MARGIN - lines.size * LINE_SMALL
            lines.forEach { line ->
                canvas.drawText(line, MARGIN, footerY, captionPaint)
                footerY += LINE_SMALL
            }
        }

        private fun wrap(text: String, paint: Paint, width: Float): List<String> {
            if (text.isBlank()) return listOf("")
            val lines = mutableListOf<String>()
            text.split('\n').forEach { paragraph ->
                val words = paragraph.split(' ').filter { it.isNotEmpty() }
                var line = StringBuilder()
                words.forEach { word ->
                    val candidate = if (line.isEmpty()) word else "$line $word"
                    if (paint.measureText(candidate) <= width || line.isEmpty()) {
                        line = StringBuilder(candidate)
                    } else {
                        lines += line.toString()
                        line = StringBuilder(word)
                    }
                }
                lines += line.toString()
            }
            return lines.ifEmpty { listOf("") }
        }

        private fun lineHeight(paint: Paint): Float = paint.textSize * 1.35f

        private fun paint(size: PdfTextSize, bold: Boolean = false): Paint = Paint().apply {
            isAntiAlias = true
            color = OhReportPaper.ink.toArgb()
            textSize = when (size) {
                PdfTextSize.TITLE -> 20f
                PdfTextSize.HEADING -> 13f
                PdfTextSize.BODY -> 9.5f
                PdfTextSize.CAPTION -> 8f
            }
            typeface = if (bold) {
                Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            } else {
                Typeface.SANS_SERIF
            }
        }
    }

    private companion object {
        // A4 at 72 dpi, in points.
        const val PAGE_WIDTH = 595f
        const val PAGE_HEIGHT = 842f
        const val MARGIN = 42f
        const val CONTENT_WIDTH = PAGE_WIDTH - MARGIN * 2
        const val GAP = 14f
        const val GAP_SMALL = 6f
        const val HEADING_HEIGHT = 18f
        const val CELL_PADDING = 6f
        const val LINE_SMALL = 10f
        const val FOOTER_HEIGHT = 46f
    }
}
