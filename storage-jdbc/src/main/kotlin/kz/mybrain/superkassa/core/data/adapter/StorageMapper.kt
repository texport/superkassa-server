package kz.mybrain.superkassa.core.data.adapter

import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kz.mybrain.superkassa.core.application.error.ErrorMessages
import kz.mybrain.superkassa.core.application.error.ValidationException
import kz.mybrain.superkassa.core.domain.model.*
import kz.mybrain.superkassa.storage.application.session.StorageSession
import kz.mybrain.superkassa.storage.domain.model.FiscalDocumentRecord
import kz.mybrain.superkassa.storage.domain.model.KkmUserRecord
import kz.mybrain.superkassa.storage.domain.model.ShiftRecord
import org.slf4j.LoggerFactory
import java.util.Base64
import kz.mybrain.superkassa.storage.domain.model.CashboxRecord as StorageKkmRecord

object StorageMapper {
    private val logger = LoggerFactory.getLogger(StorageMapper::class.java)
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
                json.decodeFromString(ReceiptBranding.serializer(), it)
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
            brandingJson = json.encodeToString(ReceiptBranding.serializer(), info.branding)
        )
    }

    fun encodeServiceInfo(info: OfdServiceInfo?): String? {
        if (info == null) return null
        return runCatching { json.encodeToString(serializer<OfdServiceInfo>(), info) }.getOrNull()
    }

    fun decodeServiceInfo(payload: String?): OfdServiceInfo? {
        if (payload.isNullOrBlank()) return null
        return runCatching { json.decodeFromString(serializer<OfdServiceInfo>(), payload) }.getOrNull()
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
            logger.error("Invalid Base64 format in database", ex)
            throw ValidationException(
                ErrorMessages.invalidBase64Format(),
                "INVALID_BASE64_FORMAT"
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
            logger.error("Invalid user role in database: $roleString", ex)
            throw ValidationException(
                ErrorMessages.userRoleInvalid(roleString),
                "INVALID_USER_ROLE"
            )
        }
    }

    fun parseShiftStatus(statusString: String): ShiftStatus {
        return try {
            ShiftStatus.valueOf(statusString)
        } catch (ex: IllegalArgumentException) {
            logger.error("Invalid shift status in database: $statusString", ex)
            throw ValidationException(
                ErrorMessages.shiftStatusInvalid(statusString),
                "INVALID_SHIFT_STATUS"
            )
        }
    }

    fun parseTaxRegime(value: String?): TaxRegime {
        if (value.isNullOrBlank()) return TaxRegime.NO_VAT
        return try {
            TaxRegime.valueOf(value)
        } catch (ex: IllegalArgumentException) {
            logger.error("Invalid tax regime in database: $value", ex)
            TaxRegime.NO_VAT
        }
    }

    fun parseVatGroup(value: String?): VatGroup {
        if (value.isNullOrBlank()) return VatGroup.NO_VAT
        return try {
            VatGroup.valueOf(value)
        } catch (ex: IllegalArgumentException) {
            logger.error("Invalid VAT group in database: $value", ex)
            VatGroup.NO_VAT
        }
    }

    fun toFiscalDocumentSnapshot(r: FiscalDocumentRecord, session: StorageSession): FiscalDocumentSnapshot {
        val cashbox = session.cashboxes.findById(r.cashboxId)
        val serviceInfo = cashbox?.ofdServiceInfoJson?.let {
            try {
                json.decodeFromString<OfdServiceInfo>(it)
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
            registrationNumber = cashbox?.registrationNumber,
            taxpayerName = serviceInfo?.orgTitle,
            taxpayerBin = serviceInfo?.orgInn,
            taxpayerAddress = serviceInfo?.orgAddress,
            factoryNumber = cashbox?.factoryNumber,
            ofdProvider = cashbox?.ofdProvider
        )
    }
}
