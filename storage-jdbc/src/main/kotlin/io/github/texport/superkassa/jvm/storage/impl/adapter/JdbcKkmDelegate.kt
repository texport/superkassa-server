package io.github.texport.superkassa.jvm.storage.impl.adapter

import io.github.texport.superkassa.core.domain.api.model.auth.KkmUser
import io.github.texport.superkassa.core.domain.api.model.auth.UserRole
import io.github.texport.superkassa.core.domain.api.model.kkm.KkmInfo
import io.github.texport.superkassa.jvm.storage.impl.application.session.StorageSession
import io.github.texport.superkassa.jvm.storage.impl.domain.model.KkmUserRecord

class JdbcKkmDelegate(private val sessionProvider: () -> StorageSession) {

    fun createKkm(info: KkmInfo): Boolean {
        return sessionProvider().cashboxes.insert(StorageMapper.mapKkmToRecord(info))
    }

    fun updateKkm(info: KkmInfo): Boolean {
        return sessionProvider().cashboxes.update(StorageMapper.mapKkmToRecord(info))
    }

    fun findKkm(id: String): KkmInfo? {
        return sessionProvider().cashboxes.findById(id)?.let { StorageMapper.mapKkm(it) }
    }

    fun findKkmForUpdate(id: String): KkmInfo? {
        return sessionProvider().cashboxes.findByIdForUpdate(id)?.let { StorageMapper.mapKkm(it) }
    }

    fun findKkmByRegistrationNumber(registrationNumber: String): KkmInfo? {
        return sessionProvider().cashboxes.findByRegistrationNumber(
            registrationNumber
        )?.let { StorageMapper.mapKkm(it) }
    }

    fun findKkmBySystemId(systemId: String): KkmInfo? {
        return sessionProvider().cashboxes.findBySystemId(systemId)?.let { StorageMapper.mapKkm(it) }
    }

    fun listKkms(
        limit: Int,
        offset: Int,
        state: String?,
        search: String?,
        sortBy: String,
        sortOrder: String
    ): List<KkmInfo> {
        return sessionProvider().cashboxes.listAllFiltered(
            limit = limit,
            offset = offset,
            state = state,
            search = search,
            sortBy = sortBy,
            sortOrder = sortOrder
        ).map { StorageMapper.mapKkm(it) }
    }

    fun countKkms(state: String?, search: String?): Int {
        return sessionProvider().cashboxes.countAll(state, search)
    }

    fun deleteKkm(id: String): Boolean {
        return sessionProvider().cashboxes.deleteById(id)
    }

    fun createUser(
        kkmId: String,
        userId: String,
        name: String,
        role: UserRole,
        pin: String,
        pinHash: String,
        createdAt: Long
    ): Boolean {
        return sessionProvider().users.insert(
            KkmUserRecord(
                cashboxId = kkmId,
                id = userId,
                name = name,
                role = role.name,
                pin = pin,
                pinHash = pinHash,
                createdAt = createdAt
            )
        )
    }

    fun updateUser(
        kkmId: String,
        userId: String,
        name: String?,
        role: UserRole?,
        pin: String?,
        pinHash: String?
    ): Boolean {
        return sessionProvider().users.update(
            cashboxId = kkmId,
            userId = userId,
            name = name,
            role = role?.name,
            pin = pin,
            pinHash = pinHash
        )
    }

    fun deleteUser(kkmId: String, userId: String): Boolean {
        return sessionProvider().users.deleteById(kkmId, userId)
    }

    fun deleteUsersByKkm(kkmId: String): Boolean {
        return sessionProvider().users.deleteByCashbox(kkmId)
    }

    fun listUsers(kkmId: String): List<KkmUser> {
        return sessionProvider().users.listByCashbox(kkmId).map { StorageMapper.mapUser(it) }
    }

    fun findUserById(kkmId: String, userId: String): KkmUser? {
        return sessionProvider().users.findById(kkmId, userId)?.let { StorageMapper.mapUser(it) }
    }

    fun findUserByPin(kkmId: String, pinHash: String): KkmUser? {
        return sessionProvider().users.findByCashboxAndPinHash(kkmId, pinHash)?.let { StorageMapper.mapUser(it) }
    }
}
