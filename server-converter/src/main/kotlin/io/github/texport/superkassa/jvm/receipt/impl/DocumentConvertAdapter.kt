package io.github.texport.superkassa.jvm.receipt.impl

import io.github.texport.superkassa.core.domain.api.port.integration.DocumentConvertPort
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * Адаптер конвертации HTML в PDF и Image (делегирует ESC/POS в EscPosConverter).
 */

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

/** Миллиметров в одной точке CSS: 96 точек на дюйм. */
private const val MM_PER_CSS_PX = 25.4 / 96

/** Запас снизу страницы PDF: последняя строка не должна лечь на обрез. */
private const val PDF_BOTTOM_MARGIN_MM = 2.0

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
     * Преобразует печатную форму в PDF: страница — сама лента.
     *
     * Лента и в PDF, и на экране одна и та же разметка, но страница PDF
     * прежде считалась по снимку экрана: там тело зафиксировано на ширине
     * [FormGeometry.TAPE_CANVAS_PX], а в печати Chromium верстал под ширину бумаги —
     * строки переносились, документ становился выше, и QR-код уезжал
     * на вторую, почти пустую страницу.
     *
     * Теперь лента верстается прямо на ширину бумаги, её высота измеряется
     * в этой же вёрстке, и страница задаётся ровно по ней. Фон страницы
     * и поля вокруг ленты в PDF не нужны: покупатель получает чек, а не
     * экран кассы.
     *
     * @param html исходный HTML-код чека.
     * @return массив байт PDF-документа.
     */
    override fun htmlToPdf(html: String): ByteArray {
        val tempHtmlFile = java.io.File.createTempFile("receipt-", ".html")
        val tempPdfFile = java.io.File.createTempFile("receipt-", ".pdf")
        try {
            val paperWidth = FormGeometry.paperWidthMm(html)
            val widthPx = (paperWidth / MM_PER_CSS_PX).toInt()
            val tapeCss = """
                $HEIGHT_PROBE
                <style>
                * {
                    -webkit-print-color-adjust: exact !important;
                    print-color-adjust: exact !important;
                }
                html, body {
                    width: ${paperWidth}mm !important;
                    margin: 0 !important;
                    padding: 0 !important;
                    background: #ffffff !important;
                    overflow-x: hidden !important;
                }
                .receipt {
                    margin: 0 !important;
                    width: ${paperWidth}mm !important;
                    max-width: ${paperWidth}mm !important;
                    box-shadow: none !important;
                }
                </style>
            """.trimIndent()
            val tapeHtml = html.replace("@media print", "@media print_disabled").withHead(tapeCss)
            tempHtmlFile.writeText(wrapHtml(tapeHtml))

            // Не измерилось — страница высотой в запасное окно: длинный
            // Z-отчёт лучше с пустым низом, чем обрезанный.
            val heightCss = measuredHeight(tempHtmlFile, widthPx) ?: CANVAS_HEIGHT_PX
            val heightMm = heightCss * MM_PER_CSS_PX + PDF_BOTTOM_MARGIN_MM
            val pageCss = """
                <style>
                @page {
                    size: ${paperWidth}mm ${"%.2f".format(java.util.Locale.ROOT, heightMm)}mm;
                    margin: 0 !important;
                }
                </style>
            """.trimIndent()
            tempHtmlFile.writeText(wrapHtml(tapeHtml.withHead(pageCss)))

            val process = ProcessBuilder(
                BrowserLocator.local.path(),
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

    /** Вставляет разметку в `<head>`; страница без `<head>` оборачивается целиком. */
    private fun String.withHead(inject: String): String =
        if (contains("</head>")) replace("</head>", "$inject\n</head>") else "<html><head>$inject</head><body>$this</body></html>"

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
        val canvas = FormGeometry.canvasWidthPx(html)
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
                    width: ${canvas}px !important;
                    min-width: ${canvas}px !important;
                    max-width: ${canvas}px !important;
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

            val windowHeight = pageHeight(tempHtmlFile, canvas)
            val process = ProcessBuilder(
                BrowserLocator.local.path(),
                "--headless",
                "--disable-gpu",
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--window-size=$canvas,$windowHeight",
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
    /**
     * Высота окна для снимка: измеренная страница с запасом, не ниже
     * запасного значения и не выше предела. Лишний низ снимка срезается
     * потом по цвету, поэтому запас безвреден.
     */
    private fun pageHeight(htmlFile: java.io.File, widthPx: Int): Int {
        val height = measuredHeight(htmlFile, widthPx) ?: return CANVAS_HEIGHT_PX
        return (height + CANVAS_HEIGHT_MARGIN_PX).coerceIn(CANVAS_HEIGHT_PX, CANVAS_HEIGHT_LIMIT_PX)
    }

    /**
     * Высота страницы в точках CSS, как её сообщила сама страница.
     *
     * `null` — измерить не удалось: браузер не ответил или не подставил
     * заголовок. Вёрстка идёт при заданной ширине окна: у ленты на бумаге
     * она своя, и высота при другой ширине была бы чужой.
     */
    private fun measuredHeight(htmlFile: java.io.File, widthPx: Int): Int? = runCatching {
        val process = ProcessBuilder(
            BrowserLocator.local.path(),
            "--headless",
            "--disable-gpu",
            "--no-sandbox",
            "--disable-dev-shm-usage",
            "--window-size=$widthPx,$MEASURE_WINDOW_HEIGHT_PX",
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
}
