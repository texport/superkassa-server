package io.github.texport.superkassa.jvm.storage.impl.adapter

import io.github.texport.superkassa.core.domain.api.model.kkm.KkmInfo
import io.github.texport.superkassa.core.domain.api.model.queue.QueueTask
import io.github.texport.superkassa.jvm.storage.impl.parity.JdbcKassa
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Задача досылки ставится один раз: повтор отвечает «не поставлена»
 * и не срывает транзакцию, в которой идёт операция кассы.
 */
class QueueEnqueueOnceTest {
    private val dir = Files.createTempDirectory("enqueue-once")
    private val storage = JdbcKassa.openStorage(dir)

    @Test
    fun `повторная постановка той же задачи её не трогает`() {
        storage.startTransaction()
        assertTrue(storage.enqueueQueueTask(task(status = "PENDING")))
        assertFalse(storage.enqueueQueueTask(task(status = "SENT")))
        storage.createKkm(KkmInfo(id = KKM, createdAt = 1L, updatedAt = 1L, mode = "REGISTRATION", state = "ACTIVE"))
        storage.commitTransaction()

        val fresh = JdbcKassa.openStorage(dir)
        assertEquals(listOf("PENDING"), fresh.listQueueTasksByCashbox(KKM, "OFFLINE", 10, 0).map { it.status })
        assertNotNull(fresh.findKkm(KKM), "the surrounding transaction is committed")
    }

    private fun task(status: String) = QueueTask(
        id = "task-1", cashboxId = KKM, lane = "OFFLINE", type = "TICKET", payloadRef = "doc-1",
        createdAt = 1L, status = status, attempt = 0, nextAttemptAt = null, lastError = null
    )

    private companion object {
        const val KKM = "kkm-enqueue"
    }
}
