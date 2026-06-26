package kz.mybrain.superkassa.storage.domain.repository

import kz.mybrain.superkassa.storage.domain.model.NomenclatureItemRecord

/**
 * Репозиторий номенклатуры ККМ.
 */
interface NomenclatureItemRepository {
    fun upsert(record: NomenclatureItemRecord): Boolean
    fun listByCashbox(cashboxId: String): List<NomenclatureItemRecord>
    fun deleteByCashbox(cashboxId: String): Boolean
    fun findByCode(cashboxId: String, code: String): NomenclatureItemRecord?
}
