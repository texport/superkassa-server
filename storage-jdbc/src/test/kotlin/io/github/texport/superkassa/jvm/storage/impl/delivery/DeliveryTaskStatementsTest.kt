package io.github.texport.superkassa.jvm.storage.impl.delivery

import io.github.texport.superkassa.core.domain.api.model.delivery.DeliveryTask
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Операторы задач доставки на PostgreSQL и MySQL, которых под рукой нет:
 * постановка говорит словом своей СУБД, занятие — одним условным
 * изменением, а отказ базы не оставляет открытого оператора.
 */
class DeliveryTaskStatementsTest {
    private val sql = mutableListOf<String>()
    private val rows = mockk<ResultSet>(relaxed = true) {
        every { next() } throws SQLException("database is gone")
    }
    private val statement = mockk<PreparedStatement>(relaxed = true) {
        every { executeUpdate() } throws SQLException("database is gone")
        every { executeQuery() } returns rows
    }

    @Test
    fun `постановка пропускает известную задачу словом своей СУБД`() {
        runCatching { repository("PostgreSQL").addAll(listOf(TASK)) }
        runCatching { repository("MySQL").addAll(listOf(TASK)) }

        assertEquals(true, sql[0].endsWith("ON CONFLICT (id) DO NOTHING"), sql[0])
        assertEquals(true, sql[1].startsWith("INSERT IGNORE INTO delivery_tasks"), sql[1])
    }

    @Test
    fun `занятие - одно изменение ожидающей задачи, чей срок наступил`() {
        runCatching { repository("PostgreSQL").claim(TASK.id, now = 1, leaseUntil = 2) }

        assertEquals(
            "UPDATE delivery_tasks SET attempts = attempts + 1, next_attempt_at = ?, updated_at = ? " +
                "WHERE id = ? AND status = 'PENDING' AND next_attempt_at <= ?",
            sql.single()
        )
    }

    @Test
    fun `отказ базы выходит наружу и закрывает оператор и выборку`() {
        val repository = repository("PostgreSQL")
        val operations = listOf<() -> Unit>(
            { repository.addAll(listOf(TASK)) },
            { repository.due(now = 1, limit = 1) },
            { repository.claim(TASK.id, now = 1, leaseUntil = 2) },
            { repository.save(TASK) },
            { repository.byDocument(TASK.documentId) },
            { repository.deleteByCashbox(TASK.kkmId) }
        )

        operations.forEach { assertFailsWith<SQLException> { it() } }

        verify(exactly = operations.size) { statement.close() }
        verify(exactly = 2) { rows.close() }
    }

    private fun repository(product: String): JdbcDeliveryTaskRepository {
        val text = slot<String>()
        val connection = mockk<Connection> {
            every { metaData.databaseProductName } returns product
            every { prepareStatement(capture(text)) } answers { sql += text.captured; statement }
        }
        return JdbcDeliveryTaskRepository(connection)
    }

    private companion object {
        val TASK = DeliveryTask(
            id = DeliveryTask.idOf("doc-1", "SMS", "HTML"),
            kkmId = "kkm-1",
            documentId = "doc-1",
            channel = "SMS",
            destination = "+77017654321",
            payloadType = "HTML",
            nextAttemptAt = 1,
            createdAt = 1
        )
    }
}
