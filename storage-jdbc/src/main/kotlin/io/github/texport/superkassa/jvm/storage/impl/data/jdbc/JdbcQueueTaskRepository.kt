package io.github.texport.superkassa.jvm.storage.impl.data.jdbc

import io.github.texport.superkassa.jvm.storage.impl.domain.model.QueueTaskRecord
import io.github.texport.superkassa.jvm.storage.impl.domain.repository.QueueTaskRepository
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException

/**
 * JDBC-реализация репозитория очереди команд (queue_task).
 */
class JdbcQueueTaskRepository(
    private val connection: Connection
) : QueueTaskRepository {
    private val logger = LoggerFactory.getLogger(JdbcQueueTaskRepository::class.java)

    /**
     * Задача ставится один раз: повторная постановка того же документа
     * её не трогает и отвечает `false`, как очередь кассы на Room.
     * Прежде повтор падал на ключе, а в PostgreSQL это обрывало всю
     * транзакцию операции кассы.
     */
    override fun enqueue(record: QueueTaskRecord): Boolean {
        val insert = """
            INSERT INTO queue_task (
                id, cashbox_id, lane, type, payload_ref, created_at,
                status, attempt, next_attempt_at, last_error
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        val sql = SqlDialect(connection).insertIfAbsent(insert, "id")
        return try {
            connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, record.id)
                stmt.setString(2, record.cashboxId)
                stmt.setString(3, record.lane)
                stmt.setString(4, record.type)
                stmt.setString(5, record.payloadRef)
                stmt.setLong(6, record.createdAt)
                stmt.setString(7, record.status)
                stmt.setInt(8, record.attempt)
                stmt.setObject(9, record.nextAttemptAt?.let { java.lang.Long.valueOf(it) })
                stmt.setString(10, record.lastError)
                stmt.executeUpdate() == 1
            }
        } catch (ex: SQLException) {
            logger.error("Failed to enqueue queue task: ${record.id}", ex)
            false
        }
    }

    override fun updateStatus(
        id: String,
        status: String,
        attempt: Int,
        lastError: String?,
        nextAttemptAt: Long?
    ): Boolean {
        val sql = """
            UPDATE queue_task SET
                status = ?,
                attempt = ?,
                last_error = ?,
                next_attempt_at = ?
            WHERE id = ?
        """.trimIndent()
        return try {
            connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, status)
                stmt.setInt(2, attempt)
                stmt.setString(3, lastError)
                stmt.setObject(4, nextAttemptAt?.let { java.lang.Long.valueOf(it) })
                stmt.setString(5, id)
                stmt.executeUpdate() == 1
            }
        } catch (ex: SQLException) {
            logger.error("Failed to update status for queue task: $id", ex)
            false
        }
    }

    /**
     * Время захвата записывается вместе со статусом.
     *
     * По нему очередь отличает задачу, которую сейчас отправляют, от
     * брошенной умершим обработчиком. Пока время не писалось, брошенная
     * задача оставалась в `IN_PROGRESS` навсегда и до ОФД не доходила.
     */
    override fun markInProgress(id: String, now: Long): Boolean {
        val sql = "UPDATE queue_task SET status = 'IN_PROGRESS', next_attempt_at = ? WHERE id = ?"
        return try {
            connection.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, now)
                stmt.setString(2, id)
                stmt.executeUpdate() == 1
            }
        } catch (ex: SQLException) {
            logger.error("Failed to mark in progress: $id", ex)
            false
        }
    }

    override fun listByCashbox(cashboxId: String, lane: String, limit: Int, offset: Int): List<QueueTaskRecord> {
        val sql = """
            SELECT * FROM queue_task
            WHERE cashbox_id = ? AND lane = ?
            ORDER BY created_at ASC
            LIMIT ? OFFSET ?
        """.trimIndent()
        return try {
            connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, cashboxId)
                stmt.setString(2, lane)
                stmt.setInt(3, limit)
                stmt.setInt(4, offset)
                fetchList(stmt)
            }
        } catch (ex: SQLException) {
            logger.error("Failed to list queue tasks for cashbox: $cashboxId", ex)
            emptyList()
        }
    }

    override fun listByStatus(cashboxId: String, lane: String, statuses: Set<String>): List<QueueTaskRecord> {
        if (statuses.isEmpty()) return emptyList()
        val sql = """
            SELECT * FROM queue_task
            WHERE cashbox_id = ? AND lane = ? AND status IN (${statuses.joinToString(", ") { "?" }})
            ORDER BY created_at ASC
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, cashboxId)
            stmt.setString(2, lane)
            statuses.forEachIndexed { index, status -> stmt.setString(index + FIRST_STATUS_PARAMETER, status) }
            return fetchList(stmt)
        }
    }

    override fun deleteByCashbox(cashboxId: String): Boolean {
        val sql = "DELETE FROM queue_task WHERE cashbox_id = ?"
        return try {
            connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, cashboxId)
                stmt.executeUpdate()
                true
            }
        } catch (ex: SQLException) {
            logger.error("Failed to delete queue tasks for cashbox: $cashboxId", ex)
            false
        }
    }

    override fun countPendingByLane(lane: String): Long {
        val sql = "SELECT COUNT(*) FROM queue_task WHERE lane = ? AND status IN ('PENDING', 'FAILED', 'IN_PROGRESS')"
        return try {
            connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, lane)
                fetchCount(stmt)
            }
        } catch (ex: SQLException) {
            logger.error("Failed to count pending queue tasks for lane: $lane", ex)
            0L
        }
    }

    private fun mapRecord(rs: ResultSet): QueueTaskRecord {
        return QueueTaskRecord(
            id = rs.getString("id"),
            cashboxId = rs.getString("cashbox_id"),
            lane = rs.getString("lane"),
            type = rs.getString("type"),
            payloadRef = rs.getString("payload_ref"),
            createdAt = rs.getLong("created_at"),
            status = rs.getString("status"),
            attempt = rs.getInt("attempt"),
            nextAttemptAt = rs.getObject("next_attempt_at")?.let { rs.getLong("next_attempt_at") },
            lastError = rs.getString("last_error")
        )
    }

    private fun fetchList(stmt: java.sql.PreparedStatement): List<QueueTaskRecord> {
        return stmt.executeQuery().use { rs ->
            val result = mutableListOf<QueueTaskRecord>()
            while (rs.next()) {
                result.add(mapRecord(rs))
            }
            result
        }
    }

    private fun fetchCount(stmt: java.sql.PreparedStatement): Long {
        return stmt.executeQuery().use { rs ->
            if (rs.next()) rs.getLong(1) else 0L
        }
    }

    private companion object {
        /** Статусы связываются после кассы и полосы. */
        const val FIRST_STATUS_PARAMETER = 3
    }
}
