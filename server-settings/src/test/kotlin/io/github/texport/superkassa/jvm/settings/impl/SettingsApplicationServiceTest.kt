package io.github.texport.superkassa.jvm.settings.impl

import io.github.texport.superkassa.core.domain.api.model.settings.CoreMode
import io.github.texport.superkassa.core.domain.api.model.settings.CoreSettings
import io.github.texport.superkassa.core.domain.api.model.settings.StorageSettings
import io.github.texport.superkassa.core.domain.api.port.integration.CoreSettingsRepositoryPort
import io.github.texport.superkassa.jvm.settings.impl.mapper.toDto
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsApplicationServiceTest {

    private val repo = mockk<CoreSettingsRepositoryPort>()
    private val initial = CoreSettings(
        mode = CoreMode.DESKTOP,
        storage = StorageSettings(engine = "SQLITE", jdbcUrl = "jdbc:sqlite:build/core.db"),
        allowChanges = true
    )
    private val updateUseCase = mockk<UpdateSettingsUseCase>()
    private val service = SettingsApplicationService(repo, initial, updateUseCase)

    @Test
    fun `getSettings returns toDto of cached settings`() {
        every { repo.loadOrCreate(initial) } returns initial
        val settings = service.getSettings()
        assertEquals(initial.toDto(), settings)
    }

    /**
     * Узел отвечает одной правдой о версии протокола.
     *
     * Сохранённая запись говорила 204, запуск — 203, обмен шёл по 2.0.3:
     * `GET /info` отдавал 203, `GET /settings` — 204, и экран диагностики
     * показывал «Протокол ОФД 204» над строкой «Версия протокола 203»,
     * полученной от самого ОФД.
     */
    @Test
    fun `getSettings answers with the protocol version the node runs on`() {
        val startedWith = initial.copy(ofdProtocolVersion = "203", ofdProviderId = "BFD")
        val stored = initial.copy(ofdProtocolVersion = "204", ofdProviderId = "KAZAKHTELECOM")
        every { repo.loadOrCreate(startedWith) } returns stored
        val service = SettingsApplicationService(repo, startedWith, updateUseCase)

        val settings = service.getSettings()

        assertEquals("203", settings.ofdProtocolVersion)
    }

    @Test
    fun `updateSettings executes useCase and updates cache`() {
        val nextDomain = CoreSettings(
            mode = CoreMode.DESKTOP,
            storage = StorageSettings(engine = "SQLITE", jdbcUrl = "jdbc:sqlite:build/new.db"),
            allowChanges = true
        )
        val nextDto = nextDomain.toDto()

        var currentSettings = initial
        every { repo.loadOrCreate(any()) } answers { currentSettings }
        every { updateUseCase.execute(any()) } answers {
            currentSettings = nextDomain
            nextDomain
        }

        val result = service.updateSettings(nextDto)

        assertEquals(nextDto, result)
        assertEquals(nextDto, service.getSettings())
        verify(exactly = 1) { updateUseCase.execute(any()) }
    }
}
