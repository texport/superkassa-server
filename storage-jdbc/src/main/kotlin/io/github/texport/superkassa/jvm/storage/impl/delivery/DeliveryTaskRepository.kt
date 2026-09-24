package io.github.texport.superkassa.jvm.storage.impl.delivery

import io.github.texport.superkassa.core.domain.api.model.delivery.DeliveryTask

/**
 * Задачи доставки чека покупателю (delivery_tasks).
 *
 * Задача живёт в базе узла: чек, не ушедший покупателю до остановки узла,
 * досылается после его запуска.
 */
interface DeliveryTaskRepository {
    /** Ставит задачи; задача с уже известным идентификатором остаётся как была. */
    fun addAll(tasks: List<DeliveryTask>)

    /** Ожидающие задачи со сроком не позже [now], самые давние первыми, не больше [limit]. */
    fun due(now: Long, limit: Int): List<DeliveryTask>

    /**
     * Занимает ожидающую задачу со сроком не позже [now] одним изменением
     * строки: число попыток растёт, срок переносится на [leaseUntil].
     *
     * @return `true`, если задачу занял этот вызов.
     */
    fun claim(id: String, now: Long, leaseUntil: Long): Boolean

    /** Записывает задачу целиком поверх записанной с тем же идентификатором. */
    fun save(task: DeliveryTask)

    /** Задачи документа по всем каналам, от поставленной первой. */
    fun byDocument(documentId: String): List<DeliveryTask>

    /** Касса уходит целиком — вместе с задачами доставки её чеков. */
    fun deleteByCashbox(cashboxId: String)
}
