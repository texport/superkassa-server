package kz.mybrain.superkassa.core.application.http.controllers

import io.github.texport.superkassa.core.presentation.api.model.receipt.ReceiptLayoutType
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_PRINT_HTML
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_RECEIPT_PDF
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_403_FORBIDDEN
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_404_KKM_NOT_FOUND
import kz.mybrain.superkassa.core.application.http.annotation.KkmApiResponses
import kz.mybrain.superkassa.core.application.http.utils.AuthHeaderUtils
import kz.mybrain.superkassa.core.application.protocol.ProtocolDocumentPrinter
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * Печатная форма документа по переданным данным.
 *
 * Отличие от печати своего документа одно, но решающее: касса-рисовальщик
 * не обязана быть той, что документ пробила. Документ приходит пакетом
 * протокола — запросом кассы и ответом ОФД, — и это всё, что о нём знают
 * снаружи: так его хранит и отдаёт сервер приёма данных.
 *
 * Вид документа при этом один: рисует его тот же рисовальщик, что и свои
 * чеки, и в том же оформлении этой кассы.
 */
@RestController
@RequestMapping("/kkm")
@Tag(name = "Печатная форма по данным", description = "Документ рисуется по переданному пакету протокола")
class ProtocolDocumentController(private val printer: ProtocolDocumentPrinter) {

    @PostMapping(
        "/{kkmId}/documents/print.html",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.TEXT_HTML_VALUE]
    )
    @Operation(
        summary = "Печатная форма переданного документа (HTML)",
        description = """
            Рисует печатную форму документа по пакету протокола CPCR.

            **Тело запроса:** объект с полями `request` и `response` — запрос кассы и ответ ОФД на него.

            **Что умеет метод:**
            - Распознаёт вид документа по команде пакета: чек, X- или Z-отчёт, внесение и изъятие.
            - Печатает реквизиты из пакета — регистрационный номер КГД, БИН и адрес той кассы, что документ пробила.
            - Оформление — язык, ширину ленты, логотип — берёт у кассы `kkmId`, которая рисует.
        """
    )
    @KkmApiResponses(ok = MSG_200_PRINT_HTML, forbidden = MSG_403_FORBIDDEN, notFound = MSG_404_KKM_NOT_FOUND)
    fun printHtml(
        @PathVariable kkmId: String,
        @RequestParam(required = false) layout: ReceiptLayoutType?,
        @RequestHeader("Authorization") authHeader: String?,
        @RequestBody packet: String
    ): ResponseEntity<String> = ResponseEntity.ok()
        .contentType(MediaType.valueOf("text/html;charset=UTF-8"))
        .body(printer.html(kkmId, AuthHeaderUtils.extractPin(authHeader), packet, layout))

    @PostMapping(
        "/{kkmId}/documents/print.png",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.IMAGE_PNG_VALUE]
    )
    @Operation(
        summary = "Печатная форма переданного документа (PNG)",
        description = "Та же форма растром: для показа на экране и для печати на принтере рабочего места."
    )
    @KkmApiResponses(ok = "Успешное получение изображения документа", forbidden = MSG_403_FORBIDDEN)
    fun printPng(
        @PathVariable kkmId: String,
        @RequestParam(required = false) layout: ReceiptLayoutType?,
        @RequestHeader("Authorization") authHeader: String?,
        @RequestBody packet: String
    ): ResponseEntity<ByteArray> = ResponseEntity.ok()
        .contentType(MediaType.IMAGE_PNG)
        .body(printer.image(kkmId, AuthHeaderUtils.extractPin(authHeader), packet, layout))

    @PostMapping(
        "/{kkmId}/documents/print.pdf",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_PDF_VALUE]
    )
    @Operation(
        summary = "Печатная форма переданного документа (PDF)",
        description = "Та же форма в PDF: для сохранения в файл и передачи покупателю."
    )
    @KkmApiResponses(ok = MSG_200_RECEIPT_PDF, forbidden = MSG_403_FORBIDDEN)
    fun printPdf(
        @PathVariable kkmId: String,
        @RequestParam(required = false) layout: ReceiptLayoutType?,
        @RequestHeader("Authorization") authHeader: String?,
        @RequestBody packet: String
    ): ResponseEntity<ByteArray> = ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .body(printer.pdf(kkmId, AuthHeaderUtils.extractPin(authHeader), packet, layout))
}
