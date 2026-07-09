package kz.mybrain.superkassa.core.application.http.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_400_BAD_REQUEST
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_403_FORBIDDEN
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_404_KKM_NOT_FOUND
import kz.mybrain.superkassa.core.application.http.annotation.KkmApiResponses
import kz.mybrain.superkassa.core.application.http.utils.AuthHeaderUtils
import kz.mybrain.superkassa.core.domain.exception.ErrorMessages
import kz.mybrain.superkassa.core.domain.exception.NotFoundException
import kz.mybrain.superkassa.core.presentation.facade.SuperkassaApi
import kz.mybrain.superkassa.core.presentation.model.NomenclatureLookupResponse
import org.springframework.web.bind.annotation.*

/**
 * Контроллер для работы с номенклатурой ККМ.
 */
@RestController
@RequestMapping("/kkm/{kkmId}/nomenclature")
@Tag(name = "Номенклатура", description = "Управление номенклатурными позициями и поиск товаров")
class NomenclatureController(private val kkmService: SuperkassaApi) {

    /**
     * Поиск номенклатурной позиции по штрихкоду напрямую в ОФД (Национальный каталог товаров).
     */
    @GetMapping("/lookup")
    @Operation(
        summary = "Поиск по штрихкоду в НКТ",
        description = """
            Выполняет запрос информации о товаре из Национального каталога товаров (НКТ) через ОФД по его штрихкоду.
            
            Требования:
            - ККМ должна быть зарегистрирована и находиться в состоянии ACTIVE
            - ПИН-код должен быть передан в заголовке Authorization (Bearer <pin> или просто <pin>)
            - ПИН-код должен соответствовать пользователю с правами CASHIER или ADMIN
            
            Параметры:
            - kkmId (в пути): Идентификатор ККМ
            - barcode (в запросе): Штрихкод товара (например, 5449000176431)
            - Authorization (в заголовке): ПИН-код пользователя в формате "Bearer <pin>" или просто "<pin>"
            
            Что возвращается:
            - Сведения о найденной номенклатурной позиции (ID, штрихкод, наименование на русском и казахском, NTIN, цена, группа НДС).
            - В случае отсутствия товара в НКТ возвращается статус 404 Not Found.
        """
    )
    @KkmApiResponses(
        ok = "Сведения о номенклатурной позиции успешно получены",
        badRequest = MSG_400_BAD_REQUEST,
        forbidden = MSG_403_FORBIDDEN,
        notFound = MSG_404_KKM_NOT_FOUND
    )
    fun lookupNomenclature(
        @PathVariable kkmId: String,
        @RequestHeader("Authorization") authHeader: String?,
        @RequestParam barcode: String
    ): NomenclatureLookupResponse {
        val pin = AuthHeaderUtils.extractPin(authHeader)
        val response = kkmService.lookupNomenclature(kkmId, pin, barcode)
        if (!response.found) {
            throw NotFoundException(ErrorMessages.nomenclatureNotFound(barcode))
        }
        return response
    }
}
