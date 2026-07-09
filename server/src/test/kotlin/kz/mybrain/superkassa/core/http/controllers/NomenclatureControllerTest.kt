package kz.mybrain.superkassa.core.http.controllers

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kz.mybrain.superkassa.core.application.http.controllers.NomenclatureController
import kz.mybrain.superkassa.core.domain.exception.NotFoundException
import kz.mybrain.superkassa.core.presentation.facade.SuperkassaApi
import kz.mybrain.superkassa.core.presentation.model.NomenclatureItemDto
import kz.mybrain.superkassa.core.presentation.model.NomenclatureLookupResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NomenclatureControllerTest {

    private val service = mockk<SuperkassaApi>()
    private val controller = NomenclatureController(service)

    @Test
    fun `lookupNomenclature returns response when found`() {
        val dto = NomenclatureItemDto(
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
        every { service.lookupNomenclature("kkm-1", "1234", "5449000176431") } returns lookupResult

        val response = controller.lookupNomenclature("kkm-1", "Bearer 1234", "5449000176431")
        assertEquals(lookupResult, response)

        verify(exactly = 1) { service.lookupNomenclature("kkm-1", "1234", "5449000176431") }
    }

    @Test
    fun `lookupNomenclature throws NotFoundException when not found`() {
        val lookupResult = NomenclatureLookupResponse(
            found = false,
            item = null,
            resultCode = 1,
            resultText = "Not Found"
        )
        every { service.lookupNomenclature("kkm-1", "1234", "5449000176431") } returns lookupResult

        assertFailsWith<NotFoundException> {
            controller.lookupNomenclature("kkm-1", "Bearer 1234", "5449000176431")
        }

        verify(exactly = 1) { service.lookupNomenclature("kkm-1", "1234", "5449000176431") }
    }
}
