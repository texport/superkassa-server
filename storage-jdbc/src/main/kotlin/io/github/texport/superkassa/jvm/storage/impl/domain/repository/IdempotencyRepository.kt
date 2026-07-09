package io.github.texport.superkassa.jvm.storage.impl.domain.repository

import io.github.texport.superkassa.jvm.storage.impl.domain.model.IdempotencyRecord

/**
 * Репозиторий идемпотентности.
 *
 * Зачем нужен:
 * - не пробивать чек дважды при повторном запросе,
 * - возвращать предыдущий результат на дубликаты.
 */
interface IdempotencyRepository {
    /**
     * Вставляет запись, если ключ не существует.
     */
    fun insertIfAbsent(record: IdempotencyRecord): Boolean

    /**
     * Ищет запись по кассе и ключу.
     */
    fun findByKey(cashboxId: String, idempotencyKey: String): IdempotencyRecord?

    /**
     * Обновляет статус и ссылку на результат.
     */
    fun updateResponse(
        cashboxId: String,
        idempotencyKey: String,
        status: String,
        responseRef: String?
    ): Boolean

    /**
     * Удаляет запись идемпотентности.
     */
    fun deleteByKey(cashboxId: String, idempotencyKey: String): Boolean

    /**
     * Удаляет все записи идемпотентности по кассе.
     */
    fun deleteByCashbox(cashboxId: String): Boolean
}
