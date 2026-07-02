package kz.mybrain.superkassa.core.application.http.utils

import kz.mybrain.superkassa.core.domain.exception.ForbiddenException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AuthHeaderUtilsTest {

    @Test
    fun `extractPin extracts pin from valid Bearer format`() {
        val pin = AuthHeaderUtils.extractPin("Bearer 1234")
        assertEquals("1234", pin)
    }

    @Test
    fun `extractPin extracts pin from valid raw format`() {
        val pin = AuthHeaderUtils.extractPin("1234")
        assertEquals("1234", pin)
    }

    @Test
    fun `extractPin extracts pin from case insensitive Bearer prefix`() {
        val pin = AuthHeaderUtils.extractPin("bearer 1234")
        assertEquals("1234", pin)
    }

    @Test
    fun `extractPin handles extra whitespaces`() {
        val pin = AuthHeaderUtils.extractPin("  Bearer   1234  ")
        assertEquals("1234", pin)
    }

    @Test
    fun `extractPin throws ForbiddenException on null header`() {
        assertFailsWith<ForbiddenException> {
            AuthHeaderUtils.extractPin(null)
        }
    }

    @Test
    fun `extractPin throws ForbiddenException on empty header`() {
        assertFailsWith<ForbiddenException> {
            AuthHeaderUtils.extractPin("")
        }
    }

    @Test
    fun `extractPin throws ForbiddenException on blank header`() {
        assertFailsWith<ForbiddenException> {
            AuthHeaderUtils.extractPin("   ")
        }
    }

    @Test
    fun `extractPin throws ForbiddenException on Bearer prefix without pin`() {
        assertFailsWith<ForbiddenException> {
            AuthHeaderUtils.extractPin("Bearer")
        }
        assertFailsWith<ForbiddenException> {
            AuthHeaderUtils.extractPin("Bearer ")
        }
        assertFailsWith<ForbiddenException> {
            AuthHeaderUtils.extractPin("Bearer    ")
        }
    }

    @Test
    fun `extractPin accepts raw values containing special characters`() {
        val pin = AuthHeaderUtils.extractPin("abc-def_123")
        assertEquals("abc-def_123", pin)
    }
}
