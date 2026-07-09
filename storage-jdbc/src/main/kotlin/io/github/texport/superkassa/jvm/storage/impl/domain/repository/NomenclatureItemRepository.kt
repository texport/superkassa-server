package io.github.texport.superkassa.jvm.storage.impl.domain.repository

import io.github.texport.superkassa.jvm.storage.impl.domain.model.NomenclatureItemRecord

/**
 * Репозиторий номенклатуры ККМ.
 */
interface NomenclatureItemRepository {
    fun upsert(record: NomenclatureItemRecord): Boolean
    fun listByCashbox(cashboxId: String): List<NomenclatureItemRecord>
    fun deleteByCashbox(cashboxId: String): Boolean
    fun findByCode(cashboxId: String, code: String): NomenclatureItemRecord?
}
