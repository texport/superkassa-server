package kz.mybrain.superkassa.storage.domain.repository

import kz.mybrain.superkassa.storage.domain.model.CashboxRecord

/**
 * Репозиторий состояния кассы.
 *
 * Зачем нужен:
 * - восстановить режим/счетчики после перезапуска,
 * - хранить реквизиты кассы и токены,
 * - контролировать ограничения (например, длительность смены).
 */
interface CashboxRepository {
    /**
     * Создает запись кассы.
     */
    fun insert(record: CashboxRecord): Boolean

    /**
     * Обновляет запись кассы.
     */
    fun update(record: CashboxRecord): Boolean

    /**
     * Ищет кассу по id.
     */
    fun findById(id: String): CashboxRecord?

    /**
     * Ищет кассу по регистрационному номеру.
     */
    fun findByRegistrationNumber(registrationNumber: String): CashboxRecord?

    /**
     * Ищет кассу по system_id.
     */
    fun findBySystemId(systemId: String): CashboxRecord?

    /**
     * Список касс (постранично).
     */
    fun listAll(limit: Int, offset: Int = 0): List<CashboxRecord>

    /**
     * Список касс с фильтрацией и сортировкой.
     * @param limit максимальное количество записей
     * @param offset смещение для пагинации
     * @param state фильтр по состоянию кассы (например, "ACTIVE")
     * @param search поиск по регистрационному номеру (частичное совпадение)
     * @param sortBy поле для сортировки (created_at, updated_at, state, registration_number)
     * @param sortOrder порядок сортировки ("ASC" или "DESC")
     */
    fun listAllFiltered(
        limit: Int,
        offset: Int = 0,
        state: String? = null,
        search: String? = null,
        sortBy: String = "created_at",
        sortOrder: String = "DESC"
    ): List<CashboxRecord>

    /**
     * Подсчет касс с учетом фильтров.
     * @param state фильтр по состоянию кассы
     * @param search поиск по регистрационному номеру
     */
    fun countAll(
        state: String? = null,
        search: String? = null
    ): Int

    /**
     * Обновляет токен ОФД для кассы.
     */
    fun updateToken(
        id: String,
        tokenEncrypted: ByteArray,
        tokenUpdatedAt: Long
    ): Boolean

    /**
     * Удаляет кассу по id.
     * Используется при деактивации/удалении кассы.
     */
    fun deleteById(id: String): Boolean
}
