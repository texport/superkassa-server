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
        description = "Возвращает список доступных налоговых групп НДС с процентными ставками и описанием."
    )
    @KkmApiResponses(ok = "Список ставок НДС успешно получен")
    fun list(): List<VatRateResponse> {
        return kkmService.listVatRates()
    }
}
