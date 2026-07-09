package io.github.texport.superkassa.jvm.receipt.impl

import kz.mybrain.superkassa.core.domain.port.DocumentConvertPort
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * Адаптер конвертации HTML в PDF и Image (делегирует ESC/POS в EscPosConverter).
 */
class DocumentConvertAdapter : DocumentConvertPort {

    companion object {
        private const val BOTTOM_PADDING_PX = 20

        init {
            System.setProperty("xr.util-logging.loggingEnabled", "false")
            try {
                com.openhtmltopdf.util.XRLog.setLoggingEnabled(false)
            } catch (e: Throwable) {
                // ignore
            }
            java.util.logging.Logger.getLogger("com.openhtmltopdf").level = java.util.logging.Level.OFF
        }
    }

    /**
     * Преобразует HTML-документ чека в формат PDF с помощью headless Chromium.
     * Это гарантирует 100% соответствие вида PDF исходному HTML-виду (включая CSS-переменные, цвета и сетки).
     *
     * @param html исходный HTML-код чека.
     * @return массив байт сгенерированного PDF-документа.
     */
    override fun htmlToPdf(html: String): ByteArray {
        val tempHtmlFile = java.io.File.createTempFile("receipt-", ".html")
        val tempPdfFile = java.io.File.createTempFile("receipt-", ".pdf")
        try {
            // 1. Рендерим в PNG для определения реальной высоты
            val imgBytes = htmlToImage(html)
            val img = javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(imgBytes))
            val heightPx = img.height

            // 2. Вычисляем размеры ленты
            val is58 = html.contains("tape-58mm")
            val paperWidth = if (is58) 58.0 else 80.0

            // 380px — логическая ширина, 3.0 — масштаб устройства (scale factor)
            val heightCss = heightPx / 3.0
            val scale = paperWidth / 380.0
            val heightMm = (heightCss * scale) + 8.0 // добавляем 8мм запас на отступы

            val cssInject = """
                <style>
                @page {
                    size: ${paperWidth}mm ${heightMm}mm;
                    margin: 0 !important;
                }
                * {
                    -webkit-print-color-adjust: exact !important;
                    print-color-adjust: exact !important;
                }
                body {
                    margin: 0 !important;
                    padding: 0 !important;
                }
                </style>
            """.trimIndent()

            val modifiedHtml = html
                .replace("@media print", "@media print_disabled")
                .let {
                    if (it.contains("</head>")) {
                        it.replace("</head>", "$cssInject\n</head>")
                    } else {
                        "<html><head>$cssInject</head><body>$it</body></html>"
                    }
                }

            tempHtmlFile.writeText(wrapHtml(modifiedHtml))

            val process = ProcessBuilder(
                resolveChromiumPath(),
                "--headless",
                "--disable-gpu",
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--print-to-pdf-no-header",
                "--print-to-pdf=${tempPdfFile.absolutePath}",
                tempHtmlFile.absolutePath
            ).start()

            val finished = process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                error("Chromium PDF rendering timed out")
            }
            if (process.exitValue() != 0) {
                val errorStream = process.errorStream.bufferedReader().readText()
                val outStream = process.inputStream.bufferedReader().readText()
                error("Chromium failed with exit code ${process.exitValue()}. Output: $outStream, Error: $errorStream")
            }

            return tempPdfFile.readBytes()
        } finally {
            try {
                tempHtmlFile.delete()
            } catch (_: Throwable) {
            }
            try {
                tempPdfFile.delete()
            } catch (_: Throwable) {
            }
        }
    }

    /**
     * Преобразует HTML-документ чека в растровое изображение формата PNG.
     * Применяется для отправки чеков в мессенджеры и отображения на экране.
     *
     * @param html исходный HTML-код чека.
     * @return массив байт изображения чека в формате PNG.
     */
    override fun htmlToImage(html: String): ByteArray {
        val tempHtmlFile = java.io.File.createTempFile("receipt-", ".html")
        val tempPngFile = java.io.File.createTempFile("receipt-", ".png")
        try {
            val cssInject = """
                <style>
                * {
                    -webkit-print-color-adjust: exact !important;
                    print-color-adjust: exact !important;
                }
                body {
                    margin: 0 !important;
                    padding: 0 !important;
                }
                </style>
            """.trimIndent()

            val modifiedHtml = html
                .replace("@media print", "@media print_disabled")
                .let {
                    if (it.contains("</head>")) {
                        it.replace("</head>", "$cssInject\n</head>")
                    } else {
                        "<html><head>$cssInject</head><body>$it</body></html>"
                    }
                }

            tempHtmlFile.writeText(wrapHtml(modifiedHtml))

            val process = ProcessBuilder(
                resolveChromiumPath(),
                "--headless",
                "--disable-gpu",
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--window-size=380,3000", // standard 80mm width, very long height to avoid clipping
                "--force-device-scale-factor=3",
                "--screenshot=${tempPngFile.absolutePath}",
                tempHtmlFile.absolutePath
            ).start()

            val finished = process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                error("Chromium screenshot timed out")
            }
            val errorStream = process.errorStream.bufferedReader().readText()
            val outStream = process.inputStream.bufferedReader().readText()
            if (process.exitValue() != 0) {
                error("Chromium screenshot failed with exit code ${process.exitValue()}. Output: $outStream, Error: $errorStream")
            }

            val pngLength = tempPngFile.length()
            val pngExists = tempPngFile.exists()
            if (!pngExists || pngLength == 0L) {
                error(
                    "Chromium screenshot file does not exist or is empty! " +
                        "Path: ${tempPngFile.absolutePath}, Size: $pngLength, " +
                        "Out: $outStream, Err: $errorStream"
                )
            }

            val originalImage = ImageIO.read(tempPngFile)
            if (originalImage == null) {
                val formatNames = ImageIO.getReaderFormatNames().joinToString()
                error("ImageIO.read returned null for PNG image (File exists: $pngExists, Size: $pngLength). Registered reader formats: $formatNames")
            }
            val croppedImage = cropSolidBottom(originalImage)

            ByteArrayOutputStream().use { os ->
                ImageIO.write(croppedImage, "PNG", os)
                return os.toByteArray()
            }
        } finally {
            try {
                tempHtmlFile.delete()
            } catch (_: Throwable) {
            }
            try {
                tempPngFile.delete()
            } catch (_: Throwable) {
            }
        }
    }

    private fun cropSolidBottom(image: BufferedImage): BufferedImage {
        val width = image.width
        val height = image.height
        var cropHeight = height

        // Get the background color from the bottom-left pixel
        val bgArgb = image.getRGB(0, height - 1)

        for (y in height - 1 downTo 0) {
            var rowIsBg = true
            for (x in 0 until width) {
                if (image.getRGB(x, y) != bgArgb) {
                    rowIsBg = false
                    break
                }
            }
            if (!rowIsBg) {
                cropHeight = y + 1
                break
            }
        }

        // Add a small padding so the bottom isn't cut off too close
        cropHeight = minOf(height, cropHeight + BOTTOM_PADDING_PX)

        if (cropHeight < height && cropHeight > 0) {
            return image.getSubimage(0, 0, width, cropHeight)
        }
        return image
    }

    /**
     * Преобразует HTML-документ чека в массив команд принтера ESC/POS.
     * Позволяет выполнять физическую печать чека на совместимых принтерах.
     *
     * @param html исходный HTML-код чека.
     * @param paperWidthMm ширина бумажной ленты принтера в миллиметрах.
     * @return массив байт двоичных команд ESC/POS.
     */
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

    private fun resolveChromiumPath(): String {
        val os = System.getProperty("os.name").lowercase()
        if (os.contains("mac")) {
            val macChrome = java.io.File("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome")
            if (macChrome.exists()) {
                return macChrome.absolutePath
            }
        }
        return "chromium-browser"
    }
}
