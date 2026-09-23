package io.github.texport.superkassa.jvm.settings.impl

import io.github.texport.superkassa.core.domain.api.model.settings.CoreMode
import io.github.texport.superkassa.core.domain.api.model.settings.CoreSettings
import io.github.texport.superkassa.core.domain.api.model.settings.StorageSettings
import io.github.texport.superkassa.core.presentation.api.SettingsApi
import io.github.texport.superkassa.jvm.settings.impl.mapper.toDto
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsApplicationServiceTest {

    private val api = mockk<SettingsApi>()
    private val service = SettingsApplicationService(api)
    private val current = CoreSettings(
        mode = CoreMode.DESKTOP,
        storage = StorageSettings(engine = "SQLITE", jdbcUrl = "jdbc:sqlite:build/core.db"),
        allowChanges = true,
        ofdProtocolVersion = "204"
    )

    @Test
    fun `настройки узла - те, что отдаёт ядро`() {
        every { api.getSettings() } returns current

        assertEquals(current.toDto(), service.getSettings())
    }

    @Test
    fun `правка уходит в ядро, и ответ - сохранённое ядром`() {
        val next = current.copy(ofdTimeoutSeconds = 45L)
        every { api.updateSettings(next) } returns next

        val saved = service.updateSettings(next.toDto())

        assertEquals(45L, saved.ofdTimeoutSeconds)
        verify(exactly = 1) { api.updateSettings(next) }
    }
}
