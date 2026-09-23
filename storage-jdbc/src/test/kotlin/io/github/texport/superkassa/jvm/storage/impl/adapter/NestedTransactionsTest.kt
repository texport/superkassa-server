package io.github.texport.superkassa.jvm.storage.impl.adapter

import io.github.texport.superkassa.core.domain.api.model.kkm.KkmInfo
import io.github.texport.superkassa.jvm.storage.impl.parity.JdbcKassa
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Вложенная транзакция идёт в соединении внешней: её откат снимает только
 * её записи, а фиксирует всё внешняя. Другое соединение видит итог
 * только после внешней фиксации.
 */
class NestedTransactionsTest {
    private val dir = Files.createTempDirectory("nested-tx")
    private val storage = JdbcKassa.openStorage(dir)

    @Test
    fun `откат вложенной транзакции оставляет записи внешней`() {
        storage.startTransaction()
        storage.createKkm(kkm("outer"))
        storage.startTransaction()
        storage.createKkm(kkm("inner"))
        storage.rollbackTransaction()
        storage.commitTransaction()

        val fresh = JdbcKassa.openStorage(dir)
        assertNotNull(fresh.findKkm("outer"))
        assertNull(fresh.findKkm("inner"))
    }

    @Test
    fun `откат внешней транзакции снимает и зафиксированную вложенную`() {
        storage.startTransaction()
        storage.startTransaction()
        storage.createKkm(kkm("inner"))
        storage.commitTransaction()
        assertNull(JdbcKassa.openStorage(dir).findKkm("inner"), "not visible before the outer commit")
        storage.rollbackTransaction()

        assertNull(storage.findKkm("inner"))
    }

    @Test
    fun `фиксация и откат без транзакции ничего не делают`() {
        storage.commitTransaction()
        storage.rollbackTransaction()

        assertEquals(0, storage.countKkms(null, null))
    }

    private fun kkm(id: String) = KkmInfo(id = id, createdAt = 1L, updatedAt = 1L, mode = "REGISTRATION", state = "ACTIVE")
}
