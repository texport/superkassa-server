package io.github.texport.superkassa.jvm.receipt.impl

/**
 * Геометрия печатной формы: ширина бумаги и холста по классу самой формы.
 *
 * Класс ленты стоит на корневом элементе формы, а не только в стилях:
 * таблица стилей описывает все ленты сразу, и поиск подстроки находил
 * 58 мм в любом чеке — 80-миллиметровая форма печаталась на узкой странице.
 *
 * «Полноэкранная» форма задумана на девятьсот точек, а холст ленты
 * в три раза уже: на нём она выглядела как ещё одна лента 80 мм,
 * и «полноэкранный» макет в настройках ничего не менял. Ей — страница A4
 * и холст её ширины.
 */
internal object FormGeometry {

    /** Логическая ширина холста ленты в точках CSS. */
    const val TAPE_CANVAS_PX: Int = 380

    /** Холст полноэкранной формы — ширина A4 в точках CSS при 96 dpi. */
    const val FULLSCREEN_CANVAS_PX: Int = 794

    const val TAPE_80_MM: Double = 80.0
    const val TAPE_58_MM: Double = 58.0
    const val PAGE_A4_MM: Double = 210.0

    private val NARROW_TAPE = Regex("""class="[^"]*\btape-58mm\b""")
    private val WIDE_FORM = Regex("""class="[^"]*\btape-fullscreen\b""")

    /** Ширина бумаги по классу формы. */
    fun paperWidthMm(html: String): Double = when {
        NARROW_TAPE.containsMatchIn(html) -> TAPE_58_MM
        WIDE_FORM.containsMatchIn(html) -> PAGE_A4_MM
        else -> TAPE_80_MM
    }

    /** Холст снимка: лента рисуется на своём, полноэкранная форма — на ширине A4. */
    fun canvasWidthPx(html: String): Int =
        if (WIDE_FORM.containsMatchIn(html)) FULLSCREEN_CANVAS_PX else TAPE_CANVAS_PX
}
