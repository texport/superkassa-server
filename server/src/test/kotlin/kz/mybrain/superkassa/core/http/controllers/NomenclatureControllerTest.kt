package kz.mybrain.superkassa.core.http.controllers

import io.github.texport.superkassa.core.domain.api.exception.NotFoundException
import io.github.texport.superkassa.core.domain.api.exception.ValidationException
import io.github.texport.superkassa.core.domain.api.model.common.Decimal
import io.github.texport.superkassa.core.presentation.api.SuperkassaApi
import io.github.texport.superkassa.core.presentation.api.model.ofd.NomenclatureItemResponse
import io.github.texport.superkassa.core.presentation.api.model.ofd.NomenclatureLookupRequest
import io.github.texport.superkassa.core.presentation.api.model.ofd.NomenclatureLookupResponse
import io.github.texport.superkassa.core.string.api.CoreStrings
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kz.mybrain.superkassa.core.application.http.controllers.NomenclatureController
import kz.mybrain.superkassa.core.application.http.utils.NomenclatureUnavailableException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Поиск по штрихкоду: три исхода, а не один.
 *
 * Отсутствие товара, молчащий справочник и заблокированная касса прежде
 * уходили кассе одним и тем же 404, и кассир читал «нет такого штрихкода»
 * там, где товар есть, а спросить о нём нельзя.
 */
class NomenclatureControllerTest {

    private val service = mockk<SuperkassaApi>()
    private val controller = NomenclatureController(service)

    @Test
    fun `lookupNomenclature returns response when found`() {
        val dto = NomenclatureItemResponse(
            id = 639308L,
            barcode = BARCODE,
            name = "Напиток Piko Pulpy",
            nameKk = "Piko Pulpy сусыны",
            ntin = "0200091550792",
            price = Decimal.parse("0.0"),
            measureUnitCode = "166",
            vatGroup = "VAT_16"
        )
        val lookupResult = NomenclatureLookupResponse(found = true, item = dto, resultCode = 0, resultText = "OK")
        every { service.lookupNomenclature(PIN, request()) } returns lookupResult

        val response = controller.lookupNomenclature(KKM, "Bearer $PIN", BARCODE)
        assertEquals(lookupResult, response)

        verify(exactly = 1) { service.lookupNomenclature(PIN, request()) }
    }

    /** Каталог ответил, и позиции в нём нет: это и есть «нет такого штрихкода». */
    @Test
    fun `lookupNomenclature throws NotFoundException when catalogue has no such item`() {
        val lookupResult = NomenclatureLookupResponse(
            found = false,
            item = null,
            resultCode = 0,
            resultText = "No items found in nomenclature response"
        )
        every { service.lookupNomenclature(PIN, request()) } returns lookupResult

        val exception = assertFailsWith<NotFoundException> {
            controller.lookupNomenclature(KKM, "Bearer $PIN", BARCODE)
        }
        assertEquals(CoreStrings.nomenclatureNotFound(BARCODE), exception.trilingualMessage)
        assertEquals("NOMENCLATURE_NOT_FOUND", exception.code)
    }

    /** Спросить не удалось: ненулевой код результата каталогом не отвечен. */
    @Test
    fun `lookupNomenclature tells catalogue silence apart from missing item`() {
        val lookupResult = NomenclatureLookupResponse(
            found = false,
            item = null,
            resultCode = 254,
            resultText = "ServiceTemporarilyUnavailable"
        )
        every { service.lookupNomenclature(PIN, request()) } returns lookupResult

        val exception = assertFailsWith<NomenclatureUnavailableException> {
            controller.lookupNomenclature(KKM, "Bearer $PIN", BARCODE)
        }
        assertEquals("NOMENCLATURE_UNAVAILABLE", exception.code)
        assertEquals(SERVICE_UNAVAILABLE, exception.status)
    }

    /** Заблокированная касса называется блокировкой, а не отсутствием товара. */
    @Test
    fun `lookupNomenclature refuses on blocked kkm with its block reason`() {
        every { service.lookupNomenclature(PIN, request()) } throws
            ValidationException(CoreStrings.kkmBlocked(INVALID_TOKEN), "KKM_BLOCKED")

        val exception = assertFailsWith<ValidationException> {
            controller.lookupNomenclature(KKM, "Bearer $PIN", BARCODE)
        }
        assertEquals("KKM_BLOCKED", exception.code)
        assertEquals(CoreStrings.kkmBlocked(INVALID_TOKEN), exception.trilingualMessage)
    }

    private fun request() = NomenclatureLookupRequest(KKM, BARCODE)
}

private const val KKM = "kkm-1"
private const val PIN = "1234"
private const val BARCODE = "5449000176431"

/** Отказ БФД «неверный токен», перенесённый узлом в свой диапазон. */
private const val INVALID_TOKEN = 1002
private const val SERVICE_UNAVAILABLE = 503
