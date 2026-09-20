package io.github.texport.superkassa.jvm.storage.impl.application.migration

import java.sql.Connection

/**
 * Миграция данных, а не схемы.
 *
 * Нужна там, где новое значение нельзя вычислить средствами SQL: сумма
 * документа лежит в его же нагрузке в виде JSON, и разбирать её тремя
 * диалектами по-разному значило бы завести три способа ошибиться.
 *
 * Применяется один раз и отмечается в той же таблице `schema_migrations`,
 * что и схемные: порядок применения и учёт у них общие.
 */
interface DataMigration {

    /** Версия: уникальна среди всех миграций, схемных и данных. */
    val version: String

    val checksum: String

    /** Человеку — зачем эта миграция; попадает в журнал при применении. */
    val description: String

    fun apply(connection: Connection)
}
