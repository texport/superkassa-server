package io.github.texport.superkassa.jvm.storage.impl.adapter

import io.github.texport.superkassa.core.domain.api.model.queue.QueueTask
import io.github.texport.superkassa.jvm.storage.impl.parity.JdbcKassa
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Досылка видит все задачи кассы в нужных статусах. Прежде хранилище
 * читало первые 500 задач кассы и отбирало статусы уже в памяти: у кассы
 * с долгой историей отправленных документов новые до ОФД не доходили.
 */
class QueueTasksByStatusTest {
    private val storage = JdbcKassa.openStorage(Files.createTempDirectory("queue-status"))

    @Test
    fun `ожидающая задача за пятью сотнями отправленных находится`() {
        repeat(SENT_BEFORE) { storage.enqueueQueueTask(task("sent-$it", "SENT", createdAt = it.toLong())) }
        storage.enqueueQueueTask(task("waiting", "PENDING", createdAt = SENT_BEFORE.toLong()))

        val pending = storage.getQueueTasksByStatus(KKM, LANE, setOf("PENDING", "FAILED"))

        assertEquals(listOf("waiting"), pending.map { it.id })
    }

    @Test
    fun `без статусов задач нет`() {
        storage.enqueueQueueTask(task("waiting", "PENDING", createdAt = 1L))

        assertTrue(storage.getQueueTasksByStatus(KKM, LANE, emptySet()).isEmpty())
    }

    private fun task(id: String, status: String, createdAt: Long) = QueueTask(
        id = id, cashboxId = KKM, lane = LANE, type = "TICKET", payloadRef = "doc-$id",
        createdAt = createdAt, status = status, attempt = 0, nextAttemptAt = null, lastError = null
    )

    private companion object {
        const val KKM = "kkm-queue"
        const val LANE = "OFFLINE"
        const val SENT_BEFORE = 501
    }
}
