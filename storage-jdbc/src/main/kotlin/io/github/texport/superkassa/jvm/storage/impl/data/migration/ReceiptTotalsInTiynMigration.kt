package io.github.texport.superkassa.jvm.storage.impl.data.migration

import com.fasterxml.jackson.core.JacksonException
import io.github.texport.superkassa.core.domain.api.model.receipt.ReceiptStoredPayload
import io.github.texport.superkassa.jvm.storage.impl.application.migration.DataMigration
import io.github.texport.superkassa.jvm.storage.impl.data.jdbc.receiptPayloadJson
import org.slf4j.LoggerFactory
import java.sql.Connection

/**
 * Приводит сумму документа к тиынам.
 *
 * Версии до перевода денег на тиыны писали `total_amount` в тенге, тогда
 * как нагрузка чека хранит сумму в тиынах. Журнал и основание возврата
 * показывали такие документы дешевле в сто раз: чек на 200,00 ₸ значился
 * как 2,00 ₸. Отчёты при этом были верны — они считаются по нагрузке,
 * и расхождение выдавало себя только скачком остатка ящика после сверки.
 *
 * Сумма берётся из самой нагрузки: она и есть то, что ушло в ОФД.
 * Документы без нагрузки — отчёты, открытие смены, операции с наличными —
 * не трогаются: у них суммы в нагрузке нет.
 */
class ReceiptTotalsInTiynMigration : DataMigration {

    private val logger = LoggerFactory.getLogger(ReceiptTotalsInTiynMigration::class.java)

    override val version: String = "20"
    override val checksum: String = "v20"
    override val description: String = "Сумма документа пересчитана из нагрузки в тиыны"

    override fun apply(connection: Connection) {
        val corrections = collectCorrections(connection)
        if (corrections.isEmpty()) {
            logger.info("Receipt totals already in tiyn, nothing to correct")
            return
        }
        val sql = "UPDATE fiscal_document SET total_amount = ? WHERE id = ?"
        connection.prepareStatement(sql).use { stmt ->
            for ((id, total) in corrections) {
                stmt.setLong(1, total)
                stmt.setString(2, id)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
        logger.info("Receipt totals corrected. documents={}", corrections.size)
    }

    /**
     * Собирает поправки отдельно от записи: обходить выборку и писать
     * в ту же таблицу одним курсором нельзя ни на одном из трёх движков.
     */
    private fun collectCorrections(connection: Connection): List<Pair<String, Long>> {
        val sql = "SELECT id, total_amount, payload_bin FROM fiscal_document WHERE payload_bin IS NOT NULL"
        val corrections = mutableListOf<Pair<String, Long>>()
        connection.createStatement().use { stmt ->
            stmt.executeQuery(sql).use { rows ->
                while (rows.next()) {
                    correctionOf(rows.getString("id"), rows.getLong("total_amount"), rows.getBytes("payload_bin"))
                        ?.let(corrections::add)
                }
            }
        }
        return corrections
    }

    /** Поправка нужна, только если хранимая сумма разошлась с нагрузкой. */
    private fun correctionOf(id: String, stored: Long, payload: ByteArray?): Pair<String, Long>? {
        val actual = payload?.let { totalFromPayload(id, it) } ?: return null
        return if (actual == stored) null else id to actual
    }

    /** Испорченную нагрузку миграция пропускает: чинить её ей нечем. */
    private fun totalFromPayload(id: String, payload: ByteArray): Long? = try {
        receiptPayloadJson.readValue(payload, ReceiptStoredPayload::class.java).toReceiptRequest().total.tiyn()
    } catch (failure: JacksonException) {
        logger.warn("Failed to read receipt payload, document left as is. id={}", id, failure)
        null
    }
}
