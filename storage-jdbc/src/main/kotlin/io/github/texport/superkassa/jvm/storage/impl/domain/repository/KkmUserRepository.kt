package io.github.texport.superkassa.jvm.storage.impl.domain.repository

import io.github.texport.superkassa.jvm.storage.impl.domain.model.KkmUserRecord

/**
 * Репозиторий пользователей ККМ.
 */
interface KkmUserRepository {
    fun insert(record: KkmUserRecord): Boolean
    fun update(
        cashboxId: String,
        userId: String,
        name: String?,
        role: String?,
        pin: String?,
        pinHash: String?
    ): Boolean
    fun deleteById(cashboxId: String, userId: String): Boolean
    fun deleteByCashbox(cashboxId: String): Boolean
    fun listByCashbox(cashboxId: String): List<KkmUserRecord>
    fun findById(cashboxId: String, userId: String): KkmUserRecord?
    fun findByCashboxAndPinHash(cashboxId: String, pinHash: String): KkmUserRecord?
}
