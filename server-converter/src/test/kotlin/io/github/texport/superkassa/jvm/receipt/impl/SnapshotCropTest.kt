package io.github.texport.superkassa.jvm.receipt.impl

import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Пустой низ снимка срезается, а сам документ — нет.
 */
class SnapshotCropTest {

    private fun snapshot(documentHeight: Int, totalHeight: Int) =
        BufferedImage(10, totalHeight, BufferedImage.TYPE_INT_RGB).apply {
            createGraphics().run {
                color = Color.WHITE
                fillRect(0, 0, 10, totalHeight)
                color = Color.BLACK
                fillRect(0, 0, 10, documentHeight)
                dispose()
            }
        }

    @Test
    fun `под документом остаётся отступ, остальное срезается`() {
        assertEquals(120, SnapshotCrop.withoutEmptyBottom(snapshot(100, 900)).height)
    }

    @Test
    fun `документ во всю высоту окна остаётся как есть`() {
        assertEquals(300, SnapshotCrop.withoutEmptyBottom(snapshot(300, 300)).height)
    }

    @Test
    fun `документ почти во всю высоту не срезается ради отступа`() {
        assertEquals(300, SnapshotCrop.withoutEmptyBottom(snapshot(295, 300)).height)
    }

    @Test
    fun `сплошной фон без документа остаётся как есть`() {
        assertEquals(400, SnapshotCrop.withoutEmptyBottom(snapshot(0, 400)).height)
    }
}
