package io.github.texport.superkassa.jvm.receipt.data

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import kz.mybrain.superkassa.core.domain.port.DocumentConvertPort
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * Адаптер конвертации HTML в PDF и Image (делегирует ESC/POS в EscPosConverter).
 */
@Suppress("NewApi")
class DocumentConvertAdapter : DocumentConvertPort {

    override fun htmlToPdf(html: String): ByteArray {
        ByteArrayOutputStream().use { os ->
            val builder = PdfRendererBuilder()
            builder.useFastMode()
            builder.withHtmlContent(wrapHtml(html), null)
            builder.toStream(os)
            builder.run()
            return os.toByteArray()
        }
    }

    override fun htmlToImage(html: String): ByteArray {
        val pdf = htmlToPdf(html)
        val document = PDDocument.load(pdf)
        try {
            val renderer = PDFRenderer(document)
            val image: BufferedImage = renderer.renderImageWithDPI(0, 150f)
            ByteArrayOutputStream().use { os ->
                ImageIO.write(image, "PNG", os)
                return os.toByteArray()
            }
        } finally {
            document.close()
        }
    }

    override fun htmlToEscPos(html: String, paperWidthMm: Int): ByteArray {
        // Делегируем специализированному конвертеру (KISS & SRP)
        return EscPosConverter.convertHtmlToEscPos(html, paperWidthMm)
    }

    private fun wrapHtml(html: String): String {
        val trimmed = html.trim()
        if (trimmed.startsWith("<!DOCTYPE", ignoreCase = true) || trimmed.startsWith("<html", ignoreCase = true)) {
            return html
        }
        return """
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8"/>
<style>
body { font-family: sans-serif; font-size: 10pt; }
table { width: 100%; }
</style>
</head>
<body>
$html
</body>
</html>
        """.trimIndent()
    }
}
