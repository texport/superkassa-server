package io.github.texport.superkassa.jvm.storage.impl.delivery

import io.github.texport.superkassa.core.domain.api.model.delivery.DeliveryFailure
import io.github.texport.superkassa.core.domain.api.model.delivery.DeliveryTask
import io.github.texport.superkassa.core.domain.api.model.delivery.DeliveryTaskStatus
import io.github.texport.superkassa.core.string.api.TrilingualMessage
import io.github.texport.superkassa.jvm.storage.impl.parity.JdbcKassa
import java.nio.file.Files
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Задачи доставки чека в базе узла: постановка без повторов, срок,
 * занятие одним отправителем и то, что переживает перезапуск.
 */
class JdbcDeliveryTasksTest {
    private val dir = Files.createTempDirectory("delivery-tasks")
    private val storage = JdbcKassa.openStorage(dir)

    @Test
    fun `повторная постановка не возвращает доставленную задачу в ожидание`() {
        storage.addDeliveryTasks(listOf(task("doc-1")))
        storage.saveDeliveryTask(task("doc-1").copy(status = DeliveryTaskStatus.DELIVERED, attempts = 1))

        storage.addDeliveryTasks(listOf(task("doc-1"), task("doc-2")))

        assertEquals(DeliveryTaskStatus.DELIVERED, storage.deliveryTasksOf("doc-1").single().status)
        assertEquals(listOf("doc-2"), storage.dueDeliveryTasks(NOW, LIMIT).map { it.documentId })
    }

    @Test
    fun `к сроку уходят только ожидающие, самые давние первыми и не больше предела`() {
        storage.addDeliveryTasks(
            listOf(task("late", at = NOW + 1), task("second", at = NOW - 1), task("first", at = NOW - 2), task("third", at = NOW))
        )
        storage.saveDeliveryTask(task("third").copy(status = DeliveryTaskStatus.FAILED))

        assertEquals(listOf("first", "second"), storage.dueDeliveryTasks(NOW, LIMIT).map { it.documentId })
        assertEquals(listOf("first"), storage.dueDeliveryTasks(NOW, 1).map { it.documentId })
    }

    @Test
    fun `задачу занимает один отправитель, и срок уходит на аренду`() {
        storage.addDeliveryTasks(listOf(task("doc-1"), task("later", at = NOW + 1)))
        val id = task("doc-1").id

        assertTrue(storage.claimDeliveryTask(id, NOW, LEASE))
        assertFalse(storage.claimDeliveryTask(id, NOW, LEASE), "claimed twice")
        assertFalse(storage.claimDeliveryTask(task("later").id, NOW, LEASE), "claimed before its time")
        val claimed = storage.deliveryTasksOf("doc-1").single()
        assertEquals(listOf(1L, LEASE, NOW), listOf(claimed.attempts.toLong(), claimed.nextAttemptAt, claimed.updatedAt))
        assertTrue(storage.dueDeliveryTasks(NOW, LIMIT).isEmpty())
    }

    @Test
    fun `одновременное занятие одной задачи достаётся одному`() {
        storage.addDeliveryTasks(listOf(task("doc-1")))
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(SENDERS)

        val claims = (1..SENDERS).map {
            pool.submit(Callable { start.await(); storage.claimDeliveryTask(task("doc-1").id, NOW, LEASE) })
        }
        start.countDown()
        val won = claims.count { it.get() }
        pool.shutdown()

        assertEquals(1, won)
        assertEquals(1, storage.deliveryTasksOf("doc-1").single().attempts)
    }

    @Test
    fun `отказ хранится кодом и тремя языками, доставка его снимает`() {
        storage.addDeliveryTasks(listOf(task("doc-1")))
        val refused = task("doc-1").copy(attempts = 1, failure = FAILURE, updatedAt = NOW + 1)

        storage.saveDeliveryTask(refused)
        val stored = storage.deliveryTasksOf("doc-1").single()
        storage.saveDeliveryTask(refused.copy(status = DeliveryTaskStatus.DELIVERED, failure = null))

        assertEquals(refused, stored)
        assertNull(storage.deliveryTasksOf("doc-1").single().failure)
    }

    @Test
    fun `задачи переживают перезапуск узла и уходят вместе с кассой`() {
        storage.addDeliveryTasks(listOf(task("doc-1"), task("doc-2", kkm = "other")))

        val restarted = JdbcKassa.openStorage(dir)
        val due = restarted.dueDeliveryTasks(NOW, LIMIT)
        restarted.deleteKkmCompletely(KKM)

        assertEquals(listOf("+77017654321", "+77017654321"), due.map { it.destination })
        assertEquals(listOf("other"), restarted.dueDeliveryTasks(NOW, LIMIT).map { it.kkmId })
    }

    @Test
    fun `задачи документа идут по порядку постановки`() {
        val print = task("doc-1", channel = "PRINT", at = NOW - 1).copy(destination = null, payloadType = "ESC_POS")
        storage.addDeliveryTasks(listOf(task("doc-1"), print))

        assertEquals(listOf(print, task("doc-1")), storage.deliveryTasksOf("doc-1"))
    }

    private fun task(documentId: String, at: Long = NOW, kkm: String = KKM, channel: String = "SMS") = DeliveryTask(
        id = DeliveryTask.idOf(documentId, channel, "HTML"),
        kkmId = kkm,
        documentId = documentId,
        channel = channel,
        destination = "+77017654321",
        payloadType = "HTML",
        nextAttemptAt = at,
        createdAt = at
    )

    private companion object {
        const val KKM = "kkm-delivery-1"
        const val NOW = 1_800_000_000_000L
        const val LEASE = NOW + 120_000L
        const val LIMIT = 10
        const val SENDERS = 8

        val FAILURE = DeliveryFailure(
            "DELIVERY_SMS_REFUSED",
            TrilingualMessage(ru = "Шлюз SMS отказал", kk = "SMS шлюзы бас тартты", en = "The SMS gateway refused")
        )
    }
}
