package io.github.texport.superkassa.jvm.receipt.impl

import java.awt.image.BufferedImage

/**
 * Пустой низ снимка.
 *
 * Окно снимка выше документа: высота страницы известна лишь приблизительно,
 * и запас берётся с избытком, чтобы длинный Z-отчёт не обрезало. Лишний
 * низ — сплошной фон страницы, и его видно по самому нижнему ряду точек.
 */
internal object SnapshotCrop {

    /** Отступ под последней строкой: без него низ документа выглядит обрубленным. */
    private const val BOTTOM_PADDING_PX = 20

    /** Снимок без пустого низа; документ во всю высоту окна остаётся как есть. */
    fun withoutEmptyBottom(image: BufferedImage): BufferedImage {
        val background = image.getRGB(0, image.height - 1)
        val lastPainted = (image.height - 1 downTo 0).firstOrNull { !image.rowIs(it, background) } ?: return image
        val height = minOf(image.height, lastPainted + 1 + BOTTOM_PADDING_PX)
        return if (height < image.height) image.getSubimage(0, 0, image.width, height) else image
    }

    private fun BufferedImage.rowIs(y: Int, colour: Int): Boolean =
        (0 until width).all { getRGB(it, y) == colour }
}
