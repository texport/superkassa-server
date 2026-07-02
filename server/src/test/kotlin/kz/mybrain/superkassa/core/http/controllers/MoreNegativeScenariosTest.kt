package kz.mybrain.superkassa.core.http.controllers

import kz.mybrain.superkassa.core.application.http.exception.GlobalExceptionHandler
import kz.mybrain.superkassa.core.domain.exception.SuperkassaException
import kz.mybrain.superkassa.core.domain.exception.TrilingualMessage
import org.springframework.http.HttpStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MoreNegativeScenariosTest {

    private val handler = GlobalExceptionHandler()

    // Helper class to instantiate custom SuperkassaException
    class CustomSuperkassaException(
        code: String,
        status: Int,
        message: String? = null
    ) : SuperkassaException(
        code,
        status,
        TrilingualMessage(message ?: "error", message ?: "error", message ?: "error")
    )

    // --- 1-10. SuperkassaException Translation Tests ---

    @Test
    fun `handleSuperkassaException maps 404 NOT_FOUND`() {
        val ex = CustomSuperkassaException("KKM_NOT_FOUND", 404, "KKM not found")
        val response = handler.handleSuperkassaException(ex)
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals("KKM_NOT_FOUND", response.body?.code)
        assertTrue(response.body?.message!!.contains("KKM not found"))
    }

    @Test
    fun `handleSuperkassaException maps 409 CONFLICT`() {
        val ex = CustomSuperkassaException("SHIFT_ALREADY_OPEN", 409, "Shift already open")
        val response = handler.handleSuperkassaException(ex)
        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals("SHIFT_ALREADY_OPEN", response.body?.code)
        assertTrue(response.body?.message!!.contains("Shift already open"))
    }

    @Test
    fun `handleSuperkassaException maps 403 FORBIDDEN`() {
        val ex = CustomSuperkassaException("PIN_INVALID", 403, "Invalid pin")
        val response = handler.handleSuperkassaException(ex)
        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals("PIN_INVALID", response.body?.code)
        assertTrue(response.body?.message!!.contains("Invalid pin"))
    }

    @Test
    fun `handleSuperkassaException maps 400 BAD_REQUEST`() {
        val ex = CustomSuperkassaException("BAD_PAYLOAD", 400, "Bad payload")
        val response = handler.handleSuperkassaException(ex)
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("BAD_PAYLOAD", response.body?.code)
    }

    @Test
    fun `handleSuperkassaException maps 401 UNAUTHORIZED`() {
        val ex = CustomSuperkassaException("UNAUTHORIZED", 401, "Not authorized")
        val response = handler.handleSuperkassaException(ex)
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals("UNAUTHORIZED", response.body?.code)
    }

    @Test
    fun `handleSuperkassaException maps 422 UNPROCESSABLE_ENTITY`() {
        val ex = CustomSuperkassaException("FISCAL_ERROR", 422, "Fiscal error")
        val response = handler.handleSuperkassaException(ex)
        assertEquals(422, response.statusCode.value())
        assertEquals("FISCAL_ERROR", response.body?.code)
    }

    @Test
    fun `handleSuperkassaException maps 503 SERVICE_UNAVAILABLE`() {
        val ex = CustomSuperkassaException("OFD_DOWN", 503, "OFD is down")
        val response = handler.handleSuperkassaException(ex)
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.statusCode)
        assertEquals("OFD_DOWN", response.body?.code)
    }

    @Test
    fun `handleSuperkassaException maps 500 INTERNAL_SERVER_ERROR`() {
        val ex = CustomSuperkassaException("FATAL", 500, "Fatal error")
        val response = handler.handleSuperkassaException(ex)
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("FATAL", response.body?.code)
    }

    @Test
    fun `handleSuperkassaException falls back to 500 on invalid status code`() {
        val ex = CustomSuperkassaException("UNKNOWN_STATUS", 999, "Unknown status")
        val response = handler.handleSuperkassaException(ex)
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("UNKNOWN_STATUS", response.body?.code)
    }

    @Test
    fun `handleSuperkassaException provides fallback message when message is null`() {
        val ex = CustomSuperkassaException("FATAL", 500, null)
        val response = handler.handleSuperkassaException(ex)
        assertTrue(response.body?.message!!.contains("error"))
    }

    // --- 11-15. Exception (General) Translation Tests ---

    @Test
    fun `handleGenericException maps generic NullPointerException to 500`() {
        val response = handler.handleGenericException(NullPointerException("NPE"))
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("INTERNAL_ERROR", response.body?.code)
        assertTrue(response.body?.message!!.contains("Внутренняя ошибка сервера"))
    }

    @Test
    fun `handleGenericException maps generic RuntimeException to 500`() {
        val response = handler.handleGenericException(RuntimeException("Unknown runtime error"))
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("INTERNAL_ERROR", response.body?.code)
    }

    @Test
    fun `handleGenericException maps generic IllegalStateException to 500`() {
        val response = handler.handleGenericException(IllegalStateException("Invalid state"))
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("INTERNAL_ERROR", response.body?.code)
    }

    @Test
    fun `handleGenericException maps generic IndexOutOfBoundsException to 500`() {
        val response = handler.handleGenericException(IndexOutOfBoundsException("OOB"))
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("INTERNAL_ERROR", response.body?.code)
    }

    @Test
    fun `handleGenericException maps generic Exception to 500`() {
        val response = handler.handleGenericException(Exception("Generic"))
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("INTERNAL_ERROR", response.body?.code)
    }

    // --- 16-20. IllegalArgumentException boundary cases ---

    @Test
    fun `handleIllegalArgumentException maps with custom message`() {
        val response = handler.handleIllegalArgumentException(IllegalArgumentException("Param is missing"))
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("INVALID_ARGUMENT", response.body?.code)
        assertEquals("Param is missing", response.body?.message)
    }

    @Test
    fun `handleIllegalArgumentException uses default message if message is null`() {
        val response = handler.handleIllegalArgumentException(IllegalArgumentException(null as String?))
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("INVALID_ARGUMENT", response.body?.code)
        assertTrue(response.body?.message!!.contains("Некорректный аргумент"))
    }

    @Test
    fun `handleIllegalArgumentException handles empty message`() {
        val response = handler.handleIllegalArgumentException(IllegalArgumentException(""))
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("INVALID_ARGUMENT", response.body?.code)
        assertEquals("", response.body?.message)
    }

    @Test
    fun `handleIllegalArgumentException handles blank message`() {
        val response = handler.handleIllegalArgumentException(IllegalArgumentException("   "))
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("INVALID_ARGUMENT", response.body?.code)
        assertEquals("   ", response.body?.message)
    }

    @Test
    fun `handleIllegalArgumentException is not mapped to internal error`() {
        val response = handler.handleIllegalArgumentException(IllegalArgumentException("Test"))
        assertTrue(response.statusCode.is4xxClientError)
    }
}
