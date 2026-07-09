package io.github.texport.superkassa.jvm.storage.impl.domain.repository

import io.github.texport.superkassa.jvm.storage.impl.domain.model.OfdMessageRecord

/**
 * Репозиторий сообщений ОФД.
 *
 * Зачем нужен:
 * - хранить request/response для повторной отправки,
 * - диагностировать ошибки связи,
 * - восстанавливать состояние при сбоях.
 */
interface OfdMessageRepository {
    /**
     * Создает запись запроса ОФД.
     */
    fun insert(record: OfdMessageRecord): Boolean

    /**
     * Обновляет ответ/статус по запросу ОФД.
     */
    fun updateResponse(
        id: String,
        responseBin: ByteArray?,
        status: String,
        attempt: Int,
        errorCode: String?,
        updatedAt: Long
    ): Boolean

    /**
     * Ищет запись по id.
     */
    fun findById(id: String): OfdMessageRecord?

    /**
     * Список ожидающих отправки запросов.
     */
    fun listPending(cashboxId: String, limit: Int): List<OfdMessageRecord>

    /**
     * Список сообщений по кассе (постранично).
     */
    fun listByCashbox(cashboxId: String, limit: Int, offset: Int = 0): List<OfdMessageRecord>

    /**
     * Удаляет сообщение по id (например, по политике хранения).
     */
    fun deleteById(id: String): Boolean

    /**
     * Удаляет все сообщения по кассе.
     */
    fun deleteByCashbox(cashboxId: String): Boolean
}
