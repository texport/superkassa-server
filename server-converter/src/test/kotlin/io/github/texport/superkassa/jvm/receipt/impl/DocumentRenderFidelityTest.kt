package io.github.texport.superkassa.jvm.receipt.impl

import io.github.texport.superkassa.core.domain.api.model.receipt.ReceiptLayoutType
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * Образ документа повторяет страницу браузера.
 *
 * Образы рисует браузер, которого на машине сборки может не быть:
 * без него проверки пропускаются, а не падают — узел на такой машине
 * и сам сообщает об этом отдельным отказом.
 */
class DocumentRenderFidelityTest {

    private val adapter = DocumentConvertAdapter()
    private val samples = File("build/render-samples").apply { mkdirs() }

    private fun withBrowser() = assumeTrue(BrowserLocator.local.find() != null, "браузера для образов на машине нет")

    private fun forms() = mapOf(
        "sale-80mm" to SampleForms.sale(ReceiptLayoutType.TAPE_80MM),
        "refund-80mm" to SampleForms.refund(ReceiptLayoutType.TAPE_80MM),
        "z-report-80mm" to SampleForms.zReport(ReceiptLayoutType.TAPE_80MM),
        "sale-58mm" to SampleForms.sale(ReceiptLayoutType.TAPE_58MM),
        "sale-fullscreen" to SampleForms.sale(ReceiptLayoutType.FULLSCREEN)
    )

    @Test
    fun `документ ложится на один лист, и лист не уже ленты`() {
        withBrowser()
        forms().forEach { (name, html) ->
            val pdf = adapter.htmlToPdf(html).also { File(samples, "$name.pdf").writeBytes(it) }
            File(samples, "$name.html").writeText(html)
            val paper = FormGeometry.paperWidthMm(html)
            Loader.loadPDF(pdf).use { document ->
                assertEquals(1, document.numberOfPages, "$name: лента разошлась по листам")
                val widthMm = document.getPage(0).mediaBox.width / POINTS_PER_MM
                assertTrue(
                    widthMm >= paper && widthMm <= paper + 2 * FormMarkup.PAGE_SLACK_MM,
                    "$name: лист $widthMm мм при ленте $paper мм — рамку срежет"
                )
            }
        }
    }

    @Test
    fun `лента занимает лист целиком и доходит до нижнего края`() {
        withBrowser()
        forms().forEach { (name, html) ->
            val page = rasterize(adapter.htmlToPdf(html))
            val painted = paintedRows(page)
            assertTrue(page.rowIsPainted(0), "$name: верх листа пуст — лента сдвинута")
            val edges = paintedColumns(page)
            assertTrue(mm(edges.first) <= EDGE_GAP_MM, "$name: левая рамка ленты срезана")
            assertTrue(mm(page.width - 1 - edges.last) <= EDGE_GAP_MM, "$name: правая рамка ленты срезана")
            assertTrue(mm(page.height - painted.last) < MAX_TAIL_MM, "$name: под лентой пустота — лист посчитан не по ленте")
        }
    }

    @Test
    fun `в PDF попадает весь документ, включая его низ`() {
        withBrowser()
        forms().forEach { (name, html) ->
            val text = Loader.loadPDF(adapter.htmlToPdf(html)).use { PDFTextStripper().getText(it) }.squeezed().lowercase()
            assertTrue(text.contains("ип иванов"), "$name: шапка документа не дошла до PDF")
            assertTrue(text.contains(lastLineOf(html).lowercase()), "$name: низ документа обрезан")
        }
    }

    @Test
    fun `снимок повторяет страницу целиком, с полями и оторванным краем`() {
        withBrowser()
        forms().forEach { (name, html) ->
            val png = adapter.htmlToImage(html).also { File(samples, "$name.png").writeBytes(it) }
            val image = ImageIO.read(png.inputStream())
            assertEquals(
                FormGeometry.canvasWidthPx(html) * RENDER_SCALE,
                image.width,
                "$name: ширина снимка не равна холсту формы"
            )
            val background = image.getRGB(0, 0)
            assertTrue(image.rowIsUniform(0, background), "$name: лента прижата к верхнему краю — поля страницы потеряны")
            assertTrue(tornEdgeRow(image, background) > 0, "$name: оторванный край ленты обрезан")
        }
    }

    /** Ряд зубчатого края: фон страницы вперемешку с белым, ниже нижней рамки ленты. */
    private fun tornEdgeRow(image: BufferedImage, background: Int): Int {
        for (y in image.height - 1 downTo 0) {
            var background0 = 0
            var light = 0
            for (x in 0 until image.width step STRIDE) {
                if (image.getRGB(x, y) == background) background0++ else light++
            }
            if (background0 > 0 && light > 0 && background0 > image.width / STRIDE / 8) return y
        }
        return 0
    }

    private fun BufferedImage.rowIsUniform(y: Int, colour: Int): Boolean =
        (0 until width step STRIDE).all { getRGB(it, y) == colour }

    private fun BufferedImage.rowIsPainted(y: Int): Boolean =
        (0 until width step STRIDE).any { abs(luminance(getRGB(it, y)) - WHITE) > INK }

    private fun BufferedImage.columnIsPainted(x: Int): Boolean =
        (0 until height step STRIDE).any { abs(luminance(getRGB(x, it)) - WHITE) > INK }

    /**
     * Последняя строка текста формы: по ней видно, что низ документа дошёл до PDF.
     *
     * Сравнение идёт без учёта регистра: часть подписей набрана прописными
     * средствами стилей, и в разметке они строчные.
     */
    private fun lastLineOf(html: String): String = html
        .substringAfterLast("</table>")
        .replace(Regex("<[^>]*>"), " ")
        .split("\n")
        .map { it.squeezed() }
        .last { it.length > LAST_LINE_MIN }

    private fun String.squeezed(): String = replace(Regex("\\s+"), " ").trim()

    /** Точки растра листа в миллиметрах листа. */
    private fun mm(px: Int): Double = px / POINTS_PER_MM / RASTER_SCALE

    private fun paintedColumns(image: BufferedImage): IntRange {
        val painted = (0 until image.width).filter { image.columnIsPainted(it) }
        return (painted.firstOrNull() ?: 0)..(painted.lastOrNull() ?: 0)
    }

    private fun paintedRows(image: BufferedImage): IntRange {
        val painted = (0 until image.height).filter { image.rowIsPainted(it) }
        return (painted.firstOrNull() ?: 0)..(painted.lastOrNull() ?: 0)
    }

    private fun luminance(argb: Int) = ((argb shr 16 and 0xff) + (argb shr 8 and 0xff) + (argb and 0xff)) / 3

    private fun rasterize(pdf: ByteArray): BufferedImage =
        Loader.loadPDF(pdf).use { PDFRenderer(it).renderImage(0, RASTER_SCALE.toFloat()) }

    private companion object {
        const val POINTS_PER_MM = 72 / 25.4
        const val RENDER_SCALE = 3
        const val RASTER_SCALE = 2
        const val STRIDE = 3
        const val WHITE = 255
        const val INK = 8
        const val MAX_TAIL_MM = 4.0
        const val EDGE_GAP_MM = 2 * FormMarkup.PAGE_SLACK_MM
        const val LAST_LINE_MIN = 8
    }
}
