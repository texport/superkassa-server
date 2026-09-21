package io.github.texport.superkassa.jvm.storage.impl.data.jdbc

import io.github.texport.superkassa.jvm.storage.impl.domain.model.FiscalDocumentRecord
import io.github.texport.superkassa.jvm.storage.impl.domain.repository.FiscalDocumentRepository
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

/**
 * JDBC-реализация репозитория фискальных документов.
 */
class JdbcFiscalDocumentRepository(
    private val connection: Connection
) : FiscalDocumentRepository {
    override fun insert(record: FiscalDocumentRecord): Boolean {
        val sql = """
            INSERT INTO fiscal_document (
                id, cashbox_id, shift_id, doc_type, doc_no, shift_no, created_at,
                total_amount, currency, payload_bin, payload_hash, fiscal_sign,
                autonomous_sign, is_autonomous, ofd_status, delivered_at, receipt_url,
                ofd_error_code
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, record.id)
            stmt.setString(2, record.cashboxId)
            stmt.bindString(3, record.shiftId)
            stmt.setString(4, record.docType)
            stmt.bindLong(5, record.docNo)
            stmt.bindLong(6, record.shiftNo)
            stmt.setLong(7, record.createdAt)
            stmt.bindLong(8, record.totalAmount)
            stmt.bindString(9, record.currency)
            stmt.bindBytes(10, record.payloadBin)
            stmt.bindBytes(11, record.payloadHash)
            stmt.bindString(12, record.fiscalSign)
            stmt.bindString(13, record.autonomousSign)
            stmt.setInt(14, if (record.isAutonomous) 1 else 0)
            stmt.bindString(15, record.ofdStatus)
            stmt.bindLong(16, record.deliveredAt)
            stmt.bindString(17, record.receiptUrl)
            stmt.bindInt(18, record.ofdErrorCode)
            return stmt.executeUpdate() == 1
        }
    }

    override fun findById(id: String): FiscalDocumentRecord? {
        val sql = "SELECT * FROM fiscal_document WHERE id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, id)
            stmt.executeQuery().use { rs ->
                return if (rs.next()) mapRecord(rs) else null
            }
        }
    }

    override fun findByCashboxAndDocNo(cashboxId: String, docNo: Long): FiscalDocumentRecord? {
        val sql = "SELECT * FROM fiscal_document WHERE cashbox_id = ? AND doc_no = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, cashboxId)
            stmt.setLong(2, docNo)
            stmt.executeQuery().use { rs ->
                return if (rs.next()) mapRecord(rs) else null
            }
        }
    }

    override fun updatePrintedDocNo(id: String, number: Long): Boolean {
        val sql = "UPDATE fiscal_document SET printed_document_number = ? WHERE id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, number)
            stmt.setString(2, id)
            return stmt.executeUpdate() > 0
        }
    }

    override fun updateDocNo(id: String, docNo: Long): Boolean {
        val sql = "UPDATE fiscal_document SET doc_no = ? WHERE id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, docNo)
            stmt.setString(2, id)
            return stmt.executeUpdate() > 0
        }
    }

    override fun updateStatus(
        id: String,
        ofdStatus: String,
        fiscalSign: String?,
        autonomousSign: String?,
        deliveredAt: Long?,
        ofdErrorCode: Int?,
        isAutonomous: Boolean?,
        receiptUrl: String?
    ): Boolean {
        // Колонки собираются списком, а не четырьмя вариантами запроса
        // на два необязательных поля. Признак автономности и ссылку на чек
        // трогаем, только если вызывающий их передал: null у них означает
        // «оставить как было», а не «стереть».
        val columns = mutableListOf("ofd_status", "fiscal_sign", "autonomous_sign", "delivered_at", "ofd_error_code")
        val binders = mutableListOf<(PreparedStatement, Int) -> Unit>(
            { stmt, i -> stmt.setString(i, ofdStatus) },
            { stmt, i -> stmt.bindString(i, fiscalSign) },
            { stmt, i -> stmt.bindString(i, autonomousSign) },
            { stmt, i -> stmt.bindLong(i, deliveredAt) },
            { stmt, i -> stmt.bindInt(i, ofdErrorCode) }
        )
        if (isAutonomous != null) {
            columns += "is_autonomous"
            binders += { stmt, i -> stmt.setInt(i, if (isAutonomous) 1 else 0) }
        }
        if (receiptUrl != null) {
            columns += "receipt_url"
            binders += { stmt, i -> stmt.setString(i, receiptUrl) }
        }

        val sql = "UPDATE fiscal_document SET " +
            columns.joinToString(", ") { "$it = ?" } +
            " WHERE id = ?"
        connection.prepareStatement(sql).use { stmt ->
            binders.forEachIndexed { index, bind -> bind(stmt, index + 1) }
            stmt.setString(binders.size + 1, id)
            return stmt.executeUpdate() == 1
        }
    }

    override fun listByCashbox(cashboxId: String, limit: Int, offset: Int): List<FiscalDocumentRecord> {
        val sql = """
            SELECT * FROM fiscal_document
            WHERE cashbox_id = ?
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, cashboxId)
            stmt.setInt(2, limit)
            stmt.setInt(3, offset)
            stmt.executeQuery().use { rs ->
                val result = mutableListOf<FiscalDocumentRecord>()
                while (rs.next()) {
                    result.add(mapRecord(rs))
                }
                return result
            }
        }
    }

    override fun firstPaymentTime(shiftId: String, docTypes: Collection<String>): Long? {
        if (docTypes.isEmpty()) {
            return null
        }
        val places = docTypes.joinToString(", ") { "?" }
        val sql = "SELECT MIN(created_at) FROM fiscal_document WHERE shift_id = ? AND doc_type IN ($places)"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, shiftId)
            docTypes.forEachIndexed { index, type -> stmt.setString(index + 2, type) }
            stmt.executeQuery().use { rows ->
                if (!rows.next()) {
                    return null
                }
                val first = rows.getLong(1)
                return if (rows.wasNull()) null else first
            }
        }
    }

    override fun listByShift(
        cashboxId: String,
        shiftId: String,
        limit: Int,
        offset: Int
    ): List<FiscalDocumentRecord> {
        val sql = """
            SELECT * FROM fiscal_document
            WHERE cashbox_id = ? AND shift_id = ?
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, cashboxId)
            stmt.setString(2, shiftId)
            stmt.setInt(3, limit)
            stmt.setInt(4, offset)
            stmt.executeQuery().use { rs ->
                val result = mutableListOf<FiscalDocumentRecord>()
                while (rs.next()) {
                    result.add(mapRecord(rs))
                }
                return result
            }
        }
    }

    override fun listByCashboxAndCreatedAtBetween(
        cashboxId: String,
        fromInclusive: Long,
        toExclusive: Long,
        limit: Int,
        offset: Int
    ): List<FiscalDocumentRecord> {
        val sql = """
            SELECT * FROM fiscal_document
            WHERE cashbox_id = ? AND created_at >= ? AND created_at < ?
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, cashboxId)
            stmt.setLong(2, fromInclusive)
            stmt.setLong(3, toExclusive)
            stmt.setInt(4, limit)
            stmt.setInt(5, offset)
            stmt.executeQuery().use { rs ->
                val result = mutableListOf<FiscalDocumentRecord>()
                while (rs.next()) {
                    result.add(mapRecord(rs))
                }
                return result
            }
        }
    }

    override fun deleteById(id: String): Boolean {
        val sql = "DELETE FROM fiscal_document WHERE id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, id)
            return stmt.executeUpdate() == 1
        }
    }

    override fun deleteByCashbox(cashboxId: String): Boolean {
        val sql = "DELETE FROM fiscal_document WHERE cashbox_id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, cashboxId)
            stmt.executeUpdate()
            return true
        }
    }

    override fun countAll(docType: String?): Long {
        val sql = if (docType != null) {
            "SELECT COUNT(*) FROM fiscal_document WHERE doc_type = ?"
        } else {
            "SELECT COUNT(*) FROM fiscal_document"
        }
        connection.prepareStatement(sql).use { stmt ->
            if (docType != null) stmt.setString(1, docType)
            stmt.executeQuery().use { rs ->
                return if (rs.next()) rs.getLong(1) else 0L
            }
        }
    }

    private fun mapRecord(rs: ResultSet): FiscalDocumentRecord {
        return FiscalDocumentRecord(
            id = rs.getString("id"),
            cashboxId = rs.getString("cashbox_id"),
            shiftId = rs.getString("shift_id"),
            docType = rs.getString("doc_type"),
            docNo = rs.getLong("doc_no").takeIf { !rs.wasNull() },
            printedDocumentNumber = rs.getLong("printed_document_number").takeIf { !rs.wasNull() },
            shiftNo = rs.getLong("shift_no").takeIf { !rs.wasNull() },
            createdAt = rs.getLong("created_at"),
            totalAmount = rs.getLong("total_amount").takeIf { !rs.wasNull() },
            currency = rs.getString("currency"),
            payloadBin = rs.getBytes("payload_bin"),
            payloadHash = rs.getBytes("payload_hash"),
            fiscalSign = rs.getString("fiscal_sign"),
            autonomousSign = rs.getString("autonomous_sign"),
            isAutonomous = rs.getInt("is_autonomous") == 1,
            ofdStatus = rs.getString("ofd_status"),
            deliveredAt = rs.getLong("delivered_at").takeIf { !rs.wasNull() },
            receiptUrl = rs.getString("receipt_url"),
            ofdErrorCode = rs.getInt("ofd_error_code").takeIf { !rs.wasNull() }
        )
    }
}
