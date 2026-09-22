package kz.mybrain.superkassa.core.application.protocol

import io.github.texport.superkassa.core.domain.api.exception.NotFoundException
import io.github.texport.superkassa.core.domain.api.exception.ValidationException
import io.github.texport.superkassa.core.domain.api.model.receipt.ReceiptLayoutType
import io.github.texport.superkassa.core.domain.api.model.zxreport.ZxReportInput
import io.github.texport.superkassa.core.domain.api.port.integration.DocumentConvertPort
import io.github.texport.superkassa.core.domain.api.port.integration.StoragePort
import io.github.texport.superkassa.core.domain.api.port.internal.ReceiptRenderPort
import io.github.texport.superkassa.core.presentation.api.SuperkassaApi
import io.github.texport.superkassa.jvm.receipt.impl.QrCodeDataUriGenerator
import io.github.texport.superkassa.receiptrenderer.api.createReceiptRendererApi
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import io.github.texport.superkassa.core.presentation.api.model.receipt.ReceiptLayoutType as RequestedLayout

/**
 * Печать документа по пакету протокола.
 *
 * Рисовальщик здесь настоящий: проверяется не только то, какой его вход
 * выбран, но и то, что переведённый пакет вообще рисуется — иначе перевод
 * ломался бы только у владельца на экране.
 */
class ProtocolDocumentPrinterTest {

    private val kkmService = mockk<SuperkassaApi>(relaxed = true)
    private val storage = mockk<StoragePort>()
    private val converter = mockk<DocumentConvertPort>()
    private val renderer = createReceiptRendererApi(QrCodeDataUriGenerator)
    private val printer = ProtocolDocumentPrinter(kkmService, storage, renderer, converter)

    init {
        every { storage.findKkm(KKM) } returns ProtocolPackets.localKkm()
    }

    @Test
    fun `чек рисуется реквизитами документа, а не рисующей кассы`() {
        val html = printer.html(KKM, PIN, ProtocolPackets.receipt(), null)

        assertContains(html, "NZ7700123456")
        assertContains(html, "ТОО Пример")
        assertContains(html, "Хлеб")
        assertContains(html, "987654321012")
        assertTrue(html.startsWith("<!DOCTYPE html>") || html.contains("<html"), html.take(80))
    }

    @Test
    fun `отчёт и кассовый ордер рисуются тем же рисовальщиком`() {
        assertContains(printer.html(KKM, PIN, ProtocolPackets.report(), null), "NZ7700123456")
        assertContains(printer.html(KKM, PIN, ProtocolPackets.closeShift(), null), "NZ7700123456")
        // Сумма на форме набрана так же, как на экране кассы: разряды
        // разделены неразрывным пробелом, дробная часть запятой, знак тенге.
        assertContains(printer.html(KKM, PIN, ProtocolPackets.placement(), null), "1\u00A0000,50\u00A0₸")
    }

    @Test
    fun `ширина ленты берётся из запроса`() {
        val narrow = printer.html(KKM, PIN, ProtocolPackets.receipt(), RequestedLayout.TAPE_58MM)

        assertContains(narrow, "tape-58mm")
    }

    @Test
    fun `пин проверяется до всякой отрисовки`() {
        printer.html(KKM, PIN, ProtocolPackets.receipt(), null)

        verify(exactly = 1) { kkmService.authenticate(KKM, PIN) }
    }

    @Test
    fun `X-отчёт и Z-отчёт идут разными входами рисовальщика`() {
        val drawing = mockk<ReceiptRenderPort>(relaxed = true)
        val checking = ProtocolDocumentPrinter(kkmService, storage, drawing, converter)

        checking.html(KKM, PIN, ProtocolPackets.report(), null)
        checking.html(KKM, PIN, ProtocolPackets.report(kind = "REPORT_Z"), null)

        verify(exactly = 1) { drawing.renderXReportHtml(any<ZxReportInput>(), any(), any(), any(), any()) }
        verify(exactly = 1) { drawing.renderCloseShiftHtml(any<ZxReportInput>(), any(), any(), any(), any()) }
    }

    @Test
    fun `растр и PDF получаются из той же разметки`() {
        every { converter.htmlToImage(any()) } returns PNG
        every { converter.htmlToPdf(any()) } returns PDF
        val drawn = slot<String>()

        assertEquals(PNG.toList(), printer.image(KKM, PIN, ProtocolPackets.receipt(), null).toList())
        assertEquals(PDF.toList(), printer.pdf(KKM, PIN, ProtocolPackets.receipt(), null).toList())

        verify { converter.htmlToImage(capture(drawn)) }
        assertContains(drawn.captured, "Хлеб")
    }

    @Test
    fun `неизвестная касса, нечитаемый пакет и команда без документа отвечают отказом`() {
        every { storage.findKkm("другая") } returns null

        assertFailsWith<NotFoundException> { printer.html("другая", PIN, ProtocolPackets.receipt(), null) }
        assertFailsWith<ValidationException> { printer.html(KKM, PIN, "не JSON вовсе", null) }
        assertFailsWith<ValidationException> {
            printer.html(KKM, PIN, """{"request": {"command": "COMMAND_INFO"}}""", null)
        }
    }

    @Test
    fun `разметка кассы и разметка запроса называют одно и то же`() {
        RequestedLayout.entries.forEach { requested ->
            assertEquals(requested.name, ReceiptLayoutType.valueOf(requested.name).name)
        }
    }

    private companion object {
        const val KKM = "4166498c-d0c1-406e-863d-20458dfd3040"
        const val PIN = "4827"
        val PNG = byteArrayOf(1, 2, 3)
        val PDF = byteArrayOf(4, 5, 6)
    }
}
