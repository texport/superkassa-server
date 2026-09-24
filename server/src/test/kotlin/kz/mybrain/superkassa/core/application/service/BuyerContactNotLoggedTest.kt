package kz.mybrain.superkassa.core.application.service

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.github.texport.superkassa.core.presentation.api.model.receipt.CustomerContactRequest
import io.github.texport.superkassa.delivery.api.model.DeliveryResult
import org.slf4j.LoggerFactory
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Телефон и почта покупателя уходят в БФД вместе с чеком и в каналы
 * доставки, но не в журнал узла — ни на подробном уровне, ни в отказе
 * канала, ни в причине сбоя, где канал назвал адрес.
 */
class BuyerContactNotLoggedTest {
    private val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
    private val previousLevel = root.level
    private val journal = ListAppender<ILoggingEvent>().apply { start() }

    init {
        root.level = Level.DEBUG
        root.addAppender(journal)
    }

    @AfterTest
    fun restore() {
        root.detachAppender(journal)
        root.level = previousLevel
    }

    @Test
    fun `чек с контактом, его доставка и отказ канала не пишут контакт в журнал`() {
        val node = NodeKassa(Files.createTempDirectory("node-journal"))
        val kkmId = node.registerAndOpenShift()

        node.sell(kkmId, BUYER, key = "sale-1")
        node.worker.sendDueDeliveries()
        node.sms.answer = DeliveryResult(ok = false, message = "gateway refused ${BUYER.phone}", code = "DELIVERY_SMS_REFUSED")
        node.sell(kkmId, BUYER, key = "sale-2")
        node.worker.sendDueDeliveries()
        node.sms.failure = IllegalStateException("socket closed for ${BUYER.phone}")
        node.sell(kkmId, BUYER, key = "sale-3")
        node.worker.sendDueDeliveries()

        val leaked = journal.list.filter { event -> SECRETS.any { it in event.formattedMessage || it in thrown(event) } }
        assertTrue(journal.list.isNotEmpty(), "nothing was logged: the check sees nothing")
        assertTrue(leaked.isEmpty(), leaked.joinToString("\n") { "${it.loggerName}: ${it.formattedMessage}" })
    }

    private fun thrown(event: ILoggingEvent): String =
        generateSequence(event.throwableProxy) { it.cause }.joinToString(" ") { it.message.orEmpty() }

    private companion object {
        val BUYER = CustomerContactRequest(phone = "+77017654321", email = "buyer@example.kz")
        val SECRETS = listOf("77017654321", "buyer@example.kz")
    }
}
