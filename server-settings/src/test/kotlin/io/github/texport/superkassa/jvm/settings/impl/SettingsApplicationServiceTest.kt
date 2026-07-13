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
