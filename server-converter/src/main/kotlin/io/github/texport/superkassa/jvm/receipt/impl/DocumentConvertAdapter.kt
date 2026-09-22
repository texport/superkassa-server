package io.github.texport.superkassa.jvm.receipt.impl

import io.github.texport.superkassa.core.domain.api.port.integration.DocumentConvertPort
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
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

/** Запас снизу страницы PDF: последняя строка не должна лечь на обрез. */
private const val PDF_BOTTOM_MARGIN_MM = 2.0

class DocumentConvertAdapter : DocumentConvertPort {

    companion object {
        /** Окно мерки: высота не важна, растр не рисуется. */
        private const val MEASURE_WINDOW_HEIGHT_PX = 600

        /** Сколько виртуального времени дать странице на загрузку шрифтов и разметки. */
        private const val RENDER_BUDGET_MS = 3000

        private const val RENDER_TIMEOUT_S = 15L

        /**
         * Ключи, общие всем запускам браузера.
         *
         * Мерка, снимок и печать идут одними и теми же ключами: высота,
         * измеренная в одной вёрстке, годится только для неё же. Полосы
         * прокрутки спрятаны, чтобы окно мерки и окно снимка отдавали
         * вёрстке одну ширину.
         */
        private val COMMON_KEYS = listOf(
            "--headless",
            "--disable-gpu",
            "--no-sandbox",
            "--disable-dev-shm-usage",
            "--hide-scrollbars",
            "--virtual-time-budget=$RENDER_BUDGET_MS"
        )

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
     * холста ленты, а в печати Chromium верстал под ширину бумаги —
     * строки переносились, документ становился выше, и QR-код уезжал
     * на вторую, почти пустую страницу.
     *
     * Теперь лента верстается прямо на ширину бумаги, её высота измеряется
     * в этой же вёрстке, и страница задаётся ровно по ней.
     *
     * @param html исходный HTML-код чека.
     * @return массив байт PDF-документа.
     */
    override fun htmlToPdf(html: String): ByteArray {
        val paperWidth = FormGeometry.paperWidthMm(html)
        val printable = FormMarkup.printablePage(html, paperWidth)
        return withTempPage(printable, ".pdf") { pageFile, pdfFile ->
            // Не измерилось — страница высотой в запасное окно: длинный
            // Z-отчёт лучше с пустым низом, чем обрезанный.
            val heightCss = measuredHeight(pageFile, FormGeometry.formWidthPx(html)) ?: CANVAS_HEIGHT_PX
            val heightMm = heightCss / FormGeometry.PX_PER_MM + PDF_BOTTOM_MARGIN_MM
            pageFile.writeText(FormMarkup.withPageSize(printable, paperWidth, heightMm))
            runBrowser(
                "Chromium PDF rendering",
                COMMON_KEYS + listOf(
                    "--print-to-pdf-no-header",
                    "--print-to-pdf=${pdfFile.absolutePath}",
                    pageFile.absolutePath
                )
            )
            pdfFile.readBytes()
        }
    }

    /**
     * Преобразует HTML-документ чека в растровое изображение формата PNG.
     * Применяется для отправки чеков в мессенджеры и отображения на экране.
     *
     * Снимок повторяет страницу браузера: ширина холста задаётся в самой
     * странице, а не только окном браузера — окно уже своего минимума
     * браузер не отдаёт, вёрстка шла по более широкому окну, а снимок
     * резался по этой ширине, и чек уезжал вправо.
     *
     * @param html исходный HTML-код чека.
     * @return массив байт изображения чека в формате PNG.
     */
    override fun htmlToImage(html: String): ByteArray {
        val canvas = FormGeometry.canvasWidthPx(html)
        val page = FormMarkup.screenshotPage(html, canvas)
        return withTempPage(page, ".png") { pageFile, pngFile ->
            val windowHeight = pageHeight(pageFile, canvas)
            runBrowser(
                "Chromium screenshot",
                COMMON_KEYS + listOf(
                    "--window-size=$canvas,$windowHeight",
                    "--force-device-scale-factor=$RENDER_SCALE",
                    "--screenshot=${pngFile.absolutePath}",
                    pageFile.absolutePath
                )
            )
            check(pngFile.length() > 0) { "Chromium screenshot produced an empty file at ${pngFile.absolutePath}" }
            val image = ImageIO.read(pngFile) ?: error("PNG produced by Chromium is unreadable")
            ByteArrayOutputStream().use { os ->
                ImageIO.write(SnapshotCrop.withoutEmptyBottom(image), "PNG", os)
                os.toByteArray()
            }
        }
    }

    /** Страница и её образ живут во временных файлах ровно на время конвертации. */
    private fun <T> withTempPage(page: String, suffix: String, draw: (File, File) -> T): T {
        val pageFile = File.createTempFile("receipt-", ".html")
        val imageFile = File.createTempFile("receipt-", suffix)
        try {
            pageFile.writeText(page)
            return draw(pageFile, imageFile)
        } finally {
            pageFile.delete()
            imageFile.delete()
        }
    }

    /** Запускает браузер и падает с его же выводом, если тот не справился. */
    private fun runBrowser(what: String, keys: List<String>) {
        val process = ProcessBuilder(listOf(BrowserLocator.local.path()) + keys).start()
        if (!process.waitFor(RENDER_TIMEOUT_S, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            error("$what timed out")
        }
        if (process.exitValue() != 0) {
            val error = process.errorStream.bufferedReader().readText()
            error("$what failed with exit code ${process.exitValue()}: $error")
        }
    }

    /**
     * Высота окна для снимка: измеренная страница с запасом, не ниже
     * запасного значения и не выше предела. Лишний низ снимка срезается
     * потом по цвету, поэтому запас безвреден.
     */
    private fun pageHeight(htmlFile: File, widthPx: Int): Int {
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
    private fun measuredHeight(htmlFile: File, widthPx: Int): Int? = runCatching {
        val process = ProcessBuilder(
            listOf(BrowserLocator.local.path()) + COMMON_KEYS + listOf(
                "--window-size=$widthPx,$MEASURE_WINDOW_HEIGHT_PX",
                "--dump-dom",
                htmlFile.absolutePath
            )
        ).redirectErrorStream(false).start()
        val dom = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(RENDER_TIMEOUT_S, TimeUnit.SECONDS)) {
            process.destroyForcibly()
        }
        FormMarkup.HEIGHT_MARK.find(dom)?.groupValues?.get(1)?.toIntOrNull()
    }.getOrNull()

    /**
     * Преобразует HTML-документ чека в массив команд принтера ESC/POS.
     * Позволяет выполнять физическую печать чека на совместимых принтерах.
     *
     * @param html исходный HTML-код чека.
     * @param paperWidthMm ширина бумажной ленты принтера в миллиметрах.
     * @return массив байт двоичных команд ESC/POS.
     */
    override fun htmlToEscPos(html: String, paperWidthMm: Int): ByteArray =
        EscPosConverter.convertHtmlToEscPos(html, paperWidthMm)
}
