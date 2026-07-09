package io.github.texport.superkassa.jvm.storage.impl.domain.model

/**
 * Запись номенклатуры (товара/услуги) в базе данных.
 */
data class NomenclatureItemRecord(
    val cashboxId: String,
    val id: Long,
    val code: String,
    val name: String,
    val nameKk: String?,
    val price: Long, // в тиынах
    val measureUnitCode: String?,
    val vatGroup: String?,
    val version: Int
)
