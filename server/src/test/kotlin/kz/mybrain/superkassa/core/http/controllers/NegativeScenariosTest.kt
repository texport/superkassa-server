package kz.mybrain.superkassa.core.http.controllers

import io.github.texport.superkassa.core.domain.api.exception.ForbiddenException
import io.github.texport.superkassa.core.domain.api.exception.NotFoundException
import io.github.texport.superkassa.core.presentation.api.DeliveryApi
import io.github.texport.superkassa.core.presentation.api.SuperkassaApi
import io.github.texport.superkassa.core.presentation.api.model.kkm.KkmTaxSettingsUpdateRequest
import io.github.texport.superkassa.core.presentation.api.model.kkm.ReceiptBrandingRequest
import io.github.texport.superkassa.core.presentation.api.model.kkm.CashOperationRequest
import io.github.texport.superkassa.core.presentation.api.model.shift.AutoCloseShiftRequest
import io.github.texport.superkassa.core.presentation.api.model.ofd.OfdTokenUpdateRequest
import io.github.texport.superkassa.core.presentation.api.model.user.UserCreateRequest
import io.github.texport.superkassa.core.presentation.api.model.user.UserUpdateRequest
import io.github.texport.superkassa.core.presentation.api.model.common.PaginatedResponse
import io.github.texport.superkassa.core.presentation.api.model.common.UnitOfMeasurementResponse
import io.github.texport.superkassa.jvm.storage.impl.application.health.StorageHealthChecker
import io.github.texport.superkassa.jvm.storage.impl.application.health.StorageHealthStatus
import io.github.texport.superkassa.jvm.storage.impl.domain.config.StorageConfig
import io.github.texport.superkassa.core.string.api.TrilingualMessage
import io.mockk.every
import io.mockk.mockk
import kotlin.test.assertTrue
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
import kz.mybrain.superkassa.core.application.measurement.UnitsApplicationService
import org.springframework.http.HttpStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NegativeScenariosTest {

    private val service = mockk<SuperkassaApi>()
    private val deliveryApi = mockk<DeliveryApi>(relaxed = true)
    private val kkmController = KkmController(service, deliveryApi)

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

        every { checker.check(config, 3) } returns StorageHealthStatus(ok = false, message = "Connection Error")

        val response = controller.health(checkOfd = false)

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), response.statusCode.value())
        assertEquals("DEGRADED", response.body!!["status"])
        assertEquals("Connection Error", response.body!!["storage"])
    }

    @Test
    fun `diagnostics health degrades when listKkms throws error`() {
        val checker = mockk<StorageHealthChecker>()
        val config = StorageConfig(jdbcUrl = "jdbc:sqlite:build/core.db")
        val controller = DiagnosticsController(checker, config, service)

        every { checker.check(config, 3) } returns StorageHealthStatus(ok = true, message = "Connection OK")
        every { service.listKkms(any()) } throws RuntimeException("DB error")

        val response = controller.health(checkOfd = true)

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), response.statusCode.value())
        assertEquals("DEGRADED", response.body!!["status"])
        assertTrue(response.body!!["ofd"].toString().contains("ERROR: DB error"))
    }

    @Test
    fun `diagnostics health is OK when no KKMs registered`() {
        val checker = mockk<StorageHealthChecker>()
        val config = StorageConfig(jdbcUrl = "jdbc:sqlite:build/core.db")
        val controller = DiagnosticsController(checker, config, service)

        every { checker.check(config, 3) } returns StorageHealthStatus(ok = true, message = "Connection OK")
        every { service.listKkms(any()) } returns io.github.texport.superkassa.core.presentation.api.model.kkm.KkmListResponse(items = emptyList(), total = 0)

        val response = controller.health(checkOfd = true)

        assertEquals(HttpStatus.OK.value(), response.statusCode.value())
        assertEquals("OK", response.body!!["status"])
        assertEquals("SKIPPED: no KKM registered", response.body!!["ofd"])
    }

    @Test
    fun `diagnostics health skips OFD checks when filter excludes all KKMs`() {
        val checker = mockk<StorageHealthChecker>()
        val config = StorageConfig(jdbcUrl = "jdbc:sqlite:build/core.db")
        val controller = DiagnosticsController(checker, config, service)

        every { checker.check(config, 3) } returns StorageHealthStatus(ok = true, message = "Connection OK")
        val kkm = io.github.texport.superkassa.core.presentation.api.model.kkm.KkmResponse(
            kkmId = "kkm-1",
            createdAt = 1L,
            updatedAt = 2L,
            mode = "REGISTRATION",
            state = "ACTIVE",
            ofdId = "KAZAKHTELECOM",
            ofdEnvironment = "TEST"
        )
        every { service.listKkms(any()) } returns io.github.texport.superkassa.core.presentation.api.model.kkm.KkmListResponse(items = listOf(kkm), total = 1)

        val response = controller.health(checkOfd = true, ofdProvider = "NON_EXISTENT_OFD")

        assertEquals(HttpStatus.OK.value(), response.statusCode.value())
        assertEquals("OK", response.body!!["status"])
        assertEquals("SKIPPED: no matching KKM found for filters", response.body!!["ofd"])
    }

    // --- 15-18. QueueController Negative Tests ---

    @Test
    fun `queue controller listQueue throws ForbiddenException when authHeader is null`() {
        val controller = QueueController(service)

        assertFailsWith<ForbiddenException> {
            controller.listQueue("kkm-1", null)
        }
    }

    @Test
    fun `queue controller retryFailed throws ForbiddenException when authHeader is null`() {
        val controller = QueueController(service)

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
        assertFailsWith<ForbiddenException> { cash.cashIn("kkm-1", "Bearer", mockk<CashOperationRequest>()) }
        assertFailsWith<ForbiddenException> { cash.cashOut("kkm-1", "", mockk<CashOperationRequest>()) }
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
                mockk<ReceiptBrandingRequest>()
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
        val unitsService = mockk<UnitsApplicationService>()
        val controller = UnitsOfMeasurementController(unitsService)

        val uom = UnitOfMeasurementResponse(code = "796", nameShort = "шт", nameFull = "Штука")
        every { unitsService.list(1, 0, null) } returns PaginatedResponse(items = listOf(uom), total = 1, limit = 1, offset = 0, hasMore = false)

        val result = controller.list(limit = -10, offset = 0, search = null)
        assertEquals(1, result.limit)
        assertEquals(1, result.items.size)
    }

    @Test
    fun `units controller returns empty list results when offset is out of range`() {
        val unitsService = mockk<UnitsApplicationService>()
        val controller = UnitsOfMeasurementController(unitsService)

        every { unitsService.list(10, 10000, null) } returns PaginatedResponse(items = emptyList(), total = 1, limit = 10, offset = 10000, hasMore = false)

        val result = controller.list(limit = 10, offset = 10000, search = null)
        assertEquals(0, result.items.size)
    }

    @Test
    fun `units controller throws NotFoundException when code does not exist`() {
        val unitsService = mockk<UnitsApplicationService>()
        val controller = UnitsOfMeasurementController(unitsService)

        every { unitsService.getByCode("999999") } throws NotFoundException(TrilingualMessage.mono("Not found"), "NOT_FOUND")

        assertFailsWith<NotFoundException> {
            controller.getByCode("999999")
        }
    }
}
