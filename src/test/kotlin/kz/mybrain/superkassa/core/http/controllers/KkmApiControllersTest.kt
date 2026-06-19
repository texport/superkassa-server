package kz.mybrain.superkassa.core.http.controllers

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kz.mybrain.superkassa.core.application.http.controllers.KkmController
import kz.mybrain.superkassa.core.application.http.controllers.KkmCountersController
import kz.mybrain.superkassa.core.application.http.controllers.KkmDecommissioningController
import kz.mybrain.superkassa.core.application.http.controllers.KkmDiagnosticsController
import kz.mybrain.superkassa.core.application.http.controllers.KkmManagementController
import kz.mybrain.superkassa.core.application.http.controllers.KkmProgrammingController
import kz.mybrain.superkassa.core.application.http.controllers.KkmUsersController
import kz.mybrain.superkassa.core.application.model.AutoCloseShiftRequest
import kz.mybrain.superkassa.core.application.model.FactoryNumberResponse
import kz.mybrain.superkassa.core.application.model.KkmInitSimpleRequest
import kz.mybrain.superkassa.core.application.model.KkmTaxSettingsUpdateRequest
import kz.mybrain.superkassa.core.application.model.OfdAuthInfoResponse
import kz.mybrain.superkassa.core.application.model.OfdTokenUpdateRequest
import kz.mybrain.superkassa.core.application.model.UserCreateRequest
import kz.mybrain.superkassa.core.application.model.UserUpdateRequest
import kz.mybrain.superkassa.core.application.service.KkmService
import kz.mybrain.superkassa.core.domain.model.CounterSnapshot
import kz.mybrain.superkassa.core.domain.model.DeliveryStatus
import kz.mybrain.superkassa.core.domain.model.FiscalDocumentSnapshot
import kz.mybrain.superkassa.core.domain.model.KkmInfo
import kz.mybrain.superkassa.core.domain.model.KkmMode
import kz.mybrain.superkassa.core.domain.model.KkmState
import kz.mybrain.superkassa.core.domain.model.OfdCommandResult
import kz.mybrain.superkassa.core.domain.model.OfdCommandStatus
import kz.mybrain.superkassa.core.domain.model.ReportResult
import kz.mybrain.superkassa.core.domain.model.ShiftInfo
import kz.mybrain.superkassa.core.domain.model.ShiftStatus
import kz.mybrain.superkassa.core.domain.model.TaxRegime
import kz.mybrain.superkassa.core.domain.model.UserRole
import kz.mybrain.superkassa.core.domain.model.VatGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KkmApiControllersTest {

    private val service = mockk<KkmService>()

    private val kkmController = KkmController(service)
    private val countersController = KkmCountersController(service)
    private val decommissioningController = KkmDecommissioningController(service)
    private val diagnosticsController = KkmDiagnosticsController(service)
    private val managementController = KkmManagementController(service)
    private val programmingController = KkmProgrammingController(service)
    private val usersController = KkmUsersController(service)

    @Test
    fun `kkm shift and document endpoints delegate to service with parsed pin`() {
        val shift = ShiftInfo("shift-1", "kkm-1", 10, ShiftStatus.OPEN, 1000)
        val report = ReportResult("z-1", DeliveryStatus.ONLINE_OK)
        val doc =
            FiscalDocumentSnapshot(
                id = "doc-1",
                cashboxId = "kkm-1",
                shiftId = "shift-1",
                docType = "CHECK",
                docNo = 1,
                shiftNo = 10,
                createdAt = 1001,
                totalAmount = 100,
                currency = "KZT",
                fiscalSign = "fs",
                autonomousSign = null,
                isAutonomous = false,
                ofdStatus = "SENT",
                deliveredAt = 1002
            )
        every { service.openShift("kkm-1", "1111") } returns shift
        every { service.closeShift("kkm-1", "1111") } returns report
        every { service.listShifts("kkm-1", 50, 10, "1111") } returns listOf(shift)
        every { service.listShiftDocuments("kkm-1", "shift-1", 100, 0, "1111") } returns listOf(doc)
        every { service.getOpenShift("kkm-1", "1111") } returns shift
        every { service.listFiscalDocumentsByPeriod("kkm-1", 1L, 2L, 20, 0, "1111") } returns listOf(doc)

        assertEquals(shift, kkmController.openShift("kkm-1", "Bearer 1111"))
        assertEquals(report, kkmController.closeShift("kkm-1", "Bearer 1111"))
        assertEquals(1, kkmController.listShifts("kkm-1", 50, 10, "Bearer 1111").size)
        assertEquals(1, kkmController.listShiftDocuments("kkm-1", "shift-1", 100, 0, "Bearer 1111").size)
        assertEquals(1, kkmController.listCurrentShiftDocuments("kkm-1", 100, 0, "Bearer 1111").size)
        assertEquals(1, kkmController.listDocumentsByPeriod("kkm-1", 1L, 2L, 20, 0, "Bearer 1111").size)

        verify(exactly = 1) { service.openShift("kkm-1", "1111") }
        verify(exactly = 1) { service.closeShift("kkm-1", "1111") }
        verify(exactly = 1) { service.getOpenShift("kkm-1", "1111") }
    }

    @Test
    fun `kkm print endpoints build proper response entities`() {
        every {
            service.getPrintHtml("kkm-2", kz.mybrain.superkassa.core.application.model.PrintDocumentType.DOCUMENT, "doc-2", null, "2222")
        } returns "<html>ok</html>"
        every {
            service.getPrintPdf("kkm-2", kz.mybrain.superkassa.core.application.model.PrintDocumentType.DOCUMENT, "doc-2", null, "2222")
        } returns byteArrayOf(1, 2, 3)

        val htmlResponse = kkmController.getDocumentPrintHtml("kkm-2", "doc-2", "Bearer 2222")
        val pdfResponse = kkmController.getDocumentPrintPdf("kkm-2", "doc-2", "Bearer 2222")

        assertEquals("<html>ok</html>", htmlResponse.body)
        assertEquals("text/html;charset=UTF-8", htmlResponse.headers.contentType!!.toString())
        assertEquals(3, pdfResponse.body!!.size)
        assertEquals("application/pdf", pdfResponse.headers.contentType!!.toString())
        assertTrue((pdfResponse.headers["Content-Disposition"] ?: emptyList()).first().contains("document-doc-2.pdf"))
    }

    @Test
    fun `retry delivery maps channel results`() {
        every { service.retryReceiptDelivery("kkm-3", "doc-3", "3333") } returns listOf("PRINT" to true, "EMAIL" to false)

        val response = kkmController.retryReceiptDelivery("kkm-3", "doc-3", "Bearer 3333")

        assertEquals(2, response.results.size)
        assertEquals("PRINT", response.results[0].channel)
        assertEquals(true, response.results[0].success)
        assertEquals("EMAIL", response.results[1].channel)
        assertEquals(false, response.results[1].success)
    }

    @Test
    fun `counters endpoints delegate with parsed pin`() {
        val counter = CounterSnapshot(scope = "GLOBAL", key = "k", value = 10, updatedAt = 100)
        every { service.listCounters("kkm-4", "4444") } returns listOf(counter)
        every { service.syncOfdCounters("kkm-4", "4444") } returns OfdCommandResult(status = OfdCommandStatus.OK)

        val counters = countersController.listCounters("kkm-4", "Bearer 4444")
        val sync = countersController.syncOfdCounters("kkm-4", "Bearer 4444")

        assertEquals(1, counters.size)
        assertEquals(OfdCommandStatus.OK, sync.status)
    }

    @Test
    fun `programming endpoints map kkm response`() {
        val kkm = sampleKkm("kkm-prog")
        every { service.enterProgramming("kkm-prog", "5555") } returns kkm
        every { service.exitProgramming("kkm-prog", "5555") } returns kkm

        val enter = programmingController.enterProgramming("kkm-prog", "Bearer 5555")
        val exit = programmingController.exitProgramming("kkm-prog", "Bearer 5555")

        assertEquals("kkm-prog", enter.kkmId)
        assertEquals("KAZAKHTELECOM", enter.ofdId)
        assertEquals("TEST", enter.ofdEnvironment)
        assertEquals("kkm-prog", exit.kkmId)
    }

    @Test
    fun `diagnostics endpoints delegate to service`() {
        val ofdOk = OfdCommandResult(status = OfdCommandStatus.OK)
        every { service.getOfdInfo("kkm-5") } returns ofdOk
        every { service.checkOfdConnection("kkm-5") } returns ofdOk
        every { service.getOfdAuthInfo("kkm-5", "5555") } returns OfdAuthInfoResponse(token = "t", nextReqNum = 42)

        assertEquals(OfdCommandStatus.OK, diagnosticsController.getOfdInfo("kkm-5").status)
        assertEquals(OfdCommandStatus.OK, diagnosticsController.checkOfdConnection("kkm-5").status)
        assertEquals(42, diagnosticsController.getOfdAuthInfo("kkm-5", "Bearer 5555").nextReqNum)
    }

    @Test
    fun `management endpoints delegate and map response`() {
        val kkm = sampleKkm("kkm-mgmt")
        val ofd = OfdCommandResult(status = OfdCommandStatus.OK)
        every { service.getKkm("kkm-mgmt") } returns kkm
        every { service.updateKkmSettings("kkm-mgmt", "6666", true) } returns kkm
        every {
            service.updateTaxSettings(
                kkmId = "kkm-mgmt",
                pin = "6666",
                taxRegime = TaxRegime.MIXED,
                defaultVatGroup = VatGroup.VAT_5
            )
        } returns kkm
        every { service.updateOfdToken("kkm-mgmt", "6666", "new-token") } returns true
        every { service.syncOfdServiceInfo("kkm-mgmt", "6666") } returns ofd

        val get = managementController.getKkm("kkm-mgmt")
        val updatedSettings =
            managementController.updateKkmSettings("kkm-mgmt", "Bearer 6666", AutoCloseShiftRequest(autoCloseShift = true))
        val updatedTax =
            managementController.updateKkmTaxSettings(
                "kkm-mgmt",
                "Bearer 6666",
                KkmTaxSettingsUpdateRequest(TaxRegime.MIXED, VatGroup.VAT_5)
            )
        val tokenUpdate =
            managementController.updateOfdToken(
                "kkm-mgmt",
                "Bearer 6666",
                OfdTokenUpdateRequest(token = "new-token")
            )
        val sync = managementController.syncOfdServiceInfo("kkm-mgmt", "Bearer 6666")

        assertEquals("kkm-mgmt", get.kkmId)
        assertEquals("kkm-mgmt", updatedSettings.kkmId)
        assertEquals("kkm-mgmt", updatedTax.kkmId)
        assertEquals(true, tokenUpdate["ok"])
        assertEquals(OfdCommandStatus.OK, sync.status)
    }

    @Test
    fun `decommissioning endpoints delegate and map responses`() {
        val kkm = sampleKkm("kkm-init")
        every { service.initKkmSimple("7777", any()) } returns kkm
        every { service.generateFactoryInfo() } returns FactoryNumberResponse(factoryNumber = "FN-123", manufactureYear = 2026)
        every { service.deleteKkm("kkm-init", "7777") } returns true

        val init =
            decommissioningController.initKkm(
                "Bearer 7777",
                KkmInitSimpleRequest(
                    ofdId = "kazakhtelecom",
                    ofdEnvironment = "test",
                    ofdSystemId = "100",
                    ofdToken = "token"
                )
            )
        val factory = decommissioningController.generateFactoryInfo()
        val deleted = decommissioningController.deleteKkm("kkm-init", "Bearer 7777")

        assertEquals("kkm-init", init.kkmId)
        assertEquals("FN-123", factory.factoryNumber)
        assertEquals(true, deleted["ok"])
    }

    @Test
    fun `users endpoints delegate and map responses`() {
        val admin =
            kz.mybrain.superkassa.core.application.model.UserResponse(
                userId = "u-1",
                name = "Admin",
                role = UserRole.ADMIN,
                pin = "0000"
            )
        val cashier =
            kz.mybrain.superkassa.core.application.model.UserResponse(
                userId = "u-2",
                name = "Cashier",
                role = UserRole.CASHIER,
                pin = "1111"
            )

        every { service.listUsers("kkm-users", "8888") } returns listOf(admin)
        every {
            service.createUser(
                "kkm-users",
                "8888",
                UserCreateRequest(name = "Cashier", role = UserRole.CASHIER, userPin = "1111")
            )
        } returns cashier
        every {
            service.updateUser(
                "kkm-users",
                "u-2",
                "8888",
                UserUpdateRequest(name = "Cashier 2")
            )
        } returns cashier.copy(name = "Cashier 2")
        every { service.deleteUser("kkm-users", "u-2", "8888") } returns true

        val list = usersController.listUsers("kkm-users", "Bearer 8888")
        val created =
            usersController.createUser(
                "kkm-users",
                "Bearer 8888",
                UserCreateRequest(name = "Cashier", role = UserRole.CASHIER, userPin = "1111")
            )
        val updated =
            usersController.updateUser(
                "kkm-users",
                "u-2",
                "Bearer 8888",
                UserUpdateRequest(name = "Cashier 2")
            )
        val deleted = usersController.deleteUser("kkm-users", "u-2", "Bearer 8888")

        assertEquals(1, list.size)
        assertEquals("u-2", created.userId)
        assertEquals("Cashier 2", updated.name)
        assertEquals(true, deleted["ok"])
    }

    private fun sampleKkm(id: String) =
        KkmInfo(
            id = id,
            createdAt = 1,
            updatedAt = 2,
            mode = KkmMode.REGISTRATION.name,
            state = KkmState.ACTIVE.name,
            ofdProvider = "KAZAKHTELECOM:TEST",
            registrationNumber = "RN-1",
            factoryNumber = "FN-1",
            manufactureYear = 2026,
            systemId = "100",
            taxRegime = TaxRegime.NO_VAT,
            defaultVatGroup = VatGroup.NO_VAT
        )
}
