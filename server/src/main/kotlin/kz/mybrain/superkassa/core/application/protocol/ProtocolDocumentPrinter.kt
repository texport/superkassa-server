package kz.mybrain.superkassa.core.application.protocol

import io.github.texport.superkassa.core.domain.api.exception.NotFoundException
import io.github.texport.superkassa.core.domain.api.exception.ValidationException
import io.github.texport.superkassa.core.domain.api.port.integration.DocumentConvertPort
import io.github.texport.superkassa.core.domain.api.port.integration.StoragePort
import io.github.texport.superkassa.core.domain.api.port.internal.ReceiptRenderPort
import io.github.texport.superkassa.core.presentation.api.SuperkassaApi
import io.github.texport.superkassa.core.string.api.TrilingualMessage
import org.springframework.stereotype.Service
import io.github.texport.superkassa.core.domain.api.model.receipt.ReceiptLayoutType as Layout
import io.github.texport.superkassa.core.presentation.api.model.receipt.ReceiptLayoutType as RequestedLayout

/**
 * Печатная форма документа по переданному пакету протокола.
 *
 * Рисовальщик у кассы один, и он же рисует документ, пробитый на другой
 * машине: вид документа один, и второго рисовальщика для него заводить
 * незачем. Отсюда и вход — пакет протокола: это всё, что о документе
 * знают вне кассы, где он пробит.
 *
 * Своих документов это не касается: они лежат в журнале этой кассы
 * и рисуются по идентификатору.
 */
@Service
class ProtocolDocumentPrinter(
    private val kkmService: SuperkassaApi,
    private val storage: StoragePort,
    private val renderer: ReceiptRenderPort,
    private val converter: DocumentConvertPort
) {

    /**
     * Рисует документ пакета разметкой печатной формы.
     *
     * @param kkmId касса, которой рисуется документ: её оформление и её права.
     * @param pin пин оператора этой кассы.
     * @param packet тело пакета: запрос кассы и ответ ОФД на него.
     * @param layout ширина ленты; не задана — та, что настроена у кассы.
     * @return HTML печатной формы.
     * @throws NotFoundException касса не найдена.
     * @throws ValidationException пакет не разобран или его команда документа не порождает.
     */
    fun html(kkmId: String, pin: String, packet: String, layout: RequestedLayout?): String {
        kkmService.authenticate(kkmId, pin)
        val kkm = storage.findKkm(kkmId) ?: throw NotFoundException(KKM_NOT_FOUND, "KKM_NOT_FOUND")
        val parsed = ProtocolPacket.of(packet) ?: throw ValidationException(PACKET_UNREADABLE, PACKET_CODE)
        val drawnBy = kkm.withProtocolRegistration(parsed.service)
        val document = documentOf(parsed, drawnBy) ?: throw ValidationException(NOT_A_DOCUMENT, PACKET_CODE)
        return document.draw(renderer, drawnBy, layout?.let { Layout.valueOf(it.name) })
    }

    /** Та же форма растром: так её показывают на экране и шлют на принтер. */
    fun image(kkmId: String, pin: String, packet: String, layout: RequestedLayout?): ByteArray =
        converter.htmlToImage(html(kkmId, pin, packet, layout))

    /** Та же форма в PDF: так её сохраняют и передают покупателю. */
    fun pdf(kkmId: String, pin: String, packet: String, layout: RequestedLayout?): ByteArray =
        converter.htmlToPdf(html(kkmId, pin, packet, layout))
}

private val KKM_NOT_FOUND = TrilingualMessage(
    ru = "Касса не найдена",
    kk = "Касса табылмады",
    en = "Cash register is not found"
)

private val PACKET_UNREADABLE = TrilingualMessage(
    ru = "Пакет протокола не разобран: печатать нечего",
    kk = "Хаттама пакеті талданбады: басып шығаратын ештеңе жоқ",
    en = "Protocol packet is unreadable: there is nothing to print"
)

private val NOT_A_DOCUMENT = TrilingualMessage(
    ru = "Команда пакета не порождает документа с печатной формой",
    kk = "Пакет командасы басып шығару пішіні бар құжат жасамайды",
    en = "Packet command does not produce a printable document"
)

private const val PACKET_CODE = "PROTOCOL_PACKET_INVALID"
