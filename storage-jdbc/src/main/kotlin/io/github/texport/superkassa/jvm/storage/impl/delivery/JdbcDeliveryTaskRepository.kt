package io.github.texport.superkassa.jvm.storage.impl.delivery

import io.github.texport.superkassa.core.domain.api.model.delivery.DeliveryFailure
import io.github.texport.superkassa.core.domain.api.model.delivery.DeliveryTask
import io.github.texport.superkassa.core.domain.api.model.delivery.DeliveryTaskStatus
import io.github.texport.superkassa.core.string.api.TrilingualMessage
import io.github.texport.superkassa.jvm.storage.impl.data.jdbc.SqlDialect
import io.github.texport.superkassa.jvm.storage.impl.data.jdbc.mapList
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

/**
 * JDBC-реализация задач доставки чека (delivery_tasks) для SQLite, PostgreSQL и MySQL.
 *
 * Занятие задачи — один условный `UPDATE`: второй отправитель, будь то
 * другой такт фона, ручной повтор или другой узел на той же базе, ту же
 * задачу не займёт. PostgreSQL и MySQL после ожидания замка строки
 * проверяют условие заново и видят уже перенесённый срок, SQLite
 * пишет в базу по одному.
 *
 * Причина отказа хранится кодом и тремя языками по колонкам: журнал
 * показывает её на языке кассира, не разбирая склеенную строку.
 */
class JdbcDeliveryTaskRepository(private val connection: Connection) : DeliveryTaskRepository {
    private val dialect = SqlDialect(connection)

    override fun addAll(tasks: List<DeliveryTask>) {
        connection.prepareStatement(dialect.insertIfAbsent(INSERT, "id")).use { stmt ->
            for (task in tasks) {
                stmt.bindAll(listOf(task.id, task.kkmId, task.documentId, task.channel, task.destination, task.payloadType) + task.state())
                stmt.executeUpdate()
            }
        }
    }

    override fun due(now: Long, limit: Int): List<DeliveryTask> =
        query("$SELECT WHERE status = 'PENDING' AND next_attempt_at <= ? ORDER BY next_attempt_at, id LIMIT ?", now, limit)

    override fun claim(id: String, now: Long, leaseUntil: Long): Boolean {
        val sql = "UPDATE delivery_tasks SET attempts = attempts + 1, next_attempt_at = ?, updated_at = ? " +
            "WHERE id = ? AND status = 'PENDING' AND next_attempt_at <= ?"
        return connection.prepareStatement(sql).use { stmt ->
            stmt.bindAll(listOf(leaseUntil, now, id, now))
            stmt.executeUpdate() > 0
        }
    }

    override fun save(task: DeliveryTask) {
        val sql = "UPDATE delivery_tasks SET status = ?, attempts = ?, next_attempt_at = ?, failure_code = ?, " +
            "failure_ru = ?, failure_kk = ?, failure_en = ?, created_at = ?, updated_at = ? WHERE id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.bindAll(task.state() + task.id)
            stmt.executeUpdate()
        }
    }

    override fun byDocument(documentId: String): List<DeliveryTask> =
        query("$SELECT WHERE document_id = ? ORDER BY created_at, id", documentId)

    override fun deleteByCashbox(cashboxId: String) {
        connection.prepareStatement("DELETE FROM delivery_tasks WHERE cashbox_id = ?").use { stmt ->
            stmt.setString(1, cashboxId)
            stmt.executeUpdate()
        }
    }

    private fun query(sql: String, vararg params: Any): List<DeliveryTask> =
        connection.prepareStatement(sql).use { stmt ->
            stmt.bindAll(params.asList())
            stmt.executeQuery().use { rs -> rs.mapList { it.toTask() } }
        }

    /** Изменяемая часть задачи в порядке колонок от `status` до `updated_at`. */
    private fun DeliveryTask.state(): List<Any?> = listOf(
        status.name, attempts, nextAttemptAt, failure?.code,
        failure?.message?.ru, failure?.message?.kk, failure?.message?.en, createdAt, updatedAt
    )

    /** Значения по порядку параметров; `null` — строка, у задачи других пустых полей нет. */
    private fun PreparedStatement.bindAll(values: List<Any?>) = values.forEachIndexed { index, value ->
        when (value) {
            is Int -> setInt(index + 1, value)
            is Long -> setLong(index + 1, value)
            else -> setString(index + 1, value as String?)
        }
    }

    private fun ResultSet.toTask() = DeliveryTask(
        id = getString("id"),
        kkmId = getString("cashbox_id"),
        documentId = getString("document_id"),
        channel = getString("channel"),
        destination = getString("destination"),
        payloadType = getString("payload_type"),
        status = DeliveryTaskStatus.valueOf(getString("status")),
        attempts = getInt("attempts"),
        nextAttemptAt = getLong("next_attempt_at"),
        failure = getString("failure_code")?.let { code ->
            DeliveryFailure(code, TrilingualMessage(getString("failure_ru"), getString("failure_kk"), getString("failure_en")))
        },
        createdAt = getLong("created_at"),
        updatedAt = getLong("updated_at")
    )

    private companion object {
        const val COLUMNS = "id, cashbox_id, document_id, channel, destination, payload_type, status, attempts, " +
            "next_attempt_at, failure_code, failure_ru, failure_kk, failure_en, created_at, updated_at"
        const val SELECT = "SELECT $COLUMNS FROM delivery_tasks"
        const val INSERT = "INSERT INTO delivery_tasks ($COLUMNS) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
    }
}
