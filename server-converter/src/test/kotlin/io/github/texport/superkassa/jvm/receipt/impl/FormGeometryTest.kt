package io.github.texport.superkassa.jvm.receipt.impl

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Ширина бумаги и холста читаются с самой формы.
 *
 * Стили описывают все ленты сразу, поэтому решает класс корневого
 * элемента: узкая лента — 58 мм, полноэкранная форма — страница A4
 * и широкий холст, всё прочее — лента 80 мм.
 */
class FormGeometryTest {

    private fun form(tape: String) =
        """<html><head><style>.receipt.tape-58mm{}</style></head><body><div class="receipt $tape">x</div></body></html>"""

    @Test
    fun `узкая лента узнаётся по классу формы, а не по стилям`() {
        assertEquals(FormGeometry.TAPE_58_MM, FormGeometry.paperWidthMm(form("tape-58mm")))
        assertEquals(FormGeometry.TAPE_80_MM, FormGeometry.paperWidthMm(form("tape-80mm")))
        assertEquals(FormGeometry.TAPE_CANVAS_PX, FormGeometry.canvasWidthPx(form("tape-58mm")))
    }

    @Test
    fun `полноэкранная форма получает страницу A4 и широкий холст`() {
        assertEquals(FormGeometry.PAGE_A4_MM, FormGeometry.paperWidthMm(form("tape-fullscreen")))
        assertEquals(FormGeometry.FULLSCREEN_CANVAS_PX, FormGeometry.canvasWidthPx(form("tape-fullscreen")))
    }
}
