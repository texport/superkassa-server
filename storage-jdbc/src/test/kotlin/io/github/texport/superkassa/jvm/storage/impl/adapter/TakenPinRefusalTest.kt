package io.github.texport.superkassa.jvm.storage.impl.adapter

import io.github.texport.superkassa.core.domain.api.exception.SuperkassaException
import io.github.texport.superkassa.core.domain.api.model.auth.UserRole
import io.github.texport.superkassa.jvm.shared.strings.api.key.StorageErrorKey
import io.github.texport.superkassa.jvm.shared.strings.impl.DefaultErrorResolver
import io.github.texport.superkassa.jvm.storage.impl.data.bootstrap.DefaultStorageBootstrap
import io.github.texport.superkassa.jvm.storage.impl.domain.config.StorageConfig
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

/**
 * Занятый пин — отказ словами, а не пересказ жалобы драйвера.
 *
 * Узел отвечал кассиру `STORAGE_ERROR` и текстом SQLite с именами таблицы
 * и колонок: человек на экране «Кассиры и пины» читал устройство хранилища
 * вместо объяснения, что пин надо задать другой.
 */
class TakenPinRefusalTest {

    private val driverWords = listOf("kkm_user", "cashbox_id", "pin_hash", "constraint", "sqlite")

    @Test
    fun `second user with the same pin is refused in words`() {
        val storage = storageOnTempDatabase()
        storage.createUser("kkm-1", "user-1", "Администратор", UserRole.ADMIN, "hash-1", 1L)

        val failure = assertFailsWith<SuperkassaException> {
            storage.createUser("kkm-1", "user-2", "Кассир", UserRole.CASHIER, "hash-1", 2L)
        }

        assertEquals(StorageErrorKey.USER_PIN_TAKEN.code, failure.code)
        assertEquals(409, failure.status)
        assertNoDriverWords(failure.message.orEmpty())
    }

    @Test
    fun `moving a user onto a taken pin is refused in words`() {
        val storage = storageOnTempDatabase()
        storage.createUser("kkm-1", "user-1", "Администратор", UserRole.ADMIN, "hash-1", 1L)
        storage.createUser("kkm-1", "user-2", "Кассир", UserRole.CASHIER, "hash-2", 2L)

        val failure = assertFailsWith<SuperkassaException> {
            storage.updateUser("kkm-1", "user-2", null, null, "hash-1")
        }

        assertEquals(StorageErrorKey.USER_PIN_TAKEN.code, failure.code)
        assertEquals(409, failure.status)
        assertNoDriverWords(failure.message.orEmpty())
    }

    @Test
    fun `storage failure text leaves no room for the driver's words`() {
        val text = DefaultErrorResolver().resolve(StorageErrorKey.DATABASE_ERROR)

        listOf(text.ru, text.kk, text.en).forEach { assertFalse(it.contains("{0}"), it) }
    }

    private fun storageOnTempDatabase(): StorageAdapter {
        val dbFile = Files.createTempDirectory("taken-pin").resolve("core.db").toFile()
        val config = StorageConfig(jdbcUrl = "jdbc:sqlite:${dbFile.absolutePath}")
        val bootstrap = DefaultStorageBootstrap()
        bootstrap.migrate(config)
        return StorageAdapter(bootstrap, config)
    }

    private fun assertNoDriverWords(text: String) {
        driverWords.forEach { word ->
            assertFalse(text.contains(word, ignoreCase = true), "наружу ушло «$word»: $text")
        }
    }
}
