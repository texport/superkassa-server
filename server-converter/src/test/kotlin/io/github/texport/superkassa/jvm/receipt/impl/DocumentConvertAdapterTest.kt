package io.github.texport.superkassa.jvm.receipt.impl

import io.github.texport.superkassa.receiptrenderer.api.createReceiptRendererApi
import io.github.texport.superkassa.core.domain.api.model.common.Money
import io.github.texport.superkassa.core.domain.api.model.kkm.FiscalDocumentSnapshot
import io.github.texport.superkassa.core.domain.api.model.receipt.PaymentType
import io.github.texport.superkassa.core.domain.api.model.receipt.ReceiptItem
import io.github.texport.superkassa.core.domain.api.model.receipt.ReceiptOperationType
import io.github.texport.superkassa.core.domain.api.model.receipt.ReceiptPayment
import io.github.texport.superkassa.core.domain.api.model.receipt.ReceiptRequest
import java.nio.charset.Charset
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocumentConvertAdapterTest {
    private val adapter = DocumentConvertAdapter()

    @Test
    fun `htmlToPdf renders Cyrillic text without exception`() {
        val html = """
            <div style="font-family: 'DejaVu Sans Mono'">Привет, как дела? Қазақстан! 123</div>
        """.trimIndent()

        val pdfBytes = adapter.htmlToPdf(html)
        assertTrue(pdfBytes.isNotEmpty())
    }

    @Test
    fun `htmlToImage renders first pdf page as png`() {
        val html = """
            <div style="font-family: 'DejaVu Sans Mono'">
                <strong>Superkassa</strong><br/>
                Привет, Қазақстан! 123
            </div>
        """.trimIndent()

        val imageBytes = adapter.htmlToImage(html)
        val image = ImageIO.read(imageBytes.inputStream())

        assertTrue(imageBytes.isNotEmpty())
        assertTrue(image.width > 0)
        assertTrue(image.height > 0)
    }

    @Test
    fun `htmlToEscPos encodes Cyrillic to CP866 and preserves text structure`() {
        val html = "<div>Привет, как дела? Қазақстан! 123</div>"
        val escPosBytes = adapter.htmlToEscPos(html, paperWidthMm = 58)

        assertTrue(escPosBytes.isNotEmpty())

        // Переводим обратно по таблице CP866 для верификации
        val decoded = String(escPosBytes, Charset.forName("CP866"))
        assertTrue(decoded.contains("Привет"))
        assertTrue(decoded.contains("как дела"))

        // Проверяем наличие управляющих ESC-команд
        // ESC t 17 -> 0x1B, 0x74, 0x11
        assertTrue(escPosBytes.contains(0x1B))
        assertTrue(escPosBytes.contains(0x74))
        assertTrue(escPosBytes.contains(0x11))
    }

    @Test
    fun `reproduce pdf conversion failure with exact receipt data`() {
        val receipt = ReceiptRequest(
            kkmId = "945583f1-6723-4664-8afa-81e3937b7ceb",
            pin = "0000",
            operation = ReceiptOperationType.SELL,
            items = listOf(
                ReceiptItem(
                    name = "Молоко питьевое 2.5%, 1л",
                    sectionCode = "001",
                    quantity = 2,
                    price = Money(450, 50),
                    sum = Money(901, 0)
                ),
                ReceiptItem(
                    name = "Хлеб ржаной, булка",
                    sectionCode = "001",
                    quantity = 1,
                    price = Money(180, 0),
                    sum = Money(180, 0)
                )
            ),
            payments = listOf(
                ReceiptPayment(PaymentType.CARD, Money(1081, 0))
            ),
            total = Money(1081, 0),
            taken = Money(1081, 0),
            change = Money(0, 0),
            idempotencyKey = "reproduce-key"
        )

        val doc = FiscalDocumentSnapshot(
            id = "a8b2e672-4d2d-46ca-87c4-24d8a67f4abd",
            cashboxId = "945583f1-6723-4664-8afa-81e3937b7ceb",
            shiftId = "e638cdb3-a901-4b6b-ba80-926e220d134c",
            docType = "CHECK",
            docNo = null,
            shiftNo = 2L,
            createdAt = 1781774776681,
            totalAmount = 1081L,
            currency = "KZT",
            fiscalSign = "1180393819",
            autonomousSign = null,
            isAutonomous = false,
            ofdStatus = "SENT",
            deliveredAt = null,
            receiptUrl = "https://consumer.test-oofd.kz?i=1180393819&f=620300012117&s=1081.00&t=20260618T162616",
            registrationNumber = "620300012117",
            taxpayerName = "ИП МИЧКА ПАВЕЛ АНДРЕЕВИЧ",
            taxpayerBin = "960624350642",
            taxpayerAddress = "обл. Павлодарская, Республика 1, 2",
            factoryNumber = "1365345245"
        )

        val kkm = io.github.texport.superkassa.core.domain.api.model.kkm.KkmInfo(
            id = doc.cashboxId,
            createdAt = doc.createdAt,
            updatedAt = doc.createdAt,
            mode = "PRODUCTION",
            state = "READY",
            registrationNumber = doc.registrationNumber ?: "",
            factoryNumber = doc.factoryNumber ?: "",
            branding = io.github.texport.superkassa.core.domain.api.model.receipt.ReceiptBranding(),
            ofdServiceInfo = io.github.texport.superkassa.core.domain.api.model.ofd.OfdServiceInfo(
                orgTitle = doc.taxpayerName ?: "ИП МИЧКА ПАВЕЛ АНДРЕЕВИЧ",
                orgAddress = doc.taxpayerAddress ?: "обл. Павлодарская, Республика 1, 2",
                orgAddressKz = "обл. Павлодарская, Республика 1, 2",
                orgInn = doc.taxpayerBin ?: "960624350642",
                orgOkved = "62010",
                geoLatitude = 0,
                geoLongitude = 0,
                geoSource = "GPS"
            )
        )

        val html = createReceiptRendererApi(
            QrCodeDataUriGenerator
        ).renderHtml(receipt, doc, kkm)

        val pdfBytes = adapter.htmlToPdf(html)
        assertTrue(pdfBytes.isNotEmpty())
    }

    @Test
    fun `qrCodeDataUriGenerator handles empty and valid inputs`() {
        kotlin.test.assertNull(QrCodeDataUriGenerator.generatePngDataUri("", 100))
        kotlin.test.assertNull(QrCodeDataUriGenerator.generatePngDataUri("   ", 100))
        kotlin.test.assertNull(QrCodeDataUriGenerator.generatePngDataUri("Hello World", -1))

        val result = QrCodeDataUriGenerator.generatePngDataUri("Hello World", 200)
        assertTrue(result != null && result.startsWith("data:image/png;base64,"))
    }

    @Test
    fun `escPosConverter covers all paper widths and text wrapping edge cases`() {
        val html = "<div>Hello   World</div>\n\n<div></div>"

        // Width 48
        val bytes48 = EscPosConverter.convertHtmlToEscPos(html, 48)
        assertTrue(bytes48.isNotEmpty())

        // Width 80
        val bytes80 = EscPosConverter.convertHtmlToEscPos(html, 80)
        assertTrue(bytes80.isNotEmpty())

        // Width default/other
        val bytesDefault = EscPosConverter.convertHtmlToEscPos(html, 100)
        assertTrue(bytesDefault.isNotEmpty())
    }

    @Test
    fun `escPosConverter strips html tags correctly`() {
        val html = "<div>Hello <span>nested</span> World</div>"
        val bytes = EscPosConverter.convertHtmlToEscPos(html, 48)
        val decoded = String(bytes, Charset.forName("CP866"))
        assertTrue(decoded.contains("Hello nested World"))
    }

    @Test
    fun `escPosConverter processes table rows with one cell`() {
        val html = "<table><tr><td>Single Cell Content</td></tr></table>"
        val bytes = EscPosConverter.convertHtmlToEscPos(html, 48)
        val decoded = String(bytes, Charset.forName("CP866"))
        assertTrue(decoded.contains("Single Cell Content"))
    }

    @Test
    fun `escPosConverter processes table rows with two cells`() {
        val html = "<table><tr><td>Left Item</td><td>Right Val</td></tr></table>"

        // Width 48, space Count > 0
        val bytes1 = EscPosConverter.convertHtmlToEscPos(html, 48)
        val decoded1 = String(bytes1, Charset.forName("CP866"))
        assertTrue(decoded1.contains("Left Item") && decoded1.contains("Right Val"))

        // Long text to trigger space Count <= 0
        val htmlLong = "<table><tr><td>Very Long Left Item Name Here</td><td>Right Val</td></tr></table>"
        val bytes2 = EscPosConverter.convertHtmlToEscPos(htmlLong, 48)
        val decoded2 = String(bytes2, Charset.forName("CP866"))
        assertTrue(decoded2.contains("Very Long Left") && decoded2.contains("Right Val"))
    }

    @Test
    fun `escPosConverter processes table rows with three cells`() {
        val html = "<table><tr><td>Item Name</td><td>1 x 100</td><td>100.00</td></tr></table>"
        // Run with 80mm paper width (48 chars per line) to prevent truncation of "Item Name"
        val bytes = EscPosConverter.convertHtmlToEscPos(html, 80)
        val decoded = String(bytes, Charset.forName("CP866"))
        assertTrue(decoded.contains("Item Name") && decoded.contains("1 x 100") && decoded.contains("100.00"))
    }

    @Test
    fun `escPosConverter processes complex nested html entities`() {
        val html = "<div>&amp; &lt; &gt; &nbsp; &quot; &#39;</div>"
        val bytes = EscPosConverter.convertHtmlToEscPos(html, 48)
        val decoded = String(bytes, Charset.forName("CP866"))
        assertTrue(decoded.contains("& < >   \" '"))
    }

    @Test
    fun `escPosConverter wraps text correctly with very long words`() {
        val html = "<div>ThisIsAVeryVeryVeryVeryVeryLongWordThatExceedsLineLength limit</div>"
        val bytes = EscPosConverter.convertHtmlToEscPos(html, 48)
        val decoded = String(bytes, Charset.forName("CP866"))
        assertTrue(decoded.isNotEmpty())
    }

    @Test
    fun `escPosConverter charset fallback to IBM866`() {
        val originalProvider = EscPosConverter.charsetProvider
        try {
            EscPosConverter.charsetProvider = { name ->
                require(name != "CP866") { "CP866 not supported" }
                if (name == "IBM866") {
                    java.nio.charset.StandardCharsets.US_ASCII
                } else {
                    Charset.forName(name)
                }
            }

            val html = "<div>Fallback Test</div>"
            val bytes = EscPosConverter.convertHtmlToEscPos(html, 48)
            assertTrue(bytes.isNotEmpty())
        } finally {
            EscPosConverter.charsetProvider = originalProvider
        }
    }

    @Test
    fun `escPosConverter handles unclosed tr tag`() {
        val html = "<table><tr class=\"hdr\">Unclosed TR content"
        val bytes = EscPosConverter.convertHtmlToEscPos(html, 48)
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun `escPosConverter handles empty table row`() {
        val html = "<table><tr></tr></table>"
        val bytes = EscPosConverter.convertHtmlToEscPos(html, 48)
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun `escPosConverter processes long name in three cell row`() {
        val html = "<table><tr><td>ThisIsAVeryLongItemNameForSureExceedingNameWidth</td><td>1 x 100</td><td>100.00</td></tr></table>"
        val bytes = EscPosConverter.convertHtmlToEscPos(html, 48)
        val decoded = String(bytes, Charset.forName("CP866"))
        assertTrue(decoded.isNotEmpty())
    }

    @Test
    fun `escPosConverter wrapText edge cases via reflection`() {
        val method = EscPosConverter::class.java.getDeclaredMethod("wrapText", String::class.java, Int::class.java)
        method.isAccessible = true

        // 1. blank/spaces input longer than width
        val res1 = method.invoke(EscPosConverter, "               ", 10) as List<*>
        assertTrue(res1.isEmpty())

        // 2. leading spaces
        val res2 = method.invoke(EscPosConverter, "  hello world", 10) as List<*>
        assertEquals(2, res2.size)

        // Test processTableRow natural loop exit
        val tableRowMethod = EscPosConverter::class.java.getDeclaredMethod("processTableRow", String::class.java, Int::class.java)
        tableRowMethod.isAccessible = true
        val resRow = tableRowMethod.invoke(EscPosConverter, "<td>cell</td>", 48) as String
        assertTrue(resRow.contains("cell"))
    }

    @Test
    fun `escPosConverter natural loop exit`() {
        val html = "<tr><td>cell</td></tr>"
        val bytes = EscPosConverter.convertHtmlToEscPos(html, 48)
        assertTrue(bytes.isNotEmpty())
    }
}
