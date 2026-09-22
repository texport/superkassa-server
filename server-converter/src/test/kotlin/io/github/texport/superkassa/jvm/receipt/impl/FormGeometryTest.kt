package io.github.texport.superkassa.jvm.receipt.impl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ширина бумаги и холста читаются с самой формы.
 *
 * Стили описывают все ленты сразу, поэтому решает класс корневого
 * элемента: узкая лента — 58 мм, полноэкранная форма — своя ширина
 * и свой холст, всё прочее — лента 80 мм.
 */
class FormGeometryTest {

    private fun form(tape: String) =
        """<html><head><style>.receipt.tape-58mm{}</style></head><body><div class="receipt $tape">x</div></body></html>"""

    @Test
    fun `узкая лента узнаётся по классу формы, а не по стилям`() {
        assertEquals(FormGeometry.TAPE_58_MM, FormGeometry.paperWidthMm(form("tape-58mm")))
        assertEquals(FormGeometry.TAPE_80_MM, FormGeometry.paperWidthMm(form("tape-80mm")))
        assertEquals(FormGeometry.TAPE_CANVAS_PX, FormGeometry.canvasWidthPx(form("tape-58mm")))
        assertEquals(FormGeometry.TAPE_CANVAS_PX, FormGeometry.canvasWidthPx(form("tape-80mm")))
    }

    @Test
    fun `полноэкранная форма получает свою ширину, а не страницу A4`() {
        assertEquals(FormGeometry.FULLSCREEN_MM, FormGeometry.paperWidthMm(form("tape-fullscreen")))
        assertEquals(FormGeometry.FULLSCREEN_CANVAS_PX, FormGeometry.canvasWidthPx(form("tape-fullscreen")))
        assertEquals(FormGeometry.FULLSCREEN_FORM_PX, FormGeometry.formWidthPx(form("tape-fullscreen")))
    }

    @Test
    fun `холст шире формы ровно на поле фона с обеих сторон`() {
        assertEquals(
            FormGeometry.FULLSCREEN_FORM_PX + 2 * FormGeometry.BACKGROUND_MARGIN_PX,
            FormGeometry.canvasWidthPx(form("tape-fullscreen"))
        )
        assertTrue(FormGeometry.canvasWidthPx(form("tape-80mm")) > FormGeometry.formWidthPx(form("tape-80mm")))
    }

    @Test
    fun `ширина формы в точках целая, дробное окно мерки браузеру не задать`() {
        assertEquals(302, FormGeometry.formWidthPx(form("tape-80mm")))
        assertEquals(219, FormGeometry.formWidthPx(form("tape-58mm")))
    }
}
