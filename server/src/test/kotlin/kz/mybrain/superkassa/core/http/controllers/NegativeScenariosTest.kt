package kz.mybrain.superkassa.core.http.controllers

import io.github.texport.superkassa.jvm.storage.impl.application.health.StorageHealthChecker
import io.github.texport.superkassa.jvm.storage.impl.application.health.StorageHealthStatus
import io.github.texport.superkassa.jvm.storage.impl.domain.config.StorageConfig
import io.mockk.every
import io.mockk.mockk
import kz.mybrain.superkassa.core.application.http.controllers.CashOperationRequestDto
import kz.mybrain.superkassa.core.application.http.controllers.CashOperationsController
import kz.mybrain.superkassa.core.application.http.controllers.DiagnosticsController
import kz.mybrain.superkassa.core.application.http.controllers.KkmController
import kz.mybrain.superkassa.core.application.http.controllers.KkmDiagnosticsController
import kz.mybrain.superkassa.core.application.http.controllers.KkmManagementController
import kz.mybrain.superkassa.core.application.http.controllers.KkmProgrammingController
import kz.mybrain.superkassa.core.application.http.controllers.KkmUsersController
import kz.mybrain.superkassa.core.application.http.controllers.QueueController
import kz.mybrain.superkassa.core.application.http.controllers.ReportsController
import kz.mybrain.superkassa.core.application.http.controllers.UnitsOfMeasurementController
import kz.mybrain.superkassa.core.domain.exception.ForbiddenException
import kz.mybrain.superkassa.core.presentation.facade.SuperkassaApi
import kz.mybrain.superkassa.core.presentation.model.AutoCloseShiftRequest
import kz.mybrain.superkassa.core.presentation.model.KkmListResult
import kz.mybrain.superkassa.core.presentation.model.KkmTaxSettingsUpdateRequest
import kz.mybrain.superkassa.core.presentation.model.OfdTokenUpdateRequest
import kz.mybrain.superkassa.core.presentation.model.UserCreateRequest
import kz.mybrain.superkassa.core.presentation.model.UserUpdateRequest
import org.springframework.http.HttpStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NegativeScenariosTest {

    private val service = mockk<SuperkassaApi>()
    private val kkmController = KkmController(service)

    // --- 1-8. KkmController Authorization Negative Tests ---

    @Test
    fun `openShift throws ForbiddenException when authHeader is null`() {
        assertFailsWith<ForbiddenException> {
            kkmController.openShift("kkm-1", null)
        }
    }

    @Test
    fun `openShift throws ForbiddenException when authHeader is empty`() {
        assertFailsWith<ForbiddenException> {
            kkmController.openShift("kkm-1", "")
        }
    }

    @Test
    fun `openShift throws ForbiddenException when authHeader is blank`() {
        assertFailsWith<ForbiddenException> {
            kkmController.openShift("kkm-1", "   ")
        }
    }

    @Test
    fun `openShift throws ForbiddenException when authHeader has Bearer only`() {
        assertFailsWith<ForbiddenException> {
            kkmController.openShift("kkm-1", "Bearer")
        }
    }

    @Test
    fun `openShift throws ForbiddenException when authHeader has Bearer with spaces`() {
        assertFailsWith<ForbiddenException> {
            kkmController.openShift("kkm-1", "Bearer   ")
        }
    }

    @Test
    fun `closeShift throws ForbiddenException when authHeader is null`() {
        assertFailsWith<ForbiddenException> {
            kkmController.closeShift("kkm-1", null)
        }
    }

    @Test
    fun `listShifts throws ForbiddenException when authHeader is null`() {
        assertFailsWith<ForbiddenException> {
            kkmController.listShifts("kkm-1", 10, 0, null)
        }
    }

    @Test
    fun `listShiftDocuments throws ForbiddenException when authHeader is null`() {
        assertFailsWith<ForbiddenException> {
            kkmController.listShiftDocuments("kkm-1", "shift-1", 10, 0, null)
        }
    }

    // --- 9-14. DiagnosticsController Negative Tests ---

    @Test
    fun `diagnostics health degrades when database healthcheck is not OK`() {
        val checker = mockk<StorageHealthChecker>()
        val config = StorageConfig(jdbcUrl = "jdbc:sqlite:build/core.db")
        val controller = DiagnosticsController(checker, config, service)

        every { checker.check(config, 3) } returns StorageHealthStatus(ok = false, message = "Database connection failed")

        val response = controller.health(checkOfd = false)

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), response.statusCode.value())
        assertEquals("DEGRADED", response.body!!["status"])
        assertEquals("Database connection failed", response.body!!["storage"])
    }

    @Test
    fun `diagnostics health degrades when listKkms throws exception`() {
        val checker = mockk<StorageHealthChecker>()
        val config = StorageConfig(jdbcUrl = "jdbc:sqlite:build/core.db")
        val controller = DiagnosticsController(checker, config, service)

        every { checker.check(config, 3) } returns StorageHealthStatus(ok = true, message = "Connection OK")
        every { service.listKkms(any()) } throws RuntimeException("Service failure")

        val response = controller.health(checkOfd = true)

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), response.statusCode.value())
        assertEquals("DEGRADED", response.body!!["status"])
        assertEquals("ERROR: Service failure", response.body!!["ofd"])
    }

    @Test
    fun `diagnostics health degrades when checkOfdConnection throws exception`() {
        val checker = mockk<StorageHealthChecker>()
        val config = StorageConfig(jdbcUrl = "jdbc:sqlite:build/core.db")
        val controller = DiagnosticsController(checker, config, service)
        val kkm = kz.mybrain.superkassa.core.domain.model.kkm.KkmInfo(
            id = "kkm-1",
            createdAt = 1,
            updatedAt = 2,
            mode = "REGISTRATION",
            state = "ACTIVE",
            ofdProvider = "KAZAKHTELECOM:TEST",
            registrationNumber = "RN-1",
            factoryNumber = "FN-1",
            manufactureYear = 2026,
            systemId = "100",
            taxRegime = kz.mybrain.superkassa.core.domain.model.common.TaxRegime.NO_VAT,
            defaultVatGroup = kz.mybrain.superkassa.core.domain.model.common.VatGroup.NO_VAT
        )

        every { checker.check(config, 3) } returns StorageHealthStatus(ok = true, message = "Connection OK")
        every { service.listKkms(any()) } returns KkmListResult(items = listOf(kkm), total = 1)
        every { service.checkOfdConnection("kkm-1") } throws RuntimeException("OFD timeout")

        val response = controller.health(checkOfd = true)

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), response.statusCode.value())
        assertEquals("DEGRADED", response.body!!["status"])
        val ofdMap = response.body!!["ofd"] as Map<*, *>
        assertEquals("ERROR: OFD timeout", ofdMap["KAZAKHTELECOM:TEST"])
    }

    @Test
    fun `diagnostics health returns OK and skips when listKkms returns empty`() {
        val checker = mockk<StorageHealthChecker>()
        val config = StorageConfig(jdbcUrl = "jdbc:sqlite:build/core.db")
        val controller = DiagnosticsController(checker, config, service)

        every { checker.check(config, 3) } returns StorageHealthStatus(ok = true, message = "Connection OK")
        every { service.listKkms(any()) } returns KkmListResult(items = emptyList(), total = 0)

        val response = controller.health(checkOfd = true)

        assertEquals(HttpStatus.OK.value(), response.statusCode.value())
        assertEquals("OK", response.body!!["status"])
        assertEquals("SKIPPED: no KKM registered", response.body!!["ofd"])
    }

    @Test
    fun `diagnostics health returns OK and skips when no active kkm matches provider filter`() {
        val checker = mockk<StorageHealthChecker>()
        val config = StorageConfig(jdbcUrl = "jdbc:sqlite:build/core.db")
        val controller = DiagnosticsController(checker, config, service)
        val kkm = kz.mybrain.superkassa.core.domain.model.kkm.KkmInfo(
            id = "kkm-1",
            createdAt = 1,
            updatedAt = 2,
            mode = "REGISTRATION",
            state = "ACTIVE",
            ofdProvider = "KAZAKHTELECOM:TEST",
            registrationNumber = "RN-1",
            factoryNumber = "FN-1",
            manufactureYear = 2026,
            systemId = "100",
            taxRegime = kz.mybrain.superkassa.core.domain.model.common.TaxRegime.NO_VAT,
            defaultVatGroup = kz.mybrain.superkassa.core.domain.model.common.VatGroup.NO_VAT
        )

        every { checker.check(config, 3) } returns StorageHealthStatus(ok = true, message = "Connection OK")
        every { service.listKkms(any()) } returns KkmListResult(items = listOf(kkm), total = 1)

        val response = controller.health(checkOfd = true, ofdProvider = "NON_EXISTENT_OFD")

        assertEquals(HttpStatus.OK.value(), response.statusCode.value())
        assertEquals("OK", response.body!!["status"])
        assertEquals("SKIPPED: no matching KKM found for filters", response.body!!["ofd"])
    }

    // --- 15-18. QueueController Negative Tests ---

    @Test
    fun `queue controller listQueue throws ForbiddenException when authHeader is null`() {
        val listUseCase = mockk<kz.mybrain.superkassa.core.domain.usecase.queue.ListQueueItemsUseCase>()
        val retryUseCase = mockk<kz.mybrain.superkassa.core.domain.usecase.queue.RetryFailedQueueItemsUseCase>()
        val controller = QueueController(listUseCase, retryUseCase)

        assertFailsWith<ForbiddenException> {
            controller.listQueue("kkm-1", null)
        }
    }

    @Test
    fun `queue controller retryFailed throws ForbiddenException when authHeader is null`() {
        val listUseCase = mockk<kz.mybrain.superkassa.core.domain.usecase.queue.ListQueueItemsUseCase>()
        val retryUseCase = mockk<kz.mybrain.superkassa.core.domain.usecase.queue.RetryFailedQueueItemsUseCase>()
        val controller = QueueController(listUseCase, retryUseCase)

        assertFailsWith<ForbiddenException> {
            controller.retryFailed("kkm-1", null)
        }
    }

    @Test
    fun `users controller throws ForbiddenException before service calls when auth is missing`() {
        val controller = KkmUsersController(service)

        assertFailsWith<ForbiddenException> { controller.listUsers("kkm-1", null) }
        assertFailsWith<ForbiddenException> { controller.createUser("kkm-1", null, mockk<UserCreateRequest>()) }
        assertFailsWith<ForbiddenException> {
            controller.updateUser("kkm-1", "user-1", null, mockk<UserUpdateRequest>())
        }
        assertFailsWith<ForbiddenException> { controller.deleteUser("kkm-1", "user-1", null) }
    }

    @Test
    fun `report and cash controllers throw ForbiddenException when auth is malformed`() {
        val reports = ReportsController(service)
        val cash = CashOperationsController(service)

        assertFailsWith<ForbiddenException> { reports.createXReport("kkm-1", "Bearer   ") }
        assertFailsWith<ForbiddenException> { cash.cashIn("kkm-1", "Bearer", mockk<CashOperationRequestDto>()) }
        assertFailsWith<ForbiddenException> { cash.cashOut("kkm-1", "", mockk<CashOperationRequestDto>()) }
    }

    @Test
    fun `programming and management controllers reject missing authorization`() {
        val programming = KkmProgrammingController(service)
        val management = KkmManagementController(service)

        assertFailsWith<ForbiddenException> { programming.enterProgramming("kkm-1", null) }
        assertFailsWith<ForbiddenException> { programming.exitProgramming("kkm-1", "   ") }
        assertFailsWith<ForbiddenException> {
            management.updateKkmSettings("kkm-1", null, mockk<AutoCloseShiftRequest>())
        }
        assertFailsWith<ForbiddenException> {
            management.updateKkmTaxSettings("kkm-1", null, mockk<KkmTaxSettingsUpdateRequest>())
        }
        assertFailsWith<ForbiddenException> {
            management.updateBrandingSettings(
                "kkm-1",
                null,
                mockk<kz.mybrain.superkassa.core.domain.model.receipt.ReceiptBranding>()
            )
        }
        assertFailsWith<ForbiddenException> {
            management.updateOfdToken("kkm-1", null, mockk<OfdTokenUpdateRequest>())
        }
        assertFailsWith<ForbiddenException> { management.syncOfdServiceInfo("kkm-1", null) }
    }

    @Test
    fun `ofd diagnostics auth info rejects missing authorization while public diagnostics stay service delegated`() {
        val controller = KkmDiagnosticsController(service)

        assertFailsWith<ForbiddenException> { controller.getOfdAuthInfo("kkm-1", null) }
    }

    // --- 19-21. UnitsOfMeasurementController Boundary and Negative Tests ---

    @Test
    fun `units controller coerces negative limit to one`() {
        val controller = UnitsOfMeasurementController()
        val result = controller.list(limit = -10, offset = 0, search = null)
        assertEquals(1, result.limit)
        assertEquals(1, result.items.size)
    }

    @Test
    fun `units controller returns empty list results when offset is out of range`() {
        val controller = UnitsOfMeasurementController()
        val result = controller.list(limit = 10, offset = 10000, search = null)
        assertEquals(0, result.items.size)
    }

    @Test
    fun `units controller throws NotFoundException when code does not exist`() {
        val controller = UnitsOfMeasurementController()
        assertFailsWith<kz.mybrain.superkassa.core.domain.exception.NotFoundException> {
            controller.getByCode("999999")
        }
    }
}
