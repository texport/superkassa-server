package kz.mybrain.superkassa.core.application.measurement

import io.github.texport.superkassa.core.domain.api.exception.NotFoundException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UnitsApplicationServiceTest {

    private val service = UnitsApplicationService()

    @Test
    fun `list returns all units by default`() {
        val result = service.list(limit = 50, offset = 0, search = null)
        assertTrue(result.items.isNotEmpty())
        assertEquals(50, result.limit)
        assertEquals(0, result.offset)
    }

    @Test
    fun `list coerces limit and offset`() {
        val result = service.list(limit = -10, offset = -5, search = null)
        assertEquals(1, result.limit)
        assertEquals(0, result.offset)
    }

    @Test
    fun `list filters by code or name`() {
        val pieceResult = service.list(limit = 10, offset = 0, search = "796")
        assertEquals(1, pieceResult.items.size)
        assertEquals("796", pieceResult.items.first().code)

        val nameResult = service.list(limit = 10, offset = 0, search = "штука")
        assertTrue(nameResult.items.isNotEmpty())
        assertTrue(nameResult.items.any { it.code == "796" })
    }

    @Test
    fun `getByCode returns unit or throws NotFoundException`() {
        val piece = service.getByCode("796")
        assertEquals("796", piece.code)
        assertEquals("шт", piece.nameShort)

        assertFailsWith<NotFoundException> {
            service.getByCode("unknown-code")
        }
    }
}
