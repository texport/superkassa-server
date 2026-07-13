package kz.mybrain.superkassa.core.http.controllers

import io.github.texport.superkassa.core.domain.api.exception.NotFoundException
import io.github.texport.superkassa.core.presentation.api.SuperkassaApi
import io.github.texport.superkassa.core.presentation.api.model.ofd.NomenclatureItemResponse
import io.github.texport.superkassa.core.presentation.api.model.ofd.NomenclatureLookupRequest
import io.github.texport.superkassa.core.presentation.api.model.ofd.NomenclatureLookupResponse
import io.github.texport.superkassa.core.string.api.CoreStrings
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kz.mybrain.superkassa.core.application.http.controllers.NomenclatureController
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NomenclatureControllerTest {

    private val service = mockk<SuperkassaApi>()
    private val controller = NomenclatureController(service)

    @Test
    fun `lookupNomenclature returns response when found`() {
        val dto = NomenclatureItemResponse(
            id = 639308L,
            barcode = "5449000176431",
            name = "Напиток Piko Pulpy",
            nameKk = "Piko Pulpy сусыны",
            ntin = "0200091550792",
            price = 0.0,
            measureUnitCode = "166",
            vatGroup = "VAT_16"
        )
        val lookupResult = NomenclatureLookupResponse(
            found = true,
            item = dto,
            resultCode = 0,
            resultText = "OK"
        )
        val request = NomenclatureLookupRequest("kkm-1", "5449000176431")
        every { service.lookupNomenclature("1234", request) } returns lookupResult

        val response = controller.lookupNomenclature("kkm-1", "Bearer 1234", "5449000176431")
        assertEquals(lookupResult, response)

        verify(exactly = 1) { service.lookupNomenclature("1234", request) }
    }

    @Test
    fun `lookupNomenclature throws NotFoundException when not found`() {
        val lookupResult = NomenclatureLookupResponse(
            found = false,
            item = null,
            resultCode = 1,
            resultText = "Not Found"
        )
        val request = NomenclatureLookupRequest("kkm-1", "5449000176431")
        every { service.lookupNomenclature("1234", request) } returns lookupResult

        val exception = assertFailsWith<NotFoundException> {
            controller.lookupNomenclature("kkm-1", "Bearer 1234", "5449000176431")
        }
        assertEquals(CoreStrings.nomenclatureNotFound("5449000176431"), exception.trilingualMessage)

        verify(exactly = 1) { service.lookupNomenclature("1234", request) }
    }
}
