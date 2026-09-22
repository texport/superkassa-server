package io.github.texport.superkassa.jvm.receipt.impl

import java.util.Locale

/**
 * Разметка, которой печатная форма готовится к снимку и к печати.
 *
 * Образ документа обязан повторять ту страницу, которую владелец видит,
 * открыв сохранённый HTML. Поэтому страница снимка отличается от исходной
 * ровно одним: ей задана ширина окна. Всё остальное — поля страницы,
 * фон, оторванный край ленты — остаётся как в браузере.
 *
 * Прежде снимок вдобавок обнулял поля страницы и прятал вынос за её
 * границы (`overflow-x: hidden`). Обнулённые поля прижимали ленту к краю
 * растра, а вынос уносил вместе с собой зубчатый край ленты: `.receipt::after`
 * висит на двенадцать точек ниже карточки и целиком попадал под обрезку.
 * Отсюда и «не так, как в HTML».
 */
internal object FormMarkup {

    /** Чем страница сообщает свою высоту: подменённым заголовком окна. */
    val HEIGHT_MARK: Regex = Regex("""superkassa-height:(\d+)""")

    /**
     * Запас страницы PDF по ширине.
     *
     * Chromium округляет размер страницы к своей сетке: заказанные 58 мм
     * становились 57,83 мм, и лента ровно в 58 мм теряла правую рамку.
     * Страница шире ленты на волос, лента на ней по середине — рамка цела,
     * а лишняя треть миллиметра на бумаге не видна.
     */
    const val PAGE_SLACK_MM: Double = 0.3

    /**
     * Скрипт мерки: страница сама сообщает свою высоту заголовком окна.
     *
     * Мерка повторяется после загрузки шрифтов: подставной шрифт метрику
     * меняет, и высота, снятая до его замены, оказывалась чужой.
     */
    private val HEIGHT_PROBE = """
        <script>
        function superkassaMark() {
            var page = Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);
            document.title = 'superkassa-height:' + Math.ceil(page);
        }
        window.addEventListener('load', function () {
            superkassaMark();
            if (document.fonts && document.fonts.ready) {
                document.fonts.ready.then(superkassaMark);
            }
        });
        </script>
    """.trimIndent()

    /** Страница снимка: та же вёрстка, только ширина окна задана самой странице. */
    fun screenshotPage(html: String, canvasPx: Int): String {
        val style = """
            $HEIGHT_PROBE
            <style>
            html, body {
                width: ${canvasPx}px !important;
                min-width: ${canvasPx}px !important;
                max-width: ${canvasPx}px !important;
                margin: 0 !important;
            }
            </style>
        """.trimIndent()
        return screenMedia(html).withHead(style)
    }

    /**
     * Страница печати: лента во всю ширину бумаги на белом.
     *
     * Покупателю достаётся чек, а не экран кассы, поэтому фона страницы
     * и полей вокруг ленты в PDF нет. Зубчатый край при этом прячется:
     * он белый по белому, а Chromium ради него растрирует целую полосу.
     */
    fun printablePage(html: String, paperWidthMm: Double): String {
        val paper = mm(paperWidthMm)
        val style = """
            $HEIGHT_PROBE
            <style>
            * {
                -webkit-print-color-adjust: exact !important;
                print-color-adjust: exact !important;
            }
            html, body {
                width: ${paper}mm !important;
                margin: 0 !important;
                padding: 0 !important;
                background: #ffffff !important;
            }
            .receipt {
                margin: 0 auto !important;
                width: ${paper}mm !important;
                max-width: ${paper}mm !important;
                box-shadow: none !important;
            }
            .receipt::after { display: none !important; }
            </style>
        """.trimIndent()
        return screenMedia(html).withHead(style)
    }

    /** Та же страница печати с заданным размером листа. */
    fun withPageSize(printable: String, paperWidthMm: Double, heightMm: Double): String {
        val style = """
            <style>
            @page {
                size: ${mm(paperWidthMm + PAGE_SLACK_MM)}mm ${mm(heightMm)}mm;
                margin: 0 !important;
            }
            </style>
        """.trimIndent()
        return printable.withHead(style)
    }

    /**
     * Правила печати выключаются: и снимок, и PDF показывают то же,
     * что владелец видит на экране, а не чёрно-белый вариант для принтера.
     */
    private fun screenMedia(html: String): String = html.replace("@media print", "@media print_disabled")

    private fun mm(value: Double): String = "%.2f".format(Locale.ROOT, value)

    /**
     * Вставляет разметку в `<head>`.
     *
     * Обрывок разметки без `<head>` оборачивается целой страницей — вместе
     * с кодировкой: без неё браузер угадывает её сам и портит кириллицу.
     */
    private fun String.withHead(inject: String): String =
        if (contains("</head>")) {
            replace("</head>", "$inject\n</head>")
        } else {
            """<!DOCTYPE html><html><head><meta charset="UTF-8"/>$inject</head><body>$this</body></html>"""
        }
}
