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
}

