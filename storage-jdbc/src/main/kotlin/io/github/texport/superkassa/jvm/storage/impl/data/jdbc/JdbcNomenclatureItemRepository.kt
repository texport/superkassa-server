package io.github.texport.superkassa.jvm.storage.impl.data.jdbc

import io.github.texport.superkassa.jvm.storage.impl.domain.model.NomenclatureItemRecord
import io.github.texport.superkassa.jvm.storage.impl.domain.repository.NomenclatureItemRepository
import java.sql.Connection
import java.sql.ResultSet

/**
 * JDBC-реализация репозитория номенклатуры ККМ.
 */
class JdbcNomenclatureItemRepository(
    private val connection: Connection
) : NomenclatureItemRepository {

    override fun upsert(record: NomenclatureItemRecord): Boolean {
        val deleteSql = """
            DELETE FROM nomenclature_item
            WHERE cashbox_id = ? AND id = ?
        """.trimIndent()
        connection.prepareStatement(deleteSql).use { stmt ->
            stmt.setString(1, record.cashboxId)
            stmt.setLong(2, record.id)
            stmt.executeUpdate()
        }

        val insertSql = """
            INSERT INTO nomenclature_item (
                cashbox_id, id, code, name, name_kk, price, measure_unit_code, vat_group, version
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        connection.prepareStatement(insertSql).use { stmt ->
            stmt.setString(1, record.cashboxId)
            stmt.setLong(2, record.id)
            stmt.setString(3, record.code)
            stmt.setString(4, record.name)
            stmt.bindString(5, record.nameKk)
            stmt.setLong(6, record.price)
            stmt.bindString(7, record.measureUnitCode)
            stmt.bindString(8, record.vatGroup)
            stmt.setInt(9, record.version)
            return stmt.executeUpdate() == 1
        }
    }

    override fun listByCashbox(cashboxId: String): List<NomenclatureItemRecord> {
        val sql = """
            SELECT * FROM nomenclature_item
            WHERE cashbox_id = ?
            ORDER BY id ASC
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, cashboxId)
            stmt.executeQuery().use { rs ->
                val result = mutableListOf<NomenclatureItemRecord>()
                while (rs.next()) {
                    result.add(mapRecord(rs))
                }
                return result
            }
        }
    }

    override fun deleteByCashbox(cashboxId: String): Boolean {
        val sql = "DELETE FROM nomenclature_item WHERE cashbox_id = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, cashboxId)
            stmt.executeUpdate()
            return true
        }
    }

    override fun findByCode(cashboxId: String, code: String): NomenclatureItemRecord? {
        val sql = """
            SELECT * FROM nomenclature_item
            WHERE cashbox_id = ? AND code = ?
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, cashboxId)
            stmt.setString(2, code)
            stmt.executeQuery().use { rs ->
                return if (rs.next()) mapRecord(rs) else null
            }
        }
    }

    private fun mapRecord(rs: ResultSet): NomenclatureItemRecord {
        return NomenclatureItemRecord(
            cashboxId = rs.getString("cashbox_id"),
            id = rs.getLong("id"),
            code = rs.getString("code"),
            name = rs.getString("name"),
            nameKk = rs.getString("name_kk"),
            price = rs.getLong("price"),
            measureUnitCode = rs.getString("measure_unit_code"),
            vatGroup = rs.getString("vat_group"),
            version = rs.getInt("version")
        )
    }
}
