package io.github.texport.superkassa.jvm.receipt.impl

import io.github.texport.superkassa.core.domain.api.port.integration.DocumentConvertPort
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * Адаптер конвертации HTML в PDF и Image (делегирует ESC/POS в EscPosConverter).
 */
/** Логическая ширина ленты 80 мм в точках CSS. */
private const val CANVAS_WIDTH_PX = 380

/**
 * Высота окна, когда высоту страницы измерить не удалось.
 *
 * Снимок headless Chromium берёт окно, а не страницу целиком, поэтому
 * длинный документ обрезался ровно по этой границе: Z-отчёт заканчивался
 * посреди налоговых блоков, а пустого низа не оставалось — обрезку было
 * не видно. Поэтому высота окна теперь измеряется по самой странице,
 * а это значение остаётся запасным.
 */
private const val CANVAS_HEIGHT_PX = 3000

/**
 * Предел высоты окна в логических точках.
 *
 * Снимок плотнее логических точек в [RENDER_SCALE] раз, и растр высотой
 * в десятки тысяч точек стоит сотни мегабайт. Документ длиннее предела
 * обрежется, зато касса останется на ногах.
 */
private const val CANVAS_HEIGHT_LIMIT_PX = 20_000

/** Запас под нижний отступ ленты: с ним последняя строка не прилипает к краю. */
private const val CANVAS_HEIGHT_MARGIN_PX = 80

/** Во сколько раз снимок плотнее логических точек. */
private const val RENDER_SCALE = 3

class DocumentConvertAdapter : DocumentConvertPort {

    companion object {
        private const val BOTTOM_PADDING_PX = 20

        /** Окно мерки: высота не важна, растр не рисуется. */
        private const val MEASURE_WINDOW_HEIGHT_PX = 600

        /** Сколько виртуального времени дать странице на загрузку шрифтов и разметки. */
        private const val MEASURE_BUDGET_MS = 3000

        private const val MEASURE_TIMEOUT_S = 15L

        /**
         * Скрипт мерки: страница сама сообщает свою высоту заголовком окна.
         *
         * Прибавляется к разметке снимка, а не к печатной форме узла: в снимке
         * он безвреден, потому что заголовок в растр не попадает.
         */
        private val HEIGHT_PROBE = """
            <script>
            window.addEventListener('load', function () {
                var page = Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);
                document.title = 'superkassa-height:' + Math.ceil(page);
            });
            </script>
        """.trimIndent()

        /** Узкая лента: класс стоит на самой форме, а не только в стилях. */
        private val NARROW_TAPE = Regex("""class="[^"]*\btape-58mm\b""")

        /** Чем страница сообщает свою высоту: подменённым заголовком окна. */
        private val HEIGHT_MARK = Regex("""superkassa-height:(\d+)""")

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
            // Ширина ленты — по классу самой формы, а не по вхождению строки:
            // таблица стилей описывает обе ленты, и поиск подстроки находил
            // 58 мм в любом чеке, отчего 80-миллиметровая форма печаталась
            // на узкой странице.
            val is58 = NARROW_TAPE.containsMatchIn(html)
            val paperWidth = if (is58) 58.0 else 80.0

            // CANVAS_WIDTH_PX — логическая ширина ленты, RENDER_SCALE — масштаб устройства
            val heightCss = heightPx / 3.0
            val scale = paperWidth / CANVAS_WIDTH_PX.toDouble()
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
            // Ширина холста задаётся в самой странице, а не только окном
            // браузера: окно уже своего минимума браузер не отдаёт, вёрстка
            // шла по более широкому окну, а снимок резался по этой ширине —
            // чек уезжал вправо и обрезался по правому краю.
            val cssInject = """
                $HEIGHT_PROBE
                <style>
                * {
                    -webkit-print-color-adjust: exact !important;
                    print-color-adjust: exact !important;
                }
                html, body {
                    width: ${CANVAS_WIDTH_PX}px !important;
                    min-width: ${CANVAS_WIDTH_PX}px !important;
                    max-width: ${CANVAS_WIDTH_PX}px !important;
                    margin: 0 !important;
                    padding: 0 !important;
                    overflow-x: hidden !important;
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

            val windowHeight = pageHeight(tempHtmlFile)
            val process = ProcessBuilder(
                resolveChromiumPath(),
                "--headless",
                "--disable-gpu",
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--window-size=$CANVAS_WIDTH_PX,$windowHeight",
                "--force-device-scale-factor=$RENDER_SCALE",
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

    /**
     * Высота страницы в логических точках: её знает только сама страница.
     *
     * Chromium измеряет её у себя и приносит числом в заголовке окна —
     * `--dump-dom` печатает разметку после загрузки, и заголовок в ней уже
     * подменён. Растр при этом не рисуется, поэтому мерка дешёвая. Не вышло
     * измерить — работаем по запасной высоте, как раньше.
     */
    private fun pageHeight(htmlFile: java.io.File): Int {
        val measured = runCatching {
            val process = ProcessBuilder(
                resolveChromiumPath(),
                "--headless",
                "--disable-gpu",
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--window-size=$CANVAS_WIDTH_PX,$MEASURE_WINDOW_HEIGHT_PX",
                "--virtual-time-budget=$MEASURE_BUDGET_MS",
                "--dump-dom",
                htmlFile.absolutePath
            ).redirectErrorStream(false).start()
            val dom = process.inputStream.bufferedReader().readText()
            if (!process.waitFor(MEASURE_TIMEOUT_S, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly()
            }
            HEIGHT_MARK.find(dom)?.groupValues?.get(1)?.toIntOrNull()
        }.getOrNull()
        val height = measured ?: return CANVAS_HEIGHT_PX
        return (height + CANVAS_HEIGHT_MARGIN_PX).coerceIn(CANVAS_HEIGHT_PX, CANVAS_HEIGHT_LIMIT_PX)
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
