package io.github.texport.superkassa.jvm.receipt.impl

import io.github.texport.superkassa.core.domain.api.model.common.Money
import io.github.texport.superkassa.core.domain.api.model.kkm.FiscalDocumentSnapshot
import io.github.texport.superkassa.core.domain.api.model.kkm.KkmInfo
import io.github.texport.superkassa.core.domain.api.model.ofd.OfdServiceInfo
import io.github.texport.superkassa.core.domain.api.model.receipt.PaymentType
import io.github.texport.superkassa.core.domain.api.model.receipt.ReceiptBranding
import io.github.texport.superkassa.core.domain.api.model.receipt.ReceiptItem
import io.github.texport.superkassa.core.domain.api.model.receipt.ReceiptLayoutType
import io.github.texport.superkassa.core.domain.api.model.receipt.ReceiptOperationType
import io.github.texport.superkassa.core.domain.api.model.receipt.ReceiptPayment
import io.github.texport.superkassa.core.domain.api.model.receipt.ReceiptRequest
import io.github.texport.superkassa.core.domain.api.model.shift.ShiftInfo
import io.github.texport.superkassa.core.domain.api.model.shift.ShiftStatus
import io.github.texport.superkassa.receiptrenderer.api.createReceiptRendererApi

/**
 * Печатные формы для проверок: чек продажи, чек возврата и Z-отчёт.
 *
 * Формы рисует тот же модуль ядра, что и на кассе, поэтому проверки
 * образа идут по настоящей разметке, а не по её подобию.
 */
object SampleForms {

    private const val KKM_ID = "945583f1-6723-4664-8afa-81e3937b7ceb"
    private const val SHIFT_ID = "e638cdb3-a901-4b6b-ba80-926e220d134c"
    private const val CREATED_AT = 1781774776681L

    /** По этой строке видно, что низ документа не обрезан. */
    const val FISCAL_SIGN: String = "1234567890"

    /** Количество в позиции чека хранится тысячными долями. */
    private const val ONE_PIECE = 1000L

    private val renderer = createReceiptRendererApi(QrCodeDataUriGenerator)

    private val kkm = KkmInfo(
        id = KKM_ID,
        createdAt = CREATED_AT,
        updatedAt = CREATED_AT,
        mode = "PRODUCTION",
        state = "READY",
        registrationNumber = "620300012117",
        factoryNumber = "1365345245",
        branding = ReceiptBranding(),
        ofdServiceInfo = OfdServiceInfo(
            orgTitle = "ИП ИВАНОВ С. П.",
            orgAddress = "Алматы, Абай даңғылы, 10",
            orgAddressKz = "Алматы, Абай даңғылы, 10",
            orgIinOrBin = "920102300400",
            orgOked = "62010",
            geoLatitude = 0,
            geoLongitude = 0,
            geoSource = "GPS"
        )
    )

    private fun document(type: String) = FiscalDocumentSnapshot(
        id = "a8b2e672-4d2d-46ca-87c4-24d8a67f4abd",
        cashboxId = KKM_ID,
        shiftId = SHIFT_ID,
        docType = type,
        docNo = 101L,
        shiftNo = 5L,
        createdAt = CREATED_AT,
        totalAmount = 1080L,
        currency = "KZT",
        fiscalSign = FISCAL_SIGN,
        autonomousSign = null,
        isAutonomous = false,
        ofdStatus = "SENT",
        deliveredAt = null,
        receiptUrl = "https://consumer.oofd.kz/r/101",
        registrationNumber = kkm.registrationNumber,
        taxpayerName = kkm.ofdServiceInfo?.orgTitle,
        taxpayerBin = kkm.ofdServiceInfo?.orgIinOrBin,
        taxpayerAddress = kkm.ofdServiceInfo?.orgAddress,
        factoryNumber = kkm.factoryNumber
    )

    private fun ticket(operation: ReceiptOperationType) = ReceiptRequest(
        kkmId = KKM_ID,
        pin = "7391",
        operation = operation,
        items = listOf(
            ReceiptItem("Хлеб бородинский", "001", ONE_PIECE, Money(180, 0), Money(180, 0)),
            ReceiptItem("Молоко 3.2%", "001", 2 * ONE_PIECE, Money(450, 0), Money(900, 0))
        ),
        payments = listOf(ReceiptPayment(PaymentType.CASH, Money(1080, 0))),
        total = Money(1080, 0),
        taken = Money(1100, 0),
        change = Money(20, 0),
        idempotencyKey = "render-fidelity-${operation.name}"
    )

    /** Чек продажи выбранной разметкой. */
    fun sale(layout: ReceiptLayoutType): String =
        renderer.renderHtml(ticket(ReceiptOperationType.SELL), document("CHECK"), kkm, layout)

    /** Чек возврата продажи. */
    fun refund(layout: ReceiptLayoutType): String =
        renderer.renderHtml(ticket(ReceiptOperationType.SELL_RETURN), document("CHECK"), kkm, layout)

    /** Z-отчёт: самый длинный документ кассы. */
    fun zReport(layout: ReceiptLayoutType): String = renderer.renderCloseShiftHtml(
        shift = ShiftInfo(
            id = SHIFT_ID,
            kkmId = KKM_ID,
            shiftNo = 5L,
            status = ShiftStatus.CLOSED,
            openedAt = CREATED_AT - 28_800_000L,
            closedAt = CREATED_AT
        ),
        counters = mapOf(
            "SELL_COUNT" to 12L,
            "SELL_SUM" to 129_600L,
            "SELL_RETURN_COUNT" to 1L,
            "SELL_RETURN_SUM" to 1_080L,
            "CASH_IN_SUM" to 50_000L,
            "CASH_OUT_SUM" to 20_000L
        ),
        kkm = kkm,
        ofdStatus = "SENT",
        docNo = "102",
        layoutType = layout
    )
}
