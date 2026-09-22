package io.github.texport.superkassa.jvm.receipt.impl

/**
 * Геометрия печатной формы: ширина бумаги и холста по классу самой формы.
 *
 * Класс ленты стоит на корневом элементе формы, а не только в стилях:
 * таблица стилей описывает все ленты сразу, и поиск подстроки находил
 * 58 мм в любом чеке — 80-миллиметровая форма печаталась на узкой странице.
 *
 * Ширина холста — ширина формы плюс поле фона по обе стороны от неё:
 * в браузере лента лежит карточкой на фоне страницы, и образ повторяет
 * этот вид. Полноэкранная форма свёрстана на девятьсот точек, и холст
 * ей нужен свой: на холсте ленты она выглядела как ещё одна лента 80 мм,
 * а на странице A4 (794 точки) переносила строки не там, где в браузере.
 */
internal object FormGeometry {

    /** Точек CSS в одном миллиметре: 96 точек на дюйм. */
    const val PX_PER_MM: Double = 96 / 25.4

    /** Поле фона слева и справа от формы, в точках CSS. */
    const val BACKGROUND_MARGIN_PX: Int = 39

    const val TAPE_80_MM: Double = 80.0
    const val TAPE_58_MM: Double = 58.0

    /** Собственная ширина полноэкранной формы в точках CSS: на неё она свёрстана. */
    const val FULLSCREEN_FORM_PX: Int = 900

    /** Та же ширина в миллиметрах: ею задаётся страница PDF полноэкранной формы. */
    const val FULLSCREEN_MM: Double = FULLSCREEN_FORM_PX / PX_PER_MM

    /** Логическая ширина холста ленты в точках CSS. */
    const val TAPE_CANVAS_PX: Int = 380

    /** Холст полноэкранной формы: её ширина плюс то же поле фона, что у ленты. */
    const val FULLSCREEN_CANVAS_PX: Int = FULLSCREEN_FORM_PX + 2 * BACKGROUND_MARGIN_PX

    private val NARROW_TAPE = Regex("""class="[^"]*\btape-58mm\b""")
    private val WIDE_FORM = Regex("""class="[^"]*\btape-fullscreen\b""")

    /** Ширина бумаги по классу формы. */
    fun paperWidthMm(html: String): Double = when {
        NARROW_TAPE.containsMatchIn(html) -> TAPE_58_MM
        WIDE_FORM.containsMatchIn(html) -> FULLSCREEN_MM
        else -> TAPE_80_MM
    }

    /** Холст снимка: лента рисуется на своём, полноэкранная форма — на своём. */
    fun canvasWidthPx(html: String): Int =
        if (WIDE_FORM.containsMatchIn(html)) FULLSCREEN_CANVAS_PX else TAPE_CANVAS_PX

    /** Ширина формы в целых точках CSS: столько окна нужно её вёрстке. */
    fun formWidthPx(html: String): Int = Math.round(paperWidthMm(html) * PX_PER_MM).toInt()
}
