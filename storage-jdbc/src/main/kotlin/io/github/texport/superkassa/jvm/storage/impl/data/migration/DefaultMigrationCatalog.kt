package io.github.texport.superkassa.jvm.storage.impl.data.migration

import io.github.texport.superkassa.jvm.storage.impl.application.migration.MigrationCatalog
import io.github.texport.superkassa.jvm.storage.impl.application.migration.MigrationScript
import io.github.texport.superkassa.jvm.storage.impl.domain.config.StorageEngine

/**
 * Каталог миграций по умолчанию.
 */
class DefaultMigrationCatalog : MigrationCatalog {
    override fun scriptsFor(engine: StorageEngine): List<MigrationScript> {
        val prefix = when (engine) {
            StorageEngine.SQLITE -> "db/migration/sqlite"
            StorageEngine.POSTGRES -> "db/migration/postgres"
            StorageEngine.MYSQL -> "db/migration/mysql"
        }
        return listOf(
            MigrationScript(
                version = "1",
                resourcePath = "$prefix/V1__init.sql",
                checksum = "v1"
            ),
            MigrationScript(
                version = "2",
                resourcePath = "$prefix/V2__error_log.sql",
                checksum = "v2"
            ),
            MigrationScript(
                version = "3",
                resourcePath = "$prefix/V3__shift_and_counters.sql",
                checksum = "v3"
            ),
            MigrationScript(
                version = "4",
                resourcePath = "$prefix/V4__reference_data.sql",
                checksum = "v4"
            ),
            MigrationScript(
                version = "5",
                resourcePath = "$prefix/V5__outbox_and_indexes.sql",
                checksum = "v5"
            ),
            MigrationScript(
                version = "6",
                resourcePath = "$prefix/V6__cashbox_service_info.sql",
                checksum = "v6"
            ),
            MigrationScript(
                version = "7",
                resourcePath = "$prefix/V7__kkm_operator.sql",
                checksum = "v7"
            ),
            MigrationScript(
                version = "8",
                resourcePath = "$prefix/V8__kkm_user.sql",
                checksum = "v8"
            ),
            MigrationScript(
                version = "9",
                resourcePath = "$prefix/V9__cashbox_system_id_unique.sql",
                checksum = "v9"
            ),
            MigrationScript(
                version = "10",
                resourcePath = "$prefix/V10__cashbox_auto_close_shift.sql",
                checksum = "v10"
            ),
            MigrationScript(
                version = "11",
                resourcePath = "$prefix/V11__queue_tasks_and_locks.sql",
                checksum = "v11"
            ),
            MigrationScript(
                version = "12",
                resourcePath = "$prefix/V12__cashbox_tax_regime.sql",
                checksum = "v12"
            ),
            MigrationScript(
                version = "13",
                resourcePath = "$prefix/V13__fiscal_document_receipt_url.sql",
                checksum = "v13"
            ),
            MigrationScript(
                version = "14",
                resourcePath = "$prefix/V14__nomenclature_table.sql",
                checksum = "v14"
            ),
            MigrationScript(
                version = "15",
                resourcePath = "$prefix/V15__cashbox_branding.sql",
                checksum = "v15"
            ),
            MigrationScript(
                version = "16",
                resourcePath = "$prefix/V16__printed_document_number.sql",
                checksum = "v16"
            ),
            MigrationScript(
                version = "17",
                resourcePath = "$prefix/V17__cashbox_block_reason.sql",
                checksum = "v17"
            ),
            MigrationScript(
                version = "18",
                resourcePath = "$prefix/V18__fiscal_document_ofd_error_code.sql",
                checksum = "v18"
            ),
            MigrationScript(
                version = "19",
                resourcePath = "$prefix/V19__queue_task_rejected_status.sql",
                checksum = "v19"
            ),
            // 20 занята миграцией данных ReceiptTotalsInTiynMigration.
            MigrationScript(
                version = "21",
                resourcePath = "$prefix/V21__kkm_user_drop_plain_pin.sql",
                checksum = "v21"
            ),
            MigrationScript(
                version = "22",
                resourcePath = "$prefix/V22__cashbox_ofd_address.sql",
                checksum = "v22"
            ),
            MigrationScript(
                version = "23",
                resourcePath = "$prefix/V23__cashbox_name.sql",
                checksum = "v23"
            ),
            MigrationScript(
                version = "24",
                resourcePath = "$prefix/V24__fiscal_document_ofd_error_text.sql",
                checksum = "v24"
            ),
            MigrationScript(
                version = "25",
                resourcePath = "$prefix/V25__cashbox_auto_cashout.sql",
                checksum = "v25"
            ),
            MigrationScript(
                version = "26",
                resourcePath = "$prefix/V26__pin_attempts.sql",
                checksum = "v26"
            )
        )
    }
}
