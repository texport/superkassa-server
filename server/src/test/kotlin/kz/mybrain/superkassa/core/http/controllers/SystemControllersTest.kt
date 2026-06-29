package kz.mybrain.superkassa.core.http.controllers

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kz.mybrain.superkassa.core.domain.exception.SettingsFrozenException
import kz.mybrain.superkassa.core.application.http.controllers.DiagnosticsController
import kz.mybrain.superkassa.core.application.http.controllers.QueueController
import kz.mybrain.superkassa.core.application.http.controllers.SuperkassaInfoController
import kz.mybrain.superkassa.core.application.http.controllers.SuperkassaSettingsController
import kz.mybrain.superkassa.core.application.http.controllers.UnitsOfMeasurementController
import kz.mybrain.superkassa.core.domain.model.settings.CoreMode
import kz.mybrain.superkassa.core.domain.model.settings.CoreSettings
import kz.mybrain.superkassa.core.presentation.model.KkmListResult
import kz.mybrain.superkassa.core.presentation.facade.SuperkassaApi
import kz.mybrain.superkassa.core.domain.model.settings.StorageSettings
import kz.mybrain.superkassa.core.domain.port.CoreSettingsRepositoryPort
import kz.mybrain.superkassa.core.domain.model.kkm.KkmInfo
import kz.mybrain.superkassa.core.domain.model.kkm.KkmMode
import kz.mybrain.superkassa.core.domain.model.kkm.KkmState
import kz.mybrain.superkassa.core.domain.model.ofd.OfdCommandResult
import kz.mybrain.superkassa.core.domain.model.ofd.OfdCommandStatus
import kz.mybrain.superkassa.core.domain.model.common.TaxRegime
import kz.mybrain.superkassa.core.domain.model.common.VatGroup
import kz.mybrain.superkassa.core.domain.port.StoragePort
import kz.mybrain.superkassa.storage.application.health.StorageHealthChecker
import kz.mybrain.superkassa.storage.application.health.StorageHealthStatus
import kz.mybrain.superkassa.storage.domain.config.StorageConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SystemControllersTest {

    @Test
    fun `queue controller delegates list and retry with parsed pin`() {
        val listQueueItemsUseCase =
            mockk<kz.mybrain.superkassa.core.domain.usecase.queue.ListQueueItemsUseCase>()
        val retryFailedQueueItemsUseCase =
            mockk<kz.mybrain.superkassa.core.domain.usecase.queue.RetryFailedQueueItemsUseCase>()
        val controller = QueueController(listQueueItemsUseCase, retryFailedQueueItemsUseCase)
        val queueItem =
            kz.mybrain.superkassa.core.domain.usecase.queue.ListQueueItemsUseCase.QueueItemView(
                id = "q-1",
                lane = "OFFLINE",
                type = "TICKET",
                status = "FAILED",
                attempt = 2,
                nextAttemptAt = null,
                lastError = "err"
            )
        every { listQueueItemsUseCase.execute("kkm-q", "1234") } returns listOf(queueItem)
        every { retryFailedQueueItemsUseCase.execute("kkm-q", "1234") } returns 3

        val list = controller.listQueue("kkm-q", "Bearer 1234")
        val retry = controller.retryFailed("kkm-q", "Bearer 1234")

        assertEquals(1, list.size)
        assertEquals(3, retry["updated"])
        verify(exactly = 1) { listQueueItemsUseCase.execute("kkm-q", "1234") }
        verify(exactly = 1) { retryFailedQueueItemsUseCase.execute("kkm-q", "1234") }
    }

    @Test
    fun `superkassa info controller returns kkm page and system info`() {
        val settings =
            CoreSettings(
                mode = CoreMode.DESKTOP,
                storage = StorageSettings(engine = "SQLITE", jdbcUrl = "jdbc:sqlite:build/core.db"),
                allowChanges = true
            )
        val storage = mockk<StoragePort>()
        val kkmService = mockk<SuperkassaApi>()
        val controller = SuperkassaInfoController(settings, storage, kkmService, "9.9.9")
        val kkm = sampleKkm("kkm-1")
        every { kkmService.listKkms(any()) } returns KkmListResult(items = listOf(kkm), total = 1)
        every { storage.countKkms(null, null) } returns 1

        val list = controller.listKkms(
            limit = 50,
            offset = 0,
            state = null,
            search = null,
            sortBy = "createdAt",
            order = "DESC"
        )
        val info = controller.info()

        assertEquals(1, list.items.size)
        assertEquals("kkm-1", list.items.first().kkmId)
        assertEquals("Superkassa Core", info["name"])
        assertEquals("9.9.9", info["version"])
        assertEquals(1, (info["statistics"] as Map<*, *>)["registeredKkms"])
    }

    @Test
    fun `superkassa settings controller gets and updates settings when allowed`() {
        val initial =
            CoreSettings(
                mode = CoreMode.DESKTOP,
                storage = StorageSettings(engine = "SQLITE", jdbcUrl = "jdbc:sqlite:build/core.db"),
                allowChanges = true
            )
        val repo = mockk<CoreSettingsRepositoryPort>()
        every { repo.loadOrCreate(initial) } returns initial
        every { repo.save(any()) } returns true

        val controller = SuperkassaSettingsController(repo, initial)
        val loaded = controller.getSettings()
        val updated =
            loaded.copy(
                ofdTimeoutSeconds = 45L,
                ofdReconnectIntervalSeconds = 90L
            )

        val result = controller.updateSettings(updated)

        assertEquals(45L, result.ofdTimeoutSeconds)
        verify(exactly = 1) { repo.loadOrCreate(initial) }
        verify(exactly = 1) { repo.save(updated) }
    }

    @Test
    fun `superkassa settings update fails when settings frozen`() {
        val frozen =
            CoreSettings(
                mode = CoreMode.DESKTOP,
                storage = StorageSettings(engine = "SQLITE", jdbcUrl = "jdbc:sqlite:build/core.db"),
                allowChanges = false
            )
        val repo = mockk<CoreSettingsRepositoryPort>()
        val controller = SuperkassaSettingsController(repo, frozen)

        assertFailsWith<SettingsFrozenException> {
            controller.updateSettings(frozen)
        }
    }

    @Test
    fun `diagnostics health returns ok when storage healthy`() {
        val checker = mockk<StorageHealthChecker>()
        val config = StorageConfig(jdbcUrl = "jdbc:sqlite:build/core.db")
        val controller = DiagnosticsController(checker, config, null)
        every { checker.check(config, 3) } returns StorageHealthStatus(ok = true, message = "Connection OK")

        val response = controller.health(checkOfd = false)

        assertEquals(200, response.statusCode.value())
        assertEquals("OK", response.body!!["status"])
        assertEquals("Connection OK", response.body!!["storage"])
    }

    @Test
    fun `diagnostics health degrades when ofd check fails`() {
        val checker = mockk<StorageHealthChecker>()
        val config = StorageConfig(jdbcUrl = "jdbc:sqlite:build/core.db")
        val kkmService = mockk<SuperkassaApi>()
        val controller = DiagnosticsController(checker, config, kkmService)
        every { checker.check(config, 3) } returns StorageHealthStatus(ok = true, message = "Connection OK")
        every { kkmService.listKkms(any()) } returns KkmListResult(items = listOf(sampleKkm("kkm-2")), total = 1)
        every {
            kkmService.checkOfdConnection("kkm-2")
        } returns OfdCommandResult(status = OfdCommandStatus.FAILED, errorMessage = "down")

        val response = controller.health(checkOfd = true)

        assertEquals(503, response.statusCode.value())
        assertEquals("DEGRADED", response.body!!["status"])
        val ofd = response.body!!["ofd"]
        if (ofd is Map<*, *>) {
            assertTrue(ofd.keys.first().toString().contains("KAZAKHTELECOM:TEST"))
        } else {
            assertTrue(ofd.toString().contains("DEGRADED") || ofd.toString().contains("ERROR"))
        }
    }

    @Test
    fun `units controller supports list search and get by code`() {
        val controller = UnitsOfMeasurementController()

        val all = controller.list(limit = 10, offset = 0, search = null)
        val filtered = controller.list(limit = 10, offset = 0, search = "шт")
        val piece = controller.getByCode("796")

        assertTrue(all.total > 0)
        assertTrue(filtered.total > 0)
        assertEquals("796", piece.code)
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
