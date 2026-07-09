package kz.mybrain.superkassa.core.application.settings

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kz.mybrain.superkassa.core.domain.exception.SettingsFrozenException
import kz.mybrain.superkassa.core.domain.model.settings.CoreMode
import kz.mybrain.superkassa.core.domain.model.settings.CoreSettings
import kz.mybrain.superkassa.core.domain.model.settings.StorageSettings
import kz.mybrain.superkassa.core.domain.port.CoreSettingsRepositoryPort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UpdateSettingsUseCaseTest {

    private val repo = mockk<CoreSettingsRepositoryPort>()

    @Test
    fun `execute saves settings in desktop mode when allowed`() {
        val initial = CoreSettings(
            mode = CoreMode.DESKTOP,
            storage = StorageSettings(engine = "SQLITE", jdbcUrl = "jdbc:sqlite:build/core.db"),
            allowChanges = true
        )
        val useCase = UpdateSettingsUseCase(repo, initial)

        every { repo.save(initial) } returns true

        val result = useCase.execute(initial)

        assertEquals(initial, result)
        verify(exactly = 1) { repo.save(initial) }
    }

    @Test
    fun `execute fails when settings frozen in desktop mode`() {
        val initial = CoreSettings(
            mode = CoreMode.DESKTOP,
            storage = StorageSettings(engine = "SQLITE", jdbcUrl = "jdbc:sqlite:build/core.db"),
            allowChanges = false
        )
        val useCase = UpdateSettingsUseCase(repo, initial)

        val exception = assertFailsWith<SettingsFrozenException> {
            useCase.execute(initial)
        }

        assertTrue(exception.message!!.contains("Изменение настроек заморожено"))
        verify(exactly = 0) { repo.save(any()) }
    }

    @Test
    fun `execute fails when in server mode`() {
        val initial = CoreSettings(
            mode = CoreMode.SERVER,
            storage = StorageSettings(engine = "POSTGRESQL", jdbcUrl = "jdbc:postgresql://localhost/db"),
            allowChanges = true
        )
        val useCase = UpdateSettingsUseCase(repo, initial)

        val exception = assertFailsWith<SettingsFrozenException> {
            useCase.execute(initial)
        }

        assertTrue(exception.message!!.contains("Настройки не могут быть изменены через API в режиме SERVER"))
        verify(exactly = 0) { repo.save(any()) }
    }
}
