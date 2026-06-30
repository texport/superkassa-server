package kz.mybrain.superkassa.core.application.http.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import kz.mybrain.superkassa.core.application.http.annotation.KkmApiResponses
import kz.mybrain.superkassa.core.presentation.model.VatRateResponse
import kz.mybrain.superkassa.core.presentation.facade.SuperkassaApi
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Контроллер справочника ставок НДС.
 */
@RestController
@RequestMapping("/vat-rates")
@Tag(name = "Ставки НДС", description = "Справочник ставок НДС, поддерживаемых ККМ")
class VatRatesController(private val kkmService: SuperkassaApi) {

    @GetMapping
    @Operation(
        summary = "Список ставок НДС",
        description = """
            Возвращает список доступных налоговых групп НДС, поддерживаемых кассовым аппаратом Superkassa.
            
            Используется для:
            - Заполнения поля `vatGroup` в позициях чека при продаже/возврате.
            - Проверки соответствия налогового режима ККМ и допустимых ставок НДС.
            
            Доступные группы НДС:
            - NO_VAT — Без НДС (освобожденный оборот)
            - VAT_0 — НДС 0% (облагаемый оборот со ставкой 0%)
            - VAT_12 — НДС 12% (стандартная ставка облагаемого оборота в РК)
            - VAT_16 — НДС 16% (для особых режимов/переходных периодов)
            
            Каждая запись содержит:
            - `code`: уникальный код группы НДС
            - `percent`: процентная ставка (в целых числах, например, 12)
            - `description`: понятное текстовое описание ставки
            
            Метод является публичным и не требует авторизации.
        """
    )
    @KkmApiResponses(ok = "Список ставок НДС успешно получен")
    fun list(): List<VatRateResponse> {
        return kkmService.listVatRates()
    }
}
