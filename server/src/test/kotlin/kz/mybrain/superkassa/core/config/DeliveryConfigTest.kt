package kz.mybrain.superkassa.core.config

import com.sun.net.httpserver.HttpServer
import io.github.texport.superkassa.core.domain.api.model.delivery.DeliveryRequest
import io.github.texport.superkassa.core.domain.api.model.settings.CoreMode
import io.github.texport.superkassa.core.domain.api.model.settings.CoreSettings
import io.github.texport.superkassa.core.domain.api.model.settings.DeliveryChannelSettings
import io.github.texport.superkassa.core.domain.api.model.settings.DeliverySettings
import io.github.texport.superkassa.core.domain.api.model.settings.EmailProviderSettings
import io.github.texport.superkassa.core.domain.api.model.settings.PrintConnectionSettings
import io.github.texport.superkassa.core.domain.api.model.settings.PrintDeliverySettings
import io.github.texport.superkassa.core.domain.api.model.settings.SmsProviderSettings
import io.github.texport.superkassa.core.domain.api.model.settings.StorageSettings
import io.github.texport.superkassa.core.domain.api.model.settings.TelegramProviderSettings
import io.github.texport.superkassa.core.domain.api.model.settings.WhatsAppProviderSettings
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Доставка узла собрана из каналов ядра по настройкам владельца.
 *
 * Доставлено — только то, что ушло: канал без настроек, выключенный
 * или не известный узлу отвечает отказом. Прежде на месте ненастроенного
 * канала стояла заглушка с успехом: чек не уходил, а касса записывала
 * его доставленным.
 */
class DeliveryConfigTest {

    private val config = DeliveryConfig()
    private val smsCalls = CopyOnWriteArrayList<Pair<String, String?>>()
    private val smsGateway = HttpServer.create(InetSocketAddress(LOOPBACK, 0), 0).apply {
        createContext("/send") { exchange ->
            smsCalls += exchange.requestURI.query to exchange.requestHeaders.getFirst("Authorization")
            exchange.sendResponseHeaders(HTTP_OK, -1)
            exchange.close()
        }
        start()
    }
    private val sms = SmsProviderSettings(
        providerUrl = "http://$LOOPBACK:${smsGateway.address.port}/send?phone={phone}&text={text}",
        apiKey = SMS_KEY
    )

    @AfterTest
    fun stop() = smsGateway.stop(0)

    @Test
    fun `delivery refuses when no channel is configured`() {
        val port = config.deliveryPort(settings(emptyList()))

        assertFalse(port.deliver(request("PRINT")))
    }

    @Test
    fun `every chosen channel without settings refuses, and an unknown one is skipped`() {
        val port = config.deliveryPort(settings(listOf("print", "EMAIL", "SMS", "TELEGRAM", "WHATSAPP", "FAX")))

        CHANNELS.forEach { assertFalse(port.deliver(request(it)), it) }
    }

    @Test
    fun `enabled channels with empty provider settings refuse`() {
        val port = config.deliveryPort(settings(listOf("SMS"), DeliverySettings(channels = CHANNELS.map { enabled(it) })))

        CHANNELS.forEach { assertFalse(port.deliver(request(it)), it) }
    }

    @Test
    fun `delivery refuses when every channel is disabled`() {
        val delivery = DeliverySettings(channels = listOf(enabled("SMS").copy(enabled = false)), sms = sms)
        val port = config.deliveryPort(settings(listOf("SMS"), delivery))

        assertFalse(port.deliver(request("SMS")))
        assertTrue(smsCalls.isEmpty())
    }

    @Test
    fun `sms goes through the core channel to the gateway from the settings`() {
        val port = config.deliveryPort(settings(emptyList(), configured()))

        assertTrue(port.deliver(request("SMS")))
        val (query, authorization) = smsCalls.single()
        assertTrue(query.startsWith("phone=+77770001122&text="), query)
        assertEquals("Bearer $SMS_KEY", authorization)
    }

    @Test
    fun `receipt goes to the network printer as bytes`() {
        ServerSocket(0).use { printer ->
            val received = CopyOnWriteArrayList<ByteArray>()
            val reader = thread { printer.accept().use { received += it.getInputStream().readAllBytes() } }
            val connection = PrintConnectionSettings(host = LOOPBACK, port = printer.localPort)

            assertTrue(config.deliveryPort(settings(emptyList(), configured(connection))).deliver(request("PRINT")))
            reader.join()
            assertEquals(listOf(1, 2, 3), received.single().map { it.toInt() })
        }
    }

    @Test
    fun `printer without an address or a port is not assembled`() {
        listOf(PrintConnectionSettings(host = null, port = PRINTER_PORT), PrintConnectionSettings(host = LOOPBACK, port = null)).forEach {
            val port = config.deliveryPort(settings(emptyList(), configured(it)))

            assertFalse(port.deliver(request("PRINT")))
        }
    }

    @Test
    fun `email to an unreachable mail server is refused`() {
        val port = config.deliveryPort(settings(emptyList(), configured()))

        assertFalse(port.deliver(request("EMAIL")))
    }

    private fun settings(channels: List<String>, delivery: DeliverySettings? = null) = CoreSettings(
        mode = CoreMode.DESKTOP,
        storage = StorageSettings(engine = "SQLITE", jdbcUrl = "jdbc:sqlite:data/core.db"),
        deliveryChannels = channels,
        delivery = delivery
    )

    private fun configured(printer: PrintConnectionSettings? = null) = DeliverySettings(
        channels = CHANNELS.map { enabled(it) },
        print = PrintDeliverySettings(enabled = true, paperWidthMm = PAPER_MM, connection = printer),
        email = EmailProviderSettings(LOOPBACK, closedPort(), null, null, "kassa@example.kz"),
        sms = sms,
        telegram = TelegramProviderSettings("bot-token"),
        whatsapp = WhatsAppProviderSettings("access-token", "100200300")
    )

    private fun enabled(channel: String) = DeliveryChannelSettings(
        channel = channel,
        enabled = true,
        payloadType = "LINK",
        documentFormat = "HTML",
        destination = null
    )

    private fun request(channel: String) = DeliveryRequest(
        kkmId = "kkm-1",
        documentId = "doc-1",
        channel = channel,
        destination = "+77770001122",
        payloadType = "LINK",
        payloadUrl = "https://example.kz/t/1",
        payloadBytes = byteArrayOf(1, 2, 3)
    )

    private fun closedPort(): Int = ServerSocket(0).use { it.localPort }

    private companion object {
        val CHANNELS = listOf("PRINT", "EMAIL", "SMS", "TELEGRAM", "WHATSAPP")
        const val LOOPBACK = "127.0.0.1"
        const val SMS_KEY = "sms-gateway-key"
        const val HTTP_OK = 200
        const val PAPER_MM = 80
        const val PRINTER_PORT = 9100
    }
}
