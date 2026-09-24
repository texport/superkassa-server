package kz.mybrain.superkassa.core.config

import io.github.texport.superkassa.core.domain.api.model.settings.CoreSettings
import io.github.texport.superkassa.core.domain.api.model.settings.DeliverySettings
import io.github.texport.superkassa.core.domain.api.model.settings.EmailProviderSettings
import io.github.texport.superkassa.core.domain.api.model.settings.PrintDeliverySettings
import io.github.texport.superkassa.core.domain.api.port.integration.DeliveryPort
import io.github.texport.superkassa.delivery.api.createDeliveryServiceApi
import io.github.texport.superkassa.delivery.api.model.DeliveryChannel
import io.github.texport.superkassa.delivery.channels.api.emailChannel
import io.github.texport.superkassa.delivery.channels.api.model.SmtpServer
import io.github.texport.superkassa.delivery.channels.api.smsChannel
import io.github.texport.superkassa.delivery.channels.api.telegramChannel
import io.github.texport.superkassa.delivery.channels.api.whatsAppChannel
import io.github.texport.superkassa.jvm.delivery.impl.PrintDeliveryAdapter
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import io.github.texport.superkassa.delivery.api.port.DeliveryPort as ChannelPort

/**
 * Доставка чека покупателю.
 *
 * Каналы SMS, Telegram, WhatsApp и почты — те же, что у встраиваемой кассы:
 * их собирает ядро из настроек владельца, и ключи каналов не попадают ни
 * в журнал, ни в текст отказа. Узел решает только, какие каналы включены,
 * и добавляет свой сетевой принтер.
 *
 * Канал без настроек отвечает отказом «не настроен», а канал, не включённый
 * владельцем, — отказом «нет канала»: чек, записанный доставленным, но не
 * ушедший, покупатель не получит, а кассир об этом не узнает.
 */
@Configuration
class DeliveryConfig {

    private val logger = LoggerFactory.getLogger(DeliveryConfig::class.java)

    @Bean
    fun deliveryPort(settings: CoreSettings): DeliveryPort {
        val channels = selectedChannels(settings).mapNotNull { channelOf(it, settings.delivery) }
        if (channels.isEmpty()) {
            logger.warn("No delivery channel is configured: receipt delivery will be refused")
        }
        return ServerDeliveryServiceAdapter(createDeliveryServiceApi(channels))
    }

    /** Включённые каналы подробных настроек, а без них — простой список каналов. */
    private fun selectedChannels(settings: CoreSettings): List<String> {
        val detailed = settings.delivery?.channels.orEmpty()
        val names = if (detailed.isEmpty()) {
            settings.deliveryChannels
        } else {
            detailed.filter { it.enabled }.map { it.channel }
        }
        return names.map { it.uppercase() }.distinct()
    }

    private fun channelOf(name: String, delivery: DeliverySettings?): ChannelPort? {
        val channel = DeliveryChannel.entries.firstOrNull { it.name == name }
            ?: return null.also { logger.warn("Unknown delivery channel: {}", name) }
        return when (channel) {
            DeliveryChannel.PRINT -> networkPrinter(delivery?.print)
            DeliveryChannel.EMAIL -> emailChannel(delivery?.email?.toSmtpServer())
            DeliveryChannel.SMS -> smsChannel(delivery?.sms?.providerUrl, delivery?.sms?.apiKey)
            DeliveryChannel.TELEGRAM -> telegramChannel(delivery?.telegram?.botToken)
            DeliveryChannel.WHATSAPP -> whatsAppChannel(delivery?.whatsapp?.accessToken, delivery?.whatsapp?.phoneNumberId)
        }
    }

    /** Принтер без адреса не собирается: печать тогда отвечает отказом «нет канала». */
    private fun networkPrinter(print: PrintDeliverySettings?): ChannelPort? {
        val connection = print?.connection ?: return null
        return PrintDeliveryAdapter(connection.host ?: return null, connection.port ?: return null)
    }

    private fun EmailProviderSettings.toSmtpServer() = SmtpServer(host, port, user, password, from)
}
