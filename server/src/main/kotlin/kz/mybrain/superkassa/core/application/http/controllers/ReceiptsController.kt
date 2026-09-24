package kz.mybrain.superkassa.core.application.http.controllers

import io.github.texport.superkassa.core.presentation.api.SuperkassaApi
import io.github.texport.superkassa.core.presentation.api.model.receipt.ReceiptBuyRequest
import io.github.texport.superkassa.core.presentation.api.model.receipt.ReceiptBuyReturnRequest
import io.github.texport.superkassa.core.presentation.api.model.receipt.ReceiptResponse
import io.github.texport.superkassa.core.presentation.api.model.receipt.ReceiptSellRequest
import io.github.texport.superkassa.core.presentation.api.model.receipt.ReceiptSellReturnRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_RECEIPT_CREATED
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_400_BAD_REQUEST
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_403_FORBIDDEN
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_404_KKM_NOT_FOUND
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_409_SHIFT_NOT_OPEN
import kz.mybrain.superkassa.core.application.http.RECEIPT_BUY_DESCRIPTION
import kz.mybrain.superkassa.core.application.http.RECEIPT_BUY_RETURN_DESCRIPTION
import kz.mybrain.superkassa.core.application.http.RECEIPT_SELL_DESCRIPTION
import kz.mybrain.superkassa.core.application.http.RECEIPT_SELL_RETURN_DESCRIPTION
import kz.mybrain.superkassa.core.application.http.annotation.KkmApiResponses
import kz.mybrain.superkassa.core.application.http.utils.AuthHeaderUtils
import org.springframework.web.bind.annotation.*

/**
 * Контроллер для работы с чеками.
 * Отвечает за создание чеков различных типов операций.
 *
 * Тело запроса — модель чека ядра как есть, вместе с контактом покупателя
 * `customerContact`: по нему ядро ставит доставку чека, а узел досылает её
 * по расписанию.
 */
@RestController
@RequestMapping("/kkm/{kkmId}/receipt")
@Tag(name = "Чеки", description = "Создание чеков различных типов операций")
class ReceiptsController(private val kkmService: SuperkassaApi) {

    /**
     * Создать чек продажи.
     */
    @PostMapping("/sell")
    @Operation(
        summary = "Продажа",
        description = RECEIPT_SELL_DESCRIPTION
    )
    @KkmApiResponses(
        ok = MSG_200_RECEIPT_CREATED,
        badRequest = MSG_400_BAD_REQUEST,
        forbidden = MSG_403_FORBIDDEN,
        conflict = MSG_409_SHIFT_NOT_OPEN,
        notFound = MSG_404_KKM_NOT_FOUND
    )
    fun createSellReceipt(
        @PathVariable kkmId: String,
        @RequestHeader("Authorization") authHeader: String?,
        @RequestBody @Valid request: ReceiptSellRequest
    ): ReceiptResponse {
        val pin = AuthHeaderUtils.extractPin(authHeader)
        return kkmService.createSellReceipt(kkmId, pin, request)
    }

    /**
     * Создать чек возврата продажи.
     */
    @PostMapping("/sell-return")
    @Operation(
        summary = "Возврат продажи",
        description = RECEIPT_SELL_RETURN_DESCRIPTION
    )
    @KkmApiResponses(
        ok = MSG_200_RECEIPT_CREATED,
        badRequest = MSG_400_BAD_REQUEST,
        forbidden = MSG_403_FORBIDDEN,
        conflict = MSG_409_SHIFT_NOT_OPEN,
        notFound = MSG_404_KKM_NOT_FOUND
    )
    fun createSellReturnReceipt(
        @PathVariable kkmId: String,
        @RequestHeader("Authorization") authHeader: String?,
        @RequestBody @Valid request: ReceiptSellReturnRequest
    ): ReceiptResponse {
        val pin = AuthHeaderUtils.extractPin(authHeader)
        return kkmService.createSellReturnReceipt(kkmId, pin, request)
    }

    /**
     * Создать чек покупки.
     */
    @PostMapping("/buy")
    @Operation(
        summary = "Покупка",
        description = RECEIPT_BUY_DESCRIPTION
    )
    @KkmApiResponses(
        ok = MSG_200_RECEIPT_CREATED,
        badRequest = MSG_400_BAD_REQUEST,
        forbidden = MSG_403_FORBIDDEN,
        conflict = MSG_409_SHIFT_NOT_OPEN,
        notFound = MSG_404_KKM_NOT_FOUND
    )
    fun createBuyReceipt(
        @PathVariable kkmId: String,
        @RequestHeader("Authorization") authHeader: String?,
        @RequestBody @Valid request: ReceiptBuyRequest
    ): ReceiptResponse {
        val pin = AuthHeaderUtils.extractPin(authHeader)
        return kkmService.createBuyReceipt(kkmId, pin, request)
    }

    /**
     * Создать чек возврата покупки.
     */
    @PostMapping("/buy-return")
    @Operation(
        summary = "Возврат покупки",
        description = RECEIPT_BUY_RETURN_DESCRIPTION
    )
    @KkmApiResponses(
        ok = MSG_200_RECEIPT_CREATED,
        badRequest = MSG_400_BAD_REQUEST,
        forbidden = MSG_403_FORBIDDEN,
        conflict = MSG_409_SHIFT_NOT_OPEN,
        notFound = MSG_404_KKM_NOT_FOUND
    )
    fun createBuyReturnReceipt(
        @PathVariable kkmId: String,
        @RequestHeader("Authorization") authHeader: String?,
        @RequestBody @Valid request: ReceiptBuyReturnRequest
    ): ReceiptResponse {
        val pin = AuthHeaderUtils.extractPin(authHeader)
        return kkmService.createBuyReturnReceipt(kkmId, pin, request)
    }
}
