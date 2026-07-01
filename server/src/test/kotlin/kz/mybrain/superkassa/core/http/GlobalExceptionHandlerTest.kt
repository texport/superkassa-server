package kz.mybrain.superkassa.core.http

import kz.mybrain.superkassa.core.application.http.exception.GlobalExceptionHandler
import org.springframework.http.HttpStatus
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
}
