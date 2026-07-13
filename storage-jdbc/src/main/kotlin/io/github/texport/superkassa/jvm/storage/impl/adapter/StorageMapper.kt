@file:Suppress("WildcardImport", "AnnotationOnSeparateLine")

package io.github.texport.superkassa.jvm.storage.impl.adapter

import io.github.texport.superkassa.core.domain.api.exception.StorageException
import io.github.texport.superkassa.core.domain.api.model.auth.KkmUser
import io.github.texport.superkassa.core.domain.api.model.auth.UserRole
import io.github.texport.superkassa.core.domain.api.model.common.*
import io.github.texport.superkassa.core.domain.api.model.kkm.*
import io.github.texport.superkassa.core.domain.api.model.ofd.*
import io.github.texport.superkassa.core.domain.api.model.receipt.*
import io.github.texport.superkassa.core.domain.api.model.shift.*
import io.github.texport.superkassa.core.presentation.api.model.kkm.OfdServiceInfoResponse
import io.github.texport.superkassa.core.presentation.api.model.kkm.ReceiptBrandingResponse
import io.github.texport.superkassa.core.string.api.TrilingualMessage
import io.github.texport.superkassa.jvm.shared.strings.api.key.StorageErrorKey
import io.github.texport.superkassa.jvm.shared.strings.impl.DefaultErrorResolver
import io.github.texport.superkassa.jvm.storage.impl.application.session.StorageSession
import io.github.texport.superkassa.jvm.storage.impl.domain.model.FiscalDocumentRecord
import io.github.texport.superkassa.jvm.storage.impl.domain.model.KkmUserRecord
import io.github.texport.superkassa.jvm.storage.impl.domain.model.ShiftRecord
import kotlinx.serialization.json.Json
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
                val dto = json.decodeFromString(ReceiptBrandingResponse.serializer(), it)
                mapToDomain(dto)
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
        val dto = mapToResponse(info.branding)
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
            brandingJson = json.encodeToString(ReceiptBrandingResponse.serializer(), dto)
        )
    }

    fun encodeServiceInfo(info: OfdServiceInfo?): String? {
        if (info == null) return null
        val dto = mapToResponse(info)
        return runCatching { json.encodeToString(OfdServiceInfoResponse.serializer(), dto) }.getOrNull()
    }

    fun decodeServiceInfo(payload: String?): OfdServiceInfo? {
        if (payload.isNullOrBlank()) return null
        return runCatching {
            val dto = json.decodeFromString(OfdServiceInfoResponse.serializer(), payload)
            mapToDomain(dto)
        }.getOrNull()
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
                val dto = json.decodeFromString(OfdServiceInfoResponse.serializer(), it)
                mapToDomain(dto)
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

    private fun mapToResponse(branding: ReceiptBranding): ReceiptBrandingResponse = ReceiptBrandingResponse(
        language = io.github.texport.superkassa.core.presentation.api.model.kkm.ReceiptLanguage.valueOf(branding.language.name),
        headerLogoUrl = branding.headerLogoUrl,
        paperWidthMm = branding.paperWidthMm,
        themeColor = branding.themeColor,
        beforeHeaderMsg = branding.beforeHeaderMsg,
        headerMsg = branding.headerMsg,
        afterHeaderMsg = branding.afterHeaderMsg,
        beforeItemsMsg = branding.beforeItemsMsg,
        afterItemsMsg = branding.afterItemsMsg,
        beforeTotalsMsg = branding.beforeTotalsMsg,
        afterTotalsMsg = branding.afterTotalsMsg,
        beforeQrMsg = branding.beforeQrMsg,
        footerMsg = branding.footerMsg,
        useForceDarkTheme = branding.useForceDarkTheme,
        customBackgroundColorHex = branding.customBackgroundColorHex,
        customCardTopBorderColorHex = branding.customCardTopBorderColorHex,
        ofdTicketAds = branding.ofdTicketAds,
        printOfdTicketAds = branding.printOfdTicketAds
    )

    private fun mapToDomain(dto: ReceiptBrandingResponse): ReceiptBranding = ReceiptBranding(
        language = io.github.texport.superkassa.core.domain.api.model.receipt.ReceiptLanguage.valueOf(dto.language.name),
        headerLogoUrl = dto.headerLogoUrl,
        paperWidthMm = dto.paperWidthMm,
        themeColor = dto.themeColor,
        beforeHeaderMsg = dto.beforeHeaderMsg,
        headerMsg = dto.headerMsg,
        afterHeaderMsg = dto.afterHeaderMsg,
        beforeItemsMsg = dto.beforeItemsMsg,
        afterItemsMsg = dto.afterItemsMsg,
        beforeTotalsMsg = dto.beforeTotalsMsg,
        afterTotalsMsg = dto.afterTotalsMsg,
        beforeQrMsg = dto.beforeQrMsg,
        footerMsg = dto.footerMsg,
        useForceDarkTheme = dto.useForceDarkTheme,
        customBackgroundColorHex = dto.customBackgroundColorHex,
        customCardTopBorderColorHex = dto.customCardTopBorderColorHex,
        ofdTicketAds = dto.ofdTicketAds,
        printOfdTicketAds = dto.printOfdTicketAds
    )

    private fun mapToResponse(info: OfdServiceInfo): OfdServiceInfoResponse = OfdServiceInfoResponse(
        orgTitle = info.orgTitle,
        orgAddress = info.orgAddress,
        orgAddressKz = info.orgAddressKz,
        orgInn = info.orgInn,
        orgOkved = info.orgOkved,
        geoLatitude = info.geoLatitude,
        geoLongitude = info.geoLongitude,
        geoSource = info.geoSource
    )

    private fun mapToDomain(dto: OfdServiceInfoResponse): OfdServiceInfo = OfdServiceInfo(
        orgTitle = dto.orgTitle,
        orgAddress = dto.orgAddress,
        orgAddressKz = dto.orgAddressKz,
        orgInn = dto.orgInn,
        orgOkved = dto.orgOkved,
        geoLatitude = dto.geoLatitude,
        geoLongitude = dto.geoLongitude,
        geoSource = dto.geoSource
    )
}
