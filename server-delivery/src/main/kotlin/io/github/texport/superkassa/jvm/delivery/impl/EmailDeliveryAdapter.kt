package io.github.texport.superkassa.jvm.delivery.impl

import io.github.texport.superkassa.delivery.api.model.DeliveryChannel
import io.github.texport.superkassa.delivery.api.model.DeliveryRequest
import io.github.texport.superkassa.delivery.api.model.DeliveryResult
import io.github.texport.superkassa.delivery.api.port.DeliveryPort
import io.github.texport.superkassa.jvm.shared.strings.api.ErrorResolver
import io.github.texport.superkassa.jvm.shared.strings.api.key.DeliveryErrorKey
import io.github.texport.superkassa.jvm.shared.strings.api.key.DeliveryTemplateKey
import io.github.texport.superkassa.jvm.shared.strings.impl.DefaultErrorResolver
import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import org.slf4j.LoggerFactory
import java.util.Properties

/**
 * Адаптер отправки чеков по email (SMTP).
 *
 * @property host Хост SMTP-сервера.
 * @property port Порт SMTP-сервера.
 * @property user Имя пользователя для аутентификации.
 * @property password Пароль для аутентификации.
 * @property from Адрес отправителя.
 * @property errorResolver Сервис сопоставления локализованных сообщений.
 */
class EmailDeliveryAdapter(
    private val host: String,
    private val port: Int,
    private val user: String?,
    private val password: String?,
    private val from: String,
    private val errorResolver: ErrorResolver = DefaultErrorResolver()
) : DeliveryPort {

    override val channel: DeliveryChannel = DeliveryChannel.EMAIL
    private val logger = LoggerFactory.getLogger(EmailDeliveryAdapter::class.java)

    /**
     * Отправляет фискальный чек по электронной почте через протокол SMTP.
     *
     * @param request параметры отправки [DeliveryRequest].
     * @return результат отправки [DeliveryResult].
     */
    override fun send(request: DeliveryRequest): DeliveryResult {
        val to = request.destination ?: return DeliveryResult(
            ok = false,
            message = errorResolver.resolve(
                DeliveryErrorKey.EMAIL_DESTINATION_REQUIRED
            ).toString()
        )
        val subject = errorResolver.resolve(DeliveryTemplateKey.EMAIL_SUBJECT).formatArgs(request.documentId).format("ru")
        val body = when {
            request.payloadUrl != null -> errorResolver.resolve(DeliveryTemplateKey.EMAIL_BODY_URL).formatArgs(request.payloadUrl!!).format("ru")
            request.payloadBytes != null -> errorResolver.resolve(DeliveryTemplateKey.EMAIL_BODY_ATTACHMENT).format("ru")
            else -> return DeliveryResult(
                ok = false,
                message = errorResolver.resolve(
                    DeliveryErrorKey.EMAIL_PAYLOAD_MISSING
                ).toString()
            )
        }
        return try {
            val props = Properties().apply {
                put("mail.smtp.host", host)
                put("mail.smtp.port", port.toString())
                put("mail.smtp.auth", if (user != null) "true" else "false")
                put("mail.smtp.starttls.enable", "true")
            }
            val auth = if (user != null && password != null) {
                object : jakarta.mail.Authenticator() {
                    override fun getPasswordAuthentication() =
                        jakarta.mail.PasswordAuthentication(user, password)
                }
            } else {
                null
            }
            val session = Session.getInstance(props, auth)
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(this@EmailDeliveryAdapter.from))
                addRecipient(Message.RecipientType.TO, InternetAddress(to))
                setSubject(subject)
            }
            val multipart = MimeMultipart()
            val textPart = MimeBodyPart().apply {
                setText(body, "UTF-8")
            }
            multipart.addBodyPart(textPart)
            val payload = request.payloadBytes
            if (payload != null && payload.isNotEmpty()) {
                val attachPart = MimeBodyPart().apply {
                    setContent(payload, "application/pdf")
                    fileName = "receipt-${request.documentId}.pdf"
                }
                multipart.addBodyPart(attachPart)
            }
            message.setContent(multipart)
            Transport.send(message)
            logger.debug("Email sent for document {} to {}", request.documentId, to)
            DeliveryResult(true)
        } catch (e: Exception) {
            logger.error("Email failed for document {}: {}", request.documentId, e.message, e)
            val msg = e.message ?: "Unknown error"
            val msgStr = errorResolver.resolve(
                DeliveryErrorKey.EMAIL_SEND_FAILED
            ).formatArgs(msg).toString()
            DeliveryResult(
                ok = false,
                message = msgStr
            )
        }
    }
}
