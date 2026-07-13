package kz.mybrain.superkassa.core.http.controllers

import io.github.texport.superkassa.jvm.settings.impl.mapper.toDto
import io.github.texport.superkassa.jvm.storage.impl.application.health.StorageHealthChecker
import io.github.texport.superkassa.jvm.storage.impl.application.health.StorageHealthStatus
import io.github.texport.superkassa.jvm.storage.impl.domain.config.StorageConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kz.mybrain.superkassa.core.application.http.controllers.DiagnosticsController
import kz.mybrain.superkassa.core.application.http.controllers.QueueController
import kz.mybrain.superkassa.core.application.http.controllers.SuperkassaInfoController
import kz.mybrain.superkassa.core.application.http.controllers.SuperkassaSettingsController
import kz.mybrain.superkassa.core.application.http.controllers.UnitsOfMeasurementController
import io.github.texport.superkassa.jvm.settings.impl.UpdateSettingsUseCase
import io.github.texport.superkassa.jvm.settings.impl.SettingsApplicationService
import io.github.texport.superkassa.core.domain.api.exception.SettingsFrozenException
import io.github.texport.superkassa.core.domain.api.model.settings.CoreMode
import io.github.texport.superkassa.core.domain.api.model.settings.CoreSettings
import io.github.texport.superkassa.core.domain.api.model.settings.StorageSettings
import io.github.texport.superkassa.core.domain.api.port.integration.CoreSettingsRepositoryPort
import io.github.texport.superkassa.core.domain.api.port.integration.StoragePort
import io.github.texport.superkassa.core.presentation.api.model.kkm.KkmListResponse
import io.github.texport.superkassa.core.presentation.api.model.kkm.KkmResponse
import io.github.texport.superkassa.core.presentation.api.model.common.PaginatedResponse
import io.github.texport.superkassa.core.presentation.api.model.common.UnitOfMeasurementResponse
import io.github.texport.superkassa.core.presentation.api.model.queue.QueueItemResponse
import io.github.texport.superkassa.core.presentation.api.model.ofd.OfdCommandResponse
import io.github.texport.superkassa.core.presentation.api.model.ofd.OfdCommandStatus as PresentationOfdCommandStatus
import io.github.texport.superkassa.core.presentation.api.OfflineQueueApi
import io.github.texport.superkassa.core.presentation.api.SuperkassaApi
import kz.mybrain.superkassa.core.application.info.SystemInfoApplicationService
import kz.mybrain.superkassa.core.application.measurement.UnitsApplicationService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SystemControllersTest {

    @Test
    fun `queue controller delegates list and retry with parsed pin`() {
        val kkmService = mockk<SuperkassaApi>()
        val queueApi = mockk<OfflineQueueApi>()
        every { kkmService.queue } returns queueApi

        val controller = QueueController(kkmService)
        val queueItem = QueueItemResponse(
            id = "q-1",
            lane = "OFFLINE",
            type = "TICKET",
            status = "FAILED",
            attempt = 2,
            nextAttemptAt = null,
            lastError = "err",
            errorRu = null,
            errorKk = null,
            errorEn = null
        )
        every { queueApi.listQueue("kkm-q", "1234") } returns listOf(queueItem)
        every { queueApi.retryFailed("kkm-q", "1234") } returns 3

        val list = controller.listQueue("kkm-q", "Bearer 1234")
        val retry = controller.retryFailed("kkm-q", "Bearer 1234")

        assertEquals(1, list.size)
        assertEquals(3, retry["updated"])
        verify(exactly = 1) { queueApi.listQueue("kkm-q", "1234") }
        verify(exactly = 1) { queueApi.retryFailed("kkm-q", "1234") }
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
        val systemInfoService = SystemInfoApplicationService(settings.toDto(), storage)
        val controller = SuperkassaInfoController(systemInfoService, kkmService, "9.9.9")
        val kkm = KkmResponse(
            kkmId = "kkm-1",
            createdAt = 1L,
            updatedAt = 2L,
            mode = "REGISTRATION",
            state = "ACTIVE",
            ofdId = "KAZAKHTELECOM",
            ofdEnvironment = "TEST",
            kkmKgdId = "RN-1",
            factoryNumber = "FN-1",
            manufactureYear = 2026
        )
        every { kkmService.listKkms(any()) } returns KkmListResponse(items = listOf(kkm), total = 1)
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
    fun `superkassa info controller handles storage failure gracefully`() {
        val settings =
            CoreSettings(
                mode = CoreMode.DESKTOP,
                storage = StorageSettings(engine = "SQLITE", jdbcUrl = "jdbc:sqlite:build/core.db"),
                allowChanges = true
            )
        val storage = mockk<StoragePort>()
        val kkmService = mockk<SuperkassaApi>()
        val systemInfoService = SystemInfoApplicationService(settings.toDto(), storage)
        val controller = SuperkassaInfoController(systemInfoService, kkmService, "9.9.9")

        every { storage.countKkms(null, null) } throws RuntimeException("DB offline")

        val info = controller.info()
        assertEquals(0, (info["statistics"] as Map<*, *>)["registeredKkms"])
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

        val settingsService = SettingsApplicationService(repo, initial, UpdateSettingsUseCase(repo, initial))
        val controller = SuperkassaSettingsController(settingsService)
        val loaded = controller.getSettings()
        val updated =
            loaded.copy(
                ofdTimeoutSeconds = 45L,
                ofdReconnectIntervalSeconds = 90L
            )

        val result = controller.updateSettings(updated)

        assertEquals(45L, result.ofdTimeoutSeconds)
        verify(exactly = 1) { repo.loadOrCreate(initial) }
        verify(exactly = 1) { repo.save(match { it.ofdTimeoutSeconds == 45L && it.ofdReconnectIntervalSeconds == 90L }) }
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
        val settingsService = SettingsApplicationService(repo, frozen, UpdateSettingsUseCase(repo, frozen))
        val controller = SuperkassaSettingsController(settingsService)

        assertFailsWith<SettingsFrozenException> {
            controller.updateSettings(frozen.toDto())
        }
    }

    @Test
    fun `superkassa settings update fails in server mode`() {
        val serverModeSettings =
            CoreSettings(
                mode = CoreMode.SERVER,
                storage = StorageSettings(engine = "POSTGRESQL", jdbcUrl = "jdbc:postgresql://localhost:5432/db"),
                allowChanges = true
            )
        val repo = mockk<CoreSettingsRepositoryPort>()
        val settingsService =
            SettingsApplicationService(repo, serverModeSettings, UpdateSettingsUseCase(repo, serverModeSettings))
        val controller = SuperkassaSettingsController(settingsService)

        assertFailsWith<SettingsFrozenException> {
            controller.updateSettings(serverModeSettings.toDto())
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
        val kkm = KkmResponse(
            kkmId = "kkm-2",
            createdAt = 1L,
            updatedAt = 2L,
            mode = "REGISTRATION",
            state = "ACTIVE",
            ofdId = "KAZAKHTELECOM",
            ofdEnvironment = "TEST",
            kkmKgdId = "RN-2",
            factoryNumber = "FN-2",
            manufactureYear = 2026
        )
        every { kkmService.listKkms(any()) } returns KkmListResponse(items = listOf(kkm), total = 1)
        every {
            kkmService.checkOfdConnection("kkm-2")
        } returns OfdCommandResponse(status = PresentationOfdCommandStatus.FAILED, errorMessage = "down")

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
        val unitsService = mockk<UnitsApplicationService>()
        val controller = UnitsOfMeasurementController(unitsService)

        val uom = UnitOfMeasurementResponse(code = "796", nameShort = "шт", nameFull = "Штука")
        every { unitsService.list(10, 0, null) } returns PaginatedResponse(
            items = listOf(uom),
            total = 1,
            limit = 10,
            offset = 0,
            hasMore = false
        )
        every { unitsService.list(10, 0, "шт") } returns PaginatedResponse(
            items = listOf(uom),
            total = 1,
            limit = 10,
            offset = 0,
            hasMore = false
        )
        every { unitsService.getByCode("796") } returns uom

        val all = controller.list(limit = 10, offset = 0, search = null)
        val filtered = controller.list(limit = 10, offset = 0, search = "шт")
        val piece = controller.getByCode("796")

        assertTrue(all.total > 0)
        assertTrue(filtered.total > 0)
        assertEquals("796", piece.code)
    }
}
