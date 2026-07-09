package io.github.texport.superkassa.jvm.storage.impl.adapter

import io.github.texport.superkassa.jvm.shared.strings.api.key.StorageErrorKey
import io.github.texport.superkassa.jvm.shared.strings.impl.DefaultErrorResolver
import io.github.texport.superkassa.jvm.storage.impl.application.session.StorageSession
import io.github.texport.superkassa.jvm.storage.impl.domain.model.FiscalDocumentRecord
import io.github.texport.superkassa.jvm.storage.impl.domain.model.KkmUserRecord
import io.github.texport.superkassa.jvm.storage.impl.domain.model.ShiftRecord
import kotlinx.serialization.json.Json
import kz.mybrain.superkassa.core.domain.exception.StorageException
import kz.mybrain.superkassa.core.domain.exception.TrilingualMessage
import kz.mybrain.superkassa.core.domain.model.auth.KkmUser
import kz.mybrain.superkassa.core.domain.model.auth.UserRole
import kz.mybrain.superkassa.core.domain.model.common.*
import kz.mybrain.superkassa.core.domain.model.kkm.*
import kz.mybrain.superkassa.core.domain.model.ofd.*
import kz.mybrain.superkassa.core.domain.model.receipt.*
import kz.mybrain.superkassa.core.domain.model.shift.*
import kz.mybrain.superkassa.core.presentation.model.OfdServiceInfoDto
import kz.mybrain.superkassa.core.presentation.model.ReceiptBrandingDto
import kz.mybrain.superkassa.core.presentation.model.toDomain
import kz.mybrain.superkassa.core.presentation.model.toDto
import org.slf4j.LoggerFactory
import java.util.Base64
import io.github.texport.superkassa.jvm.storage.impl.domain.model.CashboxRecord as StorageKkmRecord

/**
 * Преобразователь (маппер) между сущностями базы данных (Records) и доменными моделями Superkassa.
 * Обеспечивает преобразование типов и валидацию данных при чтении из БД.
 */
object StorageMapper {
    private val logger = LoggerFactory.getLogger(StorageMapper::class.java)
    private val resolver = DefaultErrorResolver()
    private val json = Json { ignoreUnknownKeys = true }

    fun mapShift(record: ShiftRecord): ShiftInfo {
        return ShiftInfo(
            id = record.id,
            kkmId = record.cashboxId,
            shiftNo = record.shiftNo,
            status = parseShiftStatus(record.status),
            openedAt = record.openedAt,
            closedAt = record.closedAt,
            openDocumentId = record.openDocumentId,
            closeDocumentId = record.closeDocumentId
        )
    }

    fun mapKkm(record: StorageKkmRecord): KkmInfo {
        val branding = record.brandingJson?.let {
            try {
                json.decodeFromString(ReceiptBrandingDto.serializer(), it).toDomain()
            } catch (_: Exception) {
                ReceiptBranding()
            }
        } ?: ReceiptBranding()
        return KkmInfo(
            id = record.id,
            createdAt = record.createdAt,
            updatedAt = record.updatedAt,
            mode = record.mode,
            state = record.state,
            ofdProvider = record.ofdProvider,
            registrationNumber = record.registrationNumber,
            factoryNumber = record.factoryNumber,
            manufactureYear = record.manufactureYear,
            systemId = record.systemId,
            ofdServiceInfo = decodeServiceInfo(record.ofdServiceInfoJson),
            tokenEncryptedBase64 = encodeBase64(record.tokenEncrypted),
            tokenUpdatedAt = record.tokenUpdatedAt,
            lastShiftNo = record.lastShiftNo,
            lastReceiptNo = record.lastReceiptNo,
            lastZReportNo = record.lastZReportNo,
            autonomousSince = record.autonomousSince,
            autoCloseShift = record.autoCloseShift,
            lastFiscalHashBase64 = encodeBase64(record.lastFiscalHash),
            taxRegime = parseTaxRegime(record.taxRegime),
            defaultVatGroup = parseVatGroup(record.defaultVatGroup),
            branding = branding
        )
    }

    fun mapKkmToRecord(info: KkmInfo): StorageKkmRecord {
        return StorageKkmRecord(
            id = info.id,
            createdAt = info.createdAt,
            updatedAt = info.updatedAt,
            mode = info.mode,
            state = info.state,
            ofdProvider = info.ofdProvider,
            registrationNumber = info.registrationNumber,
            factoryNumber = info.factoryNumber,
            manufactureYear = info.manufactureYear,
            systemId = info.systemId,
            ofdServiceInfoJson = encodeServiceInfo(info.ofdServiceInfo),
            tokenEncrypted = decodeBase64(info.tokenEncryptedBase64),
            tokenUpdatedAt = info.tokenUpdatedAt,
            lastShiftNo = info.lastShiftNo,
            lastReceiptNo = info.lastReceiptNo,
            lastZReportNo = info.lastZReportNo,
            autonomousSince = info.autonomousSince,
            autoCloseShift = info.autoCloseShift,
            lastFiscalHash = decodeBase64(info.lastFiscalHashBase64),
            taxRegime = info.taxRegime.name,
            defaultVatGroup = info.defaultVatGroup.name,
            brandingJson = json.encodeToString(ReceiptBrandingDto.serializer(), info.branding.toDto())
        )
    }

    fun encodeServiceInfo(info: OfdServiceInfo?): String? {
        if (info == null) return null
        return runCatching { json.encodeToString(OfdServiceInfoDto.serializer(), info.toDto()) }.getOrNull()
    }

    fun decodeServiceInfo(payload: String?): OfdServiceInfo? {
        if (payload.isNullOrBlank()) return null
        return runCatching { json.decodeFromString(OfdServiceInfoDto.serializer(), payload).toDomain() }.getOrNull()
    }

    fun encodeBase64(bytes: ByteArray?): String? {
        if (bytes == null) return null
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun decodeBase64(value: String?): ByteArray? {
        if (value.isNullOrBlank()) return null
        return try {
            Base64.getDecoder().decode(value)
        } catch (ex: IllegalArgumentException) {
            val msg = resolver.resolve(StorageErrorKey.INVALID_BASE_64_FORMAT)
            logger.error(msg.en, ex)
            throw StorageException(
                TrilingualMessage(
                    ru = msg.ru,
                    kk = msg.kk,
                    en = msg.en
                ),
                "INVALID_BASE64_FORMAT",
                ex
            )
        }
    }

    fun mapUser(record: KkmUserRecord): KkmUser {
        return KkmUser(
            id = record.id,
            name = record.name,
            role = parseUserRole(record.role),
            pin = record.pin,
            createdAt = record.createdAt
        )
    }

    fun parseUserRole(roleString: String): UserRole {
        return try {
            UserRole.valueOf(roleString)
        } catch (ex: IllegalArgumentException) {
            val msg = resolver.resolve(StorageErrorKey.USER_ROLE_INVALID).formatArgs(roleString)
            logger.error(msg.en, ex)
            throw StorageException(
                TrilingualMessage(
                    ru = msg.ru,
                    kk = msg.kk,
                    en = msg.en
                ),
                "INVALID_USER_ROLE",
                ex
            )
        }
    }

    fun parseShiftStatus(statusString: String): ShiftStatus {
        return try {
            ShiftStatus.valueOf(statusString)
        } catch (ex: IllegalArgumentException) {
            val msg = resolver.resolve(StorageErrorKey.SHIFT_STATUS_INVALID).formatArgs(statusString)
            logger.error(msg.en, ex)
            throw StorageException(
                TrilingualMessage(
                    ru = msg.ru,
                    kk = msg.kk,
                    en = msg.en
                ),
                "INVALID_SHIFT_STATUS",
                ex
            )
        }
    }

    fun parseTaxRegime(value: String?): TaxRegime {
        if (value.isNullOrBlank()) return TaxRegime.NO_VAT
        return try {
            TaxRegime.valueOf(value)
        } catch (ex: IllegalArgumentException) {
            val msg = resolver.resolve(StorageErrorKey.INVALID_TAX_REGIME).formatArgs(value)
            logger.error(msg.en, ex)
            TaxRegime.NO_VAT
        }
    }

    fun parseVatGroup(value: String?): VatGroup {
        if (value.isNullOrBlank()) return VatGroup.NO_VAT
        return try {
            VatGroup.valueOf(value)
        } catch (ex: IllegalArgumentException) {
            val msg = resolver.resolve(StorageErrorKey.INVALID_VAT_GROUP).formatArgs(value)
            logger.error(msg.en, ex)
            VatGroup.NO_VAT
        }
    }

    fun toFiscalDocumentSnapshot(r: FiscalDocumentRecord, session: StorageSession): FiscalDocumentSnapshot {
        val cashbox = session.cashboxes.findById(r.cashboxId)
        val serviceInfo = cashbox?.ofdServiceInfoJson?.let {
            try {
                json.decodeFromString(OfdServiceInfoDto.serializer(), it).toDomain()
            } catch (_: Exception) {
                null
            }
        }
        return FiscalDocumentSnapshot(
            id = r.id,
            cashboxId = r.cashboxId,
            shiftId = r.shiftId ?: "",
            docType = r.docType,
            docNo = r.docNo,
            shiftNo = r.shiftNo,
            createdAt = r.createdAt,
            totalAmount = r.totalAmount,
            currency = r.currency,
            fiscalSign = r.fiscalSign,
            autonomousSign = r.autonomousSign,
            isAutonomous = r.isAutonomous,
            ofdStatus = r.ofdStatus,
            deliveredAt = r.deliveredAt,
            receiptUrl = r.receiptUrl,
            registrationNumber = cashbox?.registrationNumber,
            taxpayerName = serviceInfo?.orgTitle,
            taxpayerBin = serviceInfo?.orgInn,
            taxpayerAddress = serviceInfo?.orgAddress,
            factoryNumber = cashbox?.factoryNumber,
            ofdProvider = cashbox?.ofdProvider
        )
    }
}
