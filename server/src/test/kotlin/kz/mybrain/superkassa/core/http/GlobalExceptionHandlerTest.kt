package kz.mybrain.superkassa.core.http

import io.mockk.mockk
import kz.mybrain.superkassa.core.application.http.exception.GlobalExceptionHandler
import org.springframework.core.MethodParameter
import org.springframework.http.HttpInputMessage
import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import kotlin.test.Test
import kotlin.test.assertEquals

class GlobalExceptionHandlerTest {

    @Test
    fun `illegal argument exception is returned as bad request`() {
        val handler = GlobalExceptionHandler()

        val response = handler.handleIllegalArgumentException(
            IllegalArgumentException("Invalid payload")
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("INVALID_ARGUMENT", response.body?.code)
        assertEquals("Invalid payload", response.body?.message)
    }

    @Test
    fun `no resource found exception is returned as not found`() {
        val handler = GlobalExceptionHandler()

        val response = handler.handleNoResourceFoundException(
            org.springframework.web.servlet.resource.NoResourceFoundException(
                org.springframework.http.HttpMethod.GET,
                "/favicon.ico",
                "static/favicon.ico"
            )
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals("RESOURCE_NOT_FOUND", response.body?.code)
        assertEquals(true, response.body?.message?.contains("Запрошенный ресурс не найден"))
    }

    @Test
    fun `http message not readable exception is returned as invalid json`() {
        val handler = GlobalExceptionHandler()
        val ex = HttpMessageNotReadableException("Malformed JSON", mockk<HttpInputMessage>())

        val response = handler.handleJsonException(ex)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("INVALID_JSON", response.body?.code)
        assertEquals(true, response.body?.message?.contains("Некорректный запрос JSON"))
    }

    @Test
    fun `method argument type mismatch exception is returned as invalid param`() {
        val handler = GlobalExceptionHandler()
        val ex = MethodArgumentTypeMismatchException(
            "abc",
            Int::class.java,
            "id",
            mockk<MethodParameter>(),
            IllegalArgumentException()
        )

        val response = handler.handleTypeMismatchException(ex)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("INVALID_PARAM", response.body?.code)
        assertEquals(true, response.body?.message?.contains("Параметр 'id' имеет неверный тип"))
    }

    @Test
    fun `method argument not valid exception is returned as validation error`() {
        val handler = GlobalExceptionHandler()
        val bindingResult = org.springframework.validation.MapBindingResult(mapOf<Any, Any>(), "target")
        bindingResult.addError(org.springframework.validation.FieldError("target", "email", "must be valid"))
        val ex = MethodArgumentNotValidException(mockk<MethodParameter>(), bindingResult)

        val response = handler.handleSpringValidationException(ex)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("VALIDATION_ERROR", response.body?.code)
        assertEquals("email: must be valid", response.body?.message)
    }
}
