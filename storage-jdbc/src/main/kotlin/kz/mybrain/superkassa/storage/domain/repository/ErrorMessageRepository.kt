package kz.mybrain.superkassa.storage.domain.repository

import kz.mybrain.superkassa.storage.domain.model.ErrorMessageRecord

/**
 * Репозиторий централизованных ошибок.
 *
 * Зачем нужен:
 * - хранить ошибки в одном месте,
 * - иметь сообщения на двух языках,
 * - отдавать пользователю понятную диагностику.
 */
interface ErrorMessageRepository {
    /**
     * Сохраняет ошибку.
     */
    fun insert(record: ErrorMessageRecord): Boolean

    /**
     * Ищет ошибку по id.
     */
    fun findById(id: String): ErrorMessageRecord?

    /**
     * Возвращает последние ошибки (глобально).
     */
    fun listRecent(limit: Int): List<ErrorMessageRecord>

    /**
     * Возвращает последние ошибки по кассе.
     */
    fun listByCashbox(cashboxId: String, limit: Int): List<ErrorMessageRecord>

    /**
     * Удаляет ошибку по id.
     */
    fun deleteById(id: String): Boolean

    /**
     * Удаляет все ошибки по кассе.
     */
    fun deleteByCashbox(cashboxId: String): Boolean
}
