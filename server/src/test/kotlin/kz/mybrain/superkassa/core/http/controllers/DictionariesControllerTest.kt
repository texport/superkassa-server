package kz.mybrain.superkassa.core.http.controllers

import io.github.texport.superkassa.core.presentation.api.SuperkassaApi
import io.github.texport.superkassa.core.presentation.api.model.common.VatRateResponse
import io.github.texport.superkassa.core.presentation.api.model.reference.AuthModeResponse
import io.github.texport.superkassa.core.presentation.api.model.reference.BrandingColorResponse
import io.github.texport.superkassa.core.presentation.api.model.reference.CashOperationTypeResponse
import io.github.texport.superkassa.core.presentation.api.model.reference.CoreModeResponse
import io.github.texport.superkassa.core.presentation.api.model.reference.DeliveryStatusResponse
import io.github.texport.superkassa.core.presentation.api.model.reference.DocumentTypeResponse
import io.github.texport.superkassa.core.presentation.api.model.reference.KkmModeResponse
import io.github.texport.superkassa.core.presentation.api.model.reference.KkmStateResponse
import io.github.texport.superkassa.core.presentation.api.model.reference.OfdCommandStatusResponse
import io.github.texport.superkassa.core.presentation.api.model.reference.OfdCommandTypeResponse
import io.github.texport.superkassa.core.presentation.api.model.reference.OfdEnvironmentResponse
import io.github.texport.superkassa.core.presentation.api.model.reference.OfdProviderResponse
import io.github.texport.superkassa.core.presentation.api.model.reference.PaperWidthResponse
import io.github.texport.superkassa.core.presentation.api.model.reference.PaymentTypeResponse
import io.github.texport.superkassa.core.presentation.api.model.reference.PrintDocumentTypeResponse
import io.github.texport.superkassa.core.presentation.api.model.reference.ReceiptDomainTypeResponse
import io.github.texport.superkassa.core.presentation.api.model.reference.ReceiptLanguageResponse
import io.github.texport.superkassa.core.presentation.api.model.reference.ReceiptLayoutTypeResponse
import io.github.texport.superkassa.core.presentation.api.model.reference.ReceiptOperationTypeResponse
import io.github.texport.superkassa.core.presentation.api.model.reference.ShiftStatusResponse
import io.github.texport.superkassa.core.presentation.api.model.reference.TaxRegimeResponse
import io.github.texport.superkassa.core.presentation.api.model.reference.TrilingualMessageResponse
import io.github.texport.superkassa.core.presentation.api.model.reference.UserRoleResponse
import io.mockk.every
import io.mockk.mockk
import kz.mybrain.superkassa.core.application.http.controllers.DictionariesController
import kotlin.test.Test
import kotlin.test.assertEquals

class DictionariesControllerTest {

    @Test
    fun `dictionaries controller delegates all calls to kkmService`() {
        val kkmService = mockk<SuperkassaApi>()
        val controller = DictionariesController(kkmService)

        // Mock payment types
        every { kkmService.getPaymentTypes() } returns listOf(
            PaymentTypeResponse("CASH", TrilingualMessageResponse("Наличные", "Қолма-қол", "Cash"), supported = true)
        )
        // Mock document types
        every { kkmService.getDocumentTypes() } returns listOf(
            DocumentTypeResponse("SALE", TrilingualMessageResponse("Продажа", "Сату", "Sale"))
        )
        // Mock user roles
        every { kkmService.getUserRoles() } returns listOf(
            UserRoleResponse("CASHIER", TrilingualMessageResponse("Кассир", "Кассир", "Cashier"))
        )
        // Mock tax regimes
        every { kkmService.getTaxRegimes() } returns listOf(
            TaxRegimeResponse("NO_VAT", TrilingualMessageResponse("Без НДС", "ҚҚС-сыз", "No VAT"))
        )
        // Mock paper widths
        every { kkmService.getPaperWidths() } returns listOf(
            PaperWidthResponse("80", TrilingualMessageResponse("80мм", "80мм", "80mm"))
        )
        // Mock branding colors
        every { kkmService.getBrandingColors() } returns listOf(
            BrandingColorResponse("#007AFF", TrilingualMessageResponse("Синий", "Көк", "Blue"))
        )
        // Mock states
        every { kkmService.getKkmStates() } returns listOf(
            KkmStateResponse("ACTIVE", TrilingualMessageResponse("Активна", "Белсенді", "Active"))
        )
        // Mock modes
        every { kkmService.getKkmModes() } returns listOf(
            KkmModeResponse("ONLINE", TrilingualMessageResponse("Онлайн", "Онлайн", "Online"))
        )
        // Mock shift statuses
        every { kkmService.getShiftStatuses() } returns listOf(
            ShiftStatusResponse("OPEN", TrilingualMessageResponse("Открыта", "Ашық", "Open"))
        )
        // Mock delivery statuses
        every { kkmService.getDeliveryStatuses() } returns listOf(
            DeliveryStatusResponse("ONLINE_OK", TrilingualMessageResponse("Доставлен", "Жеткізілді", "Delivered"))
        )
        // Mock OFD command statuses
        every { kkmService.getOfdCommandStatuses() } returns listOf(
            OfdCommandStatusResponse("OK", TrilingualMessageResponse("Успешно", "Сәтті", "Success"))
        )
        // Mock receipt operation types
        every { kkmService.getReceiptOperationTypes() } returns listOf(
            ReceiptOperationTypeResponse("SELL", TrilingualMessageResponse("Продажа", "Сату", "Sale"))
        )
        // Mock ofd environments
        every { kkmService.getOfdEnvironments() } returns listOf(
            OfdEnvironmentResponse(
                "PROD",
                TrilingualMessageResponse("Продуктивный сервер", "Продуктивті сервер", "Production Environment")
            )
        )
        // Mock ofd providers
        every { kkmService.getOfdProviders() } returns listOf(
            OfdProviderResponse(
                "KAZAKHTELECOM",
                TrilingualMessageResponse("АО «Казахтелеком»", "«Қазақтелеком» АҚ", "JSC Kazakhtelecom"),
                "oofd.kz"
            )
        )
        // Mock core modes
        every { kkmService.getCoreModes() } returns listOf(
            CoreModeResponse(
                "SERVER",
                TrilingualMessageResponse("Серверный (Кластер)", "Серверлік (Кластер)", "Server (Cluster)")
            )
        )
        // Mock auth modes
        every { kkmService.getAuthModes() } returns listOf(
            AuthModeResponse("BEARER", TrilingualMessageResponse("Bearer токен", "Bearer токені", "Bearer Token"))
        )
        // Mock receipt languages
        every { kkmService.getReceiptLanguages() } returns listOf(
            ReceiptLanguageResponse(
                "MIXED",
                TrilingualMessageResponse("Смешанный (Двуязычный)", "Аралас (Екі тілді)", "Mixed (Bilingual)")
            )
        )
        // Mock receipt layout types
        every { kkmService.getReceiptLayoutTypes() } returns listOf(
            ReceiptLayoutTypeResponse(
                "TAPE_80MM",
                TrilingualMessageResponse("Чековая лента 80мм", "Шекаралық таспа 80мм", "80mm Receipt Tape")
            )
        )
        // Mock print document types
        every { kkmService.getPrintDocumentTypes() } returns listOf(
            PrintDocumentTypeResponse(
                "DOCUMENT",
                TrilingualMessageResponse(
                    "Фискальный документ (Чек)",
                    "Фискалдық құжат (Шек)",
                    "Fiscal Document (Receipt)"
                )
            )
        )
        // Mock ofd command types
        every { kkmService.getOfdCommandTypes() } returns listOf(
            OfdCommandTypeResponse(
                "TICKET",
                TrilingualMessageResponse("Команда чека", "Шек командасы", "Ticket Command")
            )
        )
        // Mock cash operation types
        every { kkmService.getCashOperationTypes() } returns listOf(
            CashOperationTypeResponse("CASH_OUT", TrilingualMessageResponse("Изъятие наличных", "Алу", "Cash Out"))
        )

        // Verifications
        assertEquals("CASH", controller.getPaymentTypes().first().code)
        assertEquals("SALE", controller.getDocumentTypes().first().code)
        assertEquals("CASHIER", controller.getUserRoles().first().code)
        assertEquals("NO_VAT", controller.getTaxRegimes().first().code)
        assertEquals("80", controller.getPaperWidths().first().code)
        assertEquals("#007AFF", controller.getBrandingColors().first().code)
        assertEquals("ACTIVE", controller.getKkmStates().first().code)
        assertEquals("ONLINE", controller.getKkmModes().first().code)
        assertEquals("OPEN", controller.getShiftStatuses().first().code)
        assertEquals("ONLINE_OK", controller.getDeliveryStatuses().first().code)
        assertEquals("OK", controller.getOfdCommandStatuses().first().code)
        assertEquals("SELL", controller.getReceiptOperationTypes().first().code)
        assertEquals("PROD", controller.getOfdEnvironments().first().code)
        assertEquals("KAZAKHTELECOM", controller.getOfdProviders().first().code)
        assertEquals("oofd.kz", controller.getOfdProviders().first().website)
        assertEquals("SERVER", controller.getCoreModes().first().code)
        assertEquals("BEARER", controller.getAuthModes().first().code)
        assertEquals("MIXED", controller.getReceiptLanguages().first().code)
        assertEquals("TAPE_80MM", controller.getReceiptLayoutTypes().first().code)
        assertEquals("DOCUMENT", controller.getPrintDocumentTypes().first().code)
        assertEquals("TICKET", controller.getOfdCommandTypes().first().code)
        assertEquals("CASH_OUT", controller.getCashOperationTypes().first().code)
    }

    @Test
    fun `справочники ставок НДС и видов отрасли отдаются узлом`() {
        val kkmService = mockk<SuperkassaApi>()
        val controller = DictionariesController(kkmService)

        // Ставки НДС берутся из того же источника, что и GET /vat-rates.
        every { kkmService.listVatRates() } returns listOf(
            VatRateResponse(
                code = "VAT_16",
                percent = 16,
                description = "НДС 16%",
                name = TrilingualMessageResponse("НДС 16%", "ҚҚС 16%", "VAT 16%")
            )
        )
        every { kkmService.getReceiptDomainTypes() } returns listOf(
            ReceiptDomainTypeResponse("DOMAIN_TAXI", TrilingualMessageResponse("Такси", "Такси", "Taxi"))
        )

        assertEquals("VAT_16", controller.getVatGroups().first().code)
        assertEquals(16, controller.getVatGroups().first().percent)
        assertEquals("ҚҚС 16%", controller.getVatGroups().first().name.kk)

        assertEquals("DOMAIN_TAXI", controller.getDomainKinds().first().code)
        assertEquals("Такси", controller.getDomainKinds().first().name.ru)
    }
}
