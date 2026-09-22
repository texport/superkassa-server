package io.github.texport.superkassa.jvm.receipt.impl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Страница снимка отличается от исходной только шириной окна.
 *
 * Обнулённые поля и `overflow-x: hidden` уносили с собой поля страницы
 * и зубчатый край ленты — образ переставал совпадать с тем, что владелец
 * видит в браузере. Проверки держат это отличие в одном месте.
 */
class FormMarkupTest {

    private val page = """<html><head><style>@media print { body { color: #000 } }</style></head><body>чек</body></html>"""

    @Test
    fun `снимок задаёт только ширину окна и не трогает поля страницы`() {
        val markup = FormMarkup.screenshotPage(page, 380)

        assertTrue(markup.contains("width: 380px !important;"))
        assertFalse(markup.contains("overflow-x"), "вынос за край уносит зубчатый край ленты")
        assertFalse(markup.contains("padding: 0 !important"), "поля страницы — часть её вида")
    }

    @Test
    fun `печать выключает правила принтера и красит фон по-настоящему`() {
        val markup = FormMarkup.printablePage(page, FormGeometry.TAPE_80_MM)

        assertTrue(markup.contains("@media print_disabled"))
        assertFalse(markup.contains("@media print {"))
        assertTrue(markup.contains("print-color-adjust: exact"))
        assertTrue(markup.contains("width: 80.00mm !important;"))
        assertTrue(markup.contains(".receipt::after { display: none !important; }"))
    }

    @Test
    fun `лист шире ленты на запас, иначе округление срезает рамку`() {
        val printable = FormMarkup.printablePage(page, FormGeometry.TAPE_58_MM)
        val sized = FormMarkup.withPageSize(printable, FormGeometry.TAPE_58_MM, 100.0)

        assertTrue(sized.contains("size: 58.30mm 100.00mm;"))
        assertEquals(0.3, FormMarkup.PAGE_SLACK_MM)
    }

    @Test
    fun `страница сама сообщает высоту, и мерка повторяется после шрифтов`() {
        val markup = FormMarkup.screenshotPage(page, 380)

        assertTrue(markup.contains("document.fonts.ready.then(superkassaMark)"))
        assertEquals(
            "1234",
            FormMarkup.HEIGHT_MARK.find("<title>superkassa-height:1234</title>")?.groupValues?.get(1)
        )
    }

    @Test
    fun `обрывок разметки становится целой страницей`() {
        val markup = FormMarkup.screenshotPage("<div>чек</div>", 380)

        assertTrue(markup.startsWith("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"/>"))
        assertTrue(markup.contains("<div>чек</div>"))
        assertTrue(markup.contains("width: 380px !important;"))
    }

    @Test
    fun `готовая страница остаётся собой`() {
        val doctype = """<!DOCTYPE html><html><head></head><body>чек</body></html>"""

        assertTrue(FormMarkup.screenshotPage(doctype, 380).startsWith("<!DOCTYPE html>"))
        assertTrue(FormMarkup.printablePage(doctype, FormGeometry.TAPE_80_MM).startsWith("<!DOCTYPE html>"))
    }
}
