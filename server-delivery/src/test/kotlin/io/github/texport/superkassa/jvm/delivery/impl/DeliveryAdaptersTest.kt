package io.github.texport.superkassa.jvm.delivery.impl

import io.github.texport.superkassa.delivery.domain.model.DeliveryChannel
import io.github.texport.superkassa.delivery.domain.model.DeliveryRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Optional
import javax.net.ssl.SSLSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeliveryAdaptersTest {
    private class ExposedHttpDeliveryAdapter : BaseHttpDeliveryAdapter() {
        override val logger: Logger = LoggerFactory.getLogger(ExposedHttpDeliveryAdapter::class.java)

        fun handle(response: HttpResponse<String>) = handleHttpResponse(
            response = response,
            documentId = "document-1",
            destination = "+7 (777) 000-11-22",
            channelName = "sms"
        )

        fun exceptionResult(exception: Exception) = handleException(
            e = exception,
            documentId = "document-1",
            channelName = "sms"
        )

        fun normalized(phone: String): String = normalizePhoneNumber(phone)

        fun json(value: String): String = value.toJsonString()
    }

    private data class StringHttpResponse(
        private val statusCode: Int,
        private val body: String
    ) : HttpResponse<String> {
        override fun statusCode(): Int = statusCode

        override fun body(): String = body

        override fun request(): HttpRequest? = null

        override fun previousResponse(): Optional<HttpResponse<String>> = Optional.empty()

        override fun headers(): HttpHeaders = HttpHeaders.of(emptyMap()) { _, _ -> true }

        override fun sslSession(): Optional<SSLSession> = Optional.empty()

        override fun uri(): URI = URI.create("https://delivery.test/send")

        override fun version(): HttpClient.Version = HttpClient.Version.HTTP_1_1
    }

    @Test
    fun `jps printer returns error when payload is missing`() {
        val adapter = JpsPrintDeliveryAdapter("NonExistentPrinter")
        val request = DeliveryRequest(
            cashboxId = "cashbox-1",
            documentId = "DOC-123",
            channel = DeliveryChannel.PRINT,
            destination = "local",
            payloadBytes = null,
            payloadUrl = "https://example.com"
        )
        val result = adapter.send(request)
        assertFalse(result.ok)
        val msg = result.message ?: ""
        assertTrue(
            msg.contains("No print payload") || msg.contains("Отсутствуют данные для печати") || msg.contains("Басып шығаруға арналған деректер жоқ")
        )
    }

    @Test
    fun `jps printer returns printer not found error for invalid printer name`() {
        val adapter = JpsPrintDeliveryAdapter("NonExistentPrinter-XYZ-12345")
        val request = DeliveryRequest(
            cashboxId = "cashbox-1",
            documentId = "DOC-123",
            channel = DeliveryChannel.PRINT,
            destination = "local",
            payloadBytes = byteArrayOf(1, 2, 3),
            payloadUrl = null
        )
        val result = adapter.send(request)
        assertFalse(result.ok)
        val msg = result.message ?: ""
        assertTrue(
            msg.contains("not found in the OS") || msg.contains("не найден в операционной системе") || msg.contains("табылмады")
        )
    }

    @Test
    fun `base http delivery treats 2xx as success`() {
        val adapter = ExposedHttpDeliveryAdapter()

        val result = adapter.handle(StringHttpResponse(204, ""))

        assertTrue(result.ok)
        assertEquals(null, result.message)
    }

    @Test
    fun `base http delivery returns diagnostic message for non 2xx`() {
        val adapter = ExposedHttpDeliveryAdapter()

        val result = adapter.handle(StringHttpResponse(429, "rate limit"))

        assertFalse(result.ok)
        val message = result.message ?: ""
        assertTrue(message.contains("status 429"))
        assertTrue(message.contains("rate limit"))
        assertTrue(message.contains("Доставка через sms"))
    }

    @Test
    fun `base http delivery wraps thrown exception as failed delivery`() {
        val adapter = ExposedHttpDeliveryAdapter()

        val result = adapter.exceptionResult(IllegalStateException("connection refused"))

        assertFalse(result.ok)
        val message = result.message ?: ""
        assertTrue(message.contains("connection refused"))
        assertTrue(message.contains("Ошибка доставки через sms"))
    }

    @Test
    fun `base http delivery normalizes destination phone and escapes json payload`() {
        val adapter = ExposedHttpDeliveryAdapter()

        assertEquals("77770001122", adapter.normalized("+7 (777) 000-11-22"))
        assertEquals("\"line\\n\\\"quoted\\\"\\\\path\"", adapter.json("line\n\"quoted\"\\path"))
    }

    @Test
    fun `sms delivery fails when destination is missing`() {
        val adapter = SmsDeliveryAdapter(providerUrl = "https://sms.test/send", apiKey = null)

        val result = adapter.send(sampleRequest(DeliveryChannel.SMS, destination = null))

        assertFalse(result.ok)
        assertTrue((result.message ?: "").contains("destination"))
    }

    @Test
    fun `sms delivery fails when provider url is not configured`() {
        val adapter = SmsDeliveryAdapter(providerUrl = null, apiKey = null)

        val result = adapter.send(sampleRequest(DeliveryChannel.SMS))

        assertFalse(result.ok)
        assertTrue((result.message ?: "").contains("provider URL"))
    }

    @Test
    fun `sms delivery wraps invalid provider url as delivery failure`() {
        val adapter = SmsDeliveryAdapter(providerUrl = "://bad-url/{phone}/{text}", apiKey = null)

        val result = adapter.send(sampleRequest(DeliveryChannel.SMS))

        assertFalse(result.ok)
        assertTrue((result.message ?: "").contains("Delivery via SMS failed"))
    }

    @Test
    fun `whatsapp delivery fails when destination is missing`() {
        val adapter = WhatsAppDeliveryAdapter(accessToken = "token", phoneNumberId = "phone-id")

        val result = adapter.send(sampleRequest(DeliveryChannel.WHATSAPP, destination = null))

        assertFalse(result.ok)
        assertTrue((result.message ?: "").contains("WhatsApp phone number required"))
    }

    @Test
    fun `telegram delivery fails when chat id is missing`() {
        val adapter = TelegramDeliveryAdapter(botToken = "token")

        val result = adapter.send(sampleRequest(DeliveryChannel.TELEGRAM, destination = null))

        assertFalse(result.ok)
        assertTrue((result.message ?: "").contains("Telegram chat_id required"))
    }

    @Test
    fun `telegram delivery wraps invalid bot token uri as delivery failure`() {
        val adapter = TelegramDeliveryAdapter(botToken = "bad token with spaces")

        val result = adapter.send(sampleRequest(DeliveryChannel.TELEGRAM, destination = "123"))

        assertFalse(result.ok)
        assertTrue((result.message ?: "").contains("Delivery via Telegram failed"))
    }

    @Test
    fun `email delivery fails when destination is missing`() {
        val adapter = EmailDeliveryAdapter(
            host = "localhost",
            port = 2525,
            user = null,
            password = null,
            from = "noreply@example.com"
        )

        val result = adapter.send(sampleRequest(DeliveryChannel.EMAIL, destination = null))

        assertFalse(result.ok)
        assertTrue((result.message ?: "").contains("Email destination required"))
    }

    private fun sampleRequest(
        channel: DeliveryChannel,
        destination: String? = "+7 (777) 000-11-22",
        payloadUrl: String? = "https://receipt.test/doc-1",
        payloadBytes: ByteArray? = byteArrayOf(1, 2, 3)
    ) = DeliveryRequest(
        cashboxId = "cashbox-1",
        documentId = "DOC-123",
        channel = channel,
        destination = destination,
        payloadBytes = payloadBytes,
        payloadUrl = payloadUrl
    )
}
