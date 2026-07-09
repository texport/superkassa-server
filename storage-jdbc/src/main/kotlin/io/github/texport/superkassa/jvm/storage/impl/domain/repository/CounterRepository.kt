package io.github.texport.superkassa.jvm.storage.impl.domain.repository

import io.github.texport.superkassa.jvm.storage.impl.domain.model.CounterRecord

/**
 * Репозиторий счетчиков кассы (глобальных и сменных).
 *
 * Зачем нужен:
 * - хранить счетчики для X/Z отчетов,
 * - разделять глобальные и сменные значения.
 */
interface CounterRepository {
    /**
     * Создает или обновляет счетчик.
     */
    fun upsert(record: CounterRecord): Boolean

    /**
     * Возвращает счетчик по ключу.
     */
    fun findByKey(
        cashboxId: String,
        scope: String,
        shiftId: String?,
        key: String
    ): CounterRecord?

    /**
     * Возвращает список счетчиков по области.
     */
    fun listByScope(
        cashboxId: String,
        scope: String,
        shiftId: String?
    ): List<CounterRecord>

    /**
     * Возвращает все счетчики по кассе.
     */
    fun listByCashbox(cashboxId: String): List<CounterRecord>

    /**
     * Удаляет счетчик.
     */
    fun deleteByKey(
        cashboxId: String,
        scope: String,
        shiftId: String?,
        key: String
    ): Boolean

    /**
     * Удаляет все счетчики по кассе.
     */
    fun deleteByCashbox(cashboxId: String): Boolean
}
