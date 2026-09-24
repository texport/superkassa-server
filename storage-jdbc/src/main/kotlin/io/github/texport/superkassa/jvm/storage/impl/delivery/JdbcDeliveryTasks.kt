package io.github.texport.superkassa.jvm.storage.impl.delivery

import io.github.texport.superkassa.core.domain.api.model.delivery.DeliveryTask
import io.github.texport.superkassa.core.domain.api.port.integration.DeliveryTaskStore
import io.github.texport.superkassa.jvm.storage.impl.adapter.JdbcSessions

/**
 * Задачи доставки чека в базе узла — часть хранилища ядра.
 *
 * С ними ядро не доставляет чек в потоке пробития, а ставит задачи, и
 * фон узла досылает их по расписанию, в том числе после перезапуска.
 * Вызовы идут в сессии потока: внутри операции кассы — в её транзакции.
 */
internal class JdbcDeliveryTasks(private val sessions: JdbcSessions) : DeliveryTaskStore {

    /** Задачи одного чека ставятся вместе: либо все, либо ни одной. */
    override fun addDeliveryTasks(tasks: List<DeliveryTask>) = sessions.inTransaction { it.deliveryTasks.addAll(tasks) }

    override fun dueDeliveryTasks(now: Long, limit: Int): List<DeliveryTask> =
        sessions.withSession { it.deliveryTasks.due(now, limit) }

    override fun claimDeliveryTask(id: String, now: Long, leaseUntil: Long): Boolean =
        sessions.withSession { it.deliveryTasks.claim(id, now, leaseUntil) }

    override fun saveDeliveryTask(task: DeliveryTask) = sessions.withSession { it.deliveryTasks.save(task) }

    override fun deliveryTasksOf(documentId: String): List<DeliveryTask> =
        sessions.withSession { it.deliveryTasks.byDocument(documentId) }
}
