package io.github.texport.superkassa.jvm.delivery.data

import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import io.github.texport.superkassa.delivery.domain.model.DeliveryChannel
import io.github.texport.superkassa.delivery.domain.model.DeliveryRequest
import io.github.texport.superkassa.delivery.domain.model.DeliveryResult
import io.github.texport.superkassa.delivery.domain.port.DeliveryAdapter
import org.slf4j.LoggerFactory
import java.util.Properties

/**
 * Адаптер отправки чеков по email (SMTP).
 */
class EmailDeliveryAdapter(
    private val host: String,
    private val port: Int,
    private val user: String?,
    private val password: String?,
    private val from: String
) : DeliveryAdapter {

    override val channel: DeliveryChannel = DeliveryChannel.EMAIL
    private val logger = LoggerFactory.getLogger(EmailDeliveryAdapter::class.java)

    override fun send(request: DeliveryRequest): DeliveryResult {
        val to = request.destination ?: return DeliveryResult(
            ok = false,
            message = "Email destination required / " +
                "Требуется адрес электронной почты / " +
                "Электрондық пошта мекенжайы қажет"
        )
        val subject = "Чек ${request.documentId}"
        val body = when {
            request.payloadUrl != null -> "Ссылка на чек: ${request.payloadUrl}"
            request.payloadBytes != null -> "Чек во вложении."
            else -> return DeliveryResult(
                ok = false,
                message = "No payload / Отсутствуют данные чека / Чек деректері жоқ"
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
            DeliveryResult(
                ok = false,
                message = "Email sending failed: $msg / " +
                    "Ошибка отправки почты: $msg / " +
                    "Пошта жіберу қатесі: $msg"
            )
        }
    }
}
