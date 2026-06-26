package kz.mybrain.superkassa.storage.domain.repository

import kz.mybrain.superkassa.storage.domain.model.OfflineQueueItem

/**
 * Репозиторий автономной очереди.
 *
 * Зачем нужен:
 * - строго FIFO выгрузка чеков при восстановлении связи,
 * - контроль попыток и backoff,
 * - гарантированный порядок операций по кассе.
 */
interface OfflineQueueRepository {
    /**
     * Добавляет операцию в офлайн-очередь.
     */
    fun enqueue(item: OfflineQueueItem): Boolean

    /**
     * Возвращает следующий элемент для обработки.
     */
    fun nextPending(cashboxId: String): OfflineQueueItem?

    /**
     * Обновляет попытки/статус обработки.
     */
    fun updateAttempt(
        id: String,
        attempt: Int,
        lastError: String?,
        nextAttemptAt: Long?,
        status: String
    ): Boolean

    /**
     * Помечает элемент как выполненный.
     */
    fun markCompleted(id: String, status: String): Boolean

    /**
     * Список элементов очереди по кассе (постранично).
     */
    fun listByCashbox(cashboxId: String, limit: Int, offset: Int = 0): List<OfflineQueueItem>

    /**
     * Удаляет элемент очереди по id (например, по политике хранения).
     */
    fun deleteById(id: String): Boolean

    /**
     * Удаляет все элементы очереди по кассе.
     */
    fun deleteByCashbox(cashboxId: String): Boolean

    /**
     * Общее количество записей в автономной очереди (по всем кассам).
     */
    fun countAll(): Long
}
