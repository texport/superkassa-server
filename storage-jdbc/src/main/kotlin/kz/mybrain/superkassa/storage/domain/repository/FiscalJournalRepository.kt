package kz.mybrain.superkassa.storage.domain.repository

import kz.mybrain.superkassa.storage.domain.model.FiscalJournalRecord

/**
 * Репозиторий фискального журнала (append-only + hash-chain).
 *
 * Зачем нужен:
 * - контроль целостности истории,
 * - невозможность тихого изменения записей,
 * - аудит операций по кассе.
 */
interface FiscalJournalRepository {
    /**
     * Добавляет запись в append-only журнал.
     */
    fun append(record: FiscalJournalRecord): Boolean

    /**
     * Возвращает последние записи журнала по кассе.
     */
    fun listByCashbox(cashboxId: String, limit: Int): List<FiscalJournalRecord>

    /**
     * Возвращает последний hash в цепочке.
     */
    fun lastHash(cashboxId: String): ByteArray?

    /**
     * Удаляет все записи журнала по кассе.
     */
    fun deleteByCashbox(cashboxId: String): Boolean
}
