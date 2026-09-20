package kz.mybrain.superkassa.core.application.http.controllers

import io.github.texport.superkassa.core.presentation.api.DeliveryApi
import io.github.texport.superkassa.core.presentation.api.SuperkassaApi
import io.github.texport.superkassa.core.presentation.api.model.kkm.DocumentDetailsResponse
import io.github.texport.superkassa.core.presentation.api.model.kkm.FiscalDocumentResponse
import io.github.texport.superkassa.core.presentation.api.model.ofd.DeliveryRetryItemResponse
import io.github.texport.superkassa.core.presentation.api.model.ofd.DeliveryRetryResponse
import io.github.texport.superkassa.core.presentation.api.model.receipt.PrintDocumentType
import io.github.texport.superkassa.core.presentation.api.model.receipt.ReceiptLayoutType
import io.github.texport.superkassa.core.presentation.api.model.shift.ReportResponse
import io.github.texport.superkassa.core.presentation.api.model.shift.ShiftResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_DELIVERY_RETRY
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_PRINT_HTML
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_RECEIPT_PDF
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_SHIFTS_LIST
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_SHIFT_CLOSE_ACCEPTED
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_SHIFT_DOCUMENTS
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_SHIFT_OPENED
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_400_BAD_REQUEST
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_403_FORBIDDEN
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_404_DOCUMENT_NOT_FOUND
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_404_KKM_NOT_FOUND
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_409_SHIFT_NOT_OPEN
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_409_SHIFT_OPEN
import kz.mybrain.superkassa.core.application.http.annotation.KkmApiResponses
import kz.mybrain.superkassa.core.application.http.utils.AuthHeaderUtils
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/kkm")
@Tag(name = "Управление сменой (Z-Отчет) ККМ", description = "Операции со сменами, чеками и отчетами")
class KkmController(
    private val kkmService: SuperkassaApi,
    private val deliveryApi: DeliveryApi
) {

    @PostMapping("/{kkmId}/shift/open")
    @Operation(
        summary = "Открыть смену",
        description = """
            Выполняет операцию открытия новой кассовой смены на ККМ.
            
            **Требования и предусловия:**
            1. Кассовый аппарат должен быть фискализирован и активен (`state = ACTIVE`).
            2. Предыдущая кассовая смена должна быть обязательно закрыта (Z-отчетом). Нельзя открыть смену, если она уже открыта (ошибка `409 Conflict`).
            3. Требуется авторизация пользователя ККМ (ПИН-код кассира/администратора передается в HTTP-заголовке `Authorization`).
            
            **Что выполняет метод:**
            - Генерирует уникальный идентификатор смены.
            - Создает печатную форму документа открытия смены.
            - Регистрирует запись в локальной БД.
            - Инициирует отправку фискального пакета открытия смены в ОФД.
        """
    )
    @KkmApiResponses(
        ok = MSG_200_SHIFT_OPENED,
        forbidden = MSG_403_FORBIDDEN,
        conflict = MSG_409_SHIFT_OPEN,
        notFound = MSG_404_KKM_NOT_FOUND
    )
    fun openShift(
        @PathVariable kkmId: String,
        @RequestHeader("Authorization") authHeader: String?
    ): ShiftResponse {
        val pin = AuthHeaderUtils.extractPin(authHeader)
        return kkmService.openShift(kkmId, pin)
    }

    @PostMapping("/{kkmId}/shift/close")
    @Operation(
        summary = "Закрыть смену (Z-отчет)",
        description = """
            Выполняет операцию закрытия активной кассовой смены и формирует итоговый Z-отчет с гашением.
            
            **Требования и предусловия:**
            1. На ККМ должна быть открыта активная кассовая смена (ошибка `409 Conflict`, если смена уже закрыта).
            2. Продолжительность смены не должна превышать 24 часа (при превышении касса блокируется до снятия Z-отчета).
            3. Требуется авторизация пользователя ККМ с правами на снятие отчетов (роль `CASHIER` или `ADMIN`).
            
            **Что выполняет метод:**
            - Подсчитывает суммарные фискальные показатели смены (продажи, возвраты, типы оплат, налоги).
            - Обнуляет регистры дневных накоплений (производит гашение).
            - Генерирует фискальный Z-отчет.
            - Передает отчет в очередь отправки в ОФД.
            - Меняет статус смены в БД на `CLOSED`.
        """
    )
    @KkmApiResponses(
        ok = MSG_200_SHIFT_CLOSE_ACCEPTED,
        badRequest = MSG_400_BAD_REQUEST,
        forbidden = MSG_403_FORBIDDEN,
        conflict = MSG_409_SHIFT_NOT_OPEN,
        notFound = MSG_404_KKM_NOT_FOUND
    )
    fun closeShift(
        @PathVariable kkmId: String,
        @RequestHeader("Authorization") authHeader: String?
    ): ReportResponse {
        val pin = AuthHeaderUtils.extractPin(authHeader)
        return kkmService.closeShift(kkmId, pin)
    }

    @GetMapping("/{kkmId}/shifts")
    @Operation(
        summary = "Список смен",
        description = """
            Возвращает постраничный список всех зарегистрированных кассовых смен для указанного аппарата ККМ.
            
            **Сортировка:**
            - Список отсортирован по времени открытия смены в обратном хронологическом порядке (сначала новые).
            
            **Применение:**
            - Отображение истории смен в личном кабинете.
            - Поиск идентификатора конкретной смены (`shiftId`) для последующего просмотра её фискальных документов.
        """
    )
    @KkmApiResponses(ok = MSG_200_SHIFTS_LIST, forbidden = MSG_403_FORBIDDEN, notFound = MSG_404_KKM_NOT_FOUND)
    fun listShifts(
        @PathVariable kkmId: String,
        @RequestParam(defaultValue = "100") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestHeader("Authorization") authHeader: String?
    ): List<ShiftResponse> {
        val pin = AuthHeaderUtils.extractPin(authHeader)
        return kkmService.listShifts(kkmId, limit, offset, pin)
    }

    @GetMapping("/{kkmId}/shifts/{shiftId}/documents")
    @Operation(
        summary = "Документы смены",
        description = """
            Возвращает список всех фискальных документов (чеков, отчетов, внесений/изъятий), оформленных в рамках указанной смены.
            
            **Включает в себя:**
            - Чеки продажи (`SALE`) и возврата (`RETURN`).
            - Чеки покупки (`BUY`) и возврата покупки (`BUY_RETURN`).
            - Документы операций с наличными внесения (`CASH_IN`) и изъятия (`CASH_OUT`).
            - Сменные отчеты (`X_REPORT` / Z-отчеты).
            
            Каждая запись содержит сведения об уникальном фискальном признаке (FP/FPD), статусе отправки в ОФД и признаке автономности проведения документа.
        """
    )
    @KkmApiResponses(ok = MSG_200_SHIFT_DOCUMENTS, forbidden = MSG_403_FORBIDDEN, notFound = MSG_404_KKM_NOT_FOUND)
    fun listShiftDocuments(
        @PathVariable kkmId: String,
        @PathVariable shiftId: String,
        @RequestParam(defaultValue = "100") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestHeader("Authorization") authHeader: String?
    ): List<FiscalDocumentResponse> {
        val pin = AuthHeaderUtils.extractPin(authHeader)
        return kkmService.listShiftDocuments(kkmId, shiftId, limit, offset, pin)
    }

    @GetMapping("/{kkmId}/shift/documents")
    @Operation(
        summary = "Документы текущей смены",
        description = """
            Возвращает список фискальных документов, выбитых в рамках текущей (активной) открытой смены.
            
            **Особенности:**
            - Если в данный момент смена на ККМ закрыта, возвращается ошибка `409 Conflict`.
            - Метод идеален для оперативного контроля продаж кассира на точке в режиме реального времени.
        """
    )
    @KkmApiResponses(
        ok = MSG_200_SHIFT_DOCUMENTS,
        forbidden = MSG_403_FORBIDDEN,
        conflict = MSG_409_SHIFT_NOT_OPEN,
        notFound = MSG_404_KKM_NOT_FOUND
    )
    fun listCurrentShiftDocuments(
        @PathVariable kkmId: String,
        @RequestParam(defaultValue = "100") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestHeader("Authorization") authHeader: String?
    ): List<FiscalDocumentResponse> {
        val pin = AuthHeaderUtils.extractPin(authHeader)
        val shift = kkmService.getOpenShift(kkmId, pin)
        return kkmService.listShiftDocuments(kkmId, shift.id, limit, offset, pin)
    }

    @GetMapping("/{kkmId}/documents")
    @Operation(
        summary = "Документы за период",
        description = """
            Возвращает плоский список фискальных документов ККМ, созданных за произвольный промежуток времени.
            
            **Фильтрация по времени:**
            - Параметры `from` (начало, включительно) и `to` (конец, исключительно) передаются как временная метка Unix Epoch в миллисекундах.
            
            **Применение:**
            - Построение внешних отчетов по продажам за день/неделю/месяц.
            - Сверка данных с учетной системой ERP/1С.
        """
    )
    @KkmApiResponses(ok = MSG_200_SHIFT_DOCUMENTS, forbidden = MSG_403_FORBIDDEN, notFound = MSG_404_KKM_NOT_FOUND)
    fun listDocumentsByPeriod(
        @PathVariable kkmId: String,
        @RequestParam from: Long,
        @RequestParam to: Long,
        @RequestParam(defaultValue = "100") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestHeader("Authorization") authHeader: String?
    ): List<FiscalDocumentResponse> {
        val pin = AuthHeaderUtils.extractPin(authHeader)
        return kkmService.listFiscalDocumentsByPeriod(kkmId, from, to, limit, offset, pin)
    }

    /**
     * Документ вместе с составом чека и тем, кто его оформил.
     */
    @GetMapping("/{kkmId}/documents/{documentId}")
    @Operation(
        summary = "Документ с составом чека",
        description = """
            Возвращает документ, его позиции и имя оформившего кассира.

            Когда использовать:
            - частичный возврат: вернуть можно только то, что продано,
              и в том количестве, в каком продано;
            - разбор отказа ОФД: по списку документов виден только код.

            У отчётов и операций с наличными список позиций пуст — это не ошибка.
        """
    )
    @KkmApiResponses(ok = "Документ получен", notFound = MSG_404_KKM_NOT_FOUND)
    fun getDocumentDetails(
        @PathVariable kkmId: String,
        @PathVariable documentId: String,
        @RequestHeader("Authorization") authHeader: String?
    ): DocumentDetailsResponse =
        kkmService.getDocumentDetails(kkmId, documentId, AuthHeaderUtils.extractPin(authHeader))

    @GetMapping("/{kkmId}/documents/{documentId}/print.html", produces = [MediaType.TEXT_HTML_VALUE])
    @Operation(
        summary = "Печатная форма документа (HTML)",
        description = """
            Возвращает готовую сверстанную печатную форму чека или сменного отчета в формате HTML.
            
            **Параметры макета:**
            - Через параметр `layout` можно задать шаблон отображения: `TAPE_58MM` (узкий чек для термопринтера), `TAPE_80MM` (стандартный чек) или `FULLSCREEN` (версия для смартфона/A4).
            
            **Что умеет метод:**
            - Автоматически распознает тип документа (фискальный чек, Z-отчет, X-отчет, внесение) по его `documentId`.
            - Подгружает актуальные трехязычные шаблоны и переводы.
        """
    )
    @KkmApiResponses(
        ok = MSG_200_PRINT_HTML,
        forbidden = MSG_403_FORBIDDEN,
        notFound = MSG_404_DOCUMENT_NOT_FOUND
    )
    fun getDocumentPrintHtml(
        @PathVariable kkmId: String,
        @PathVariable documentId: String,
        @RequestParam(required = false) layout: ReceiptLayoutType?,
        @RequestHeader("Authorization") authHeader: String?
    ): ResponseEntity<String> {
        val pin = AuthHeaderUtils.extractPin(authHeader)
        val shifts = try {
            kkmService.listShifts(kkmId, 100, 0, pin)
        } catch (_: Exception) {
            emptyList()
        }
        val matchingShift = shifts.firstOrNull {
            it.id == documentId || it.openDocumentId == documentId || it.closeDocumentId == documentId
        }

        val html = if (matchingShift != null) {
            val type = if (matchingShift.closeDocumentId == documentId || matchingShift.id == documentId) {
                PrintDocumentType.CLOSE_SHIFT
            } else {
                PrintDocumentType.OPEN_SHIFT
            }
            kkmService.getPrintHtml(kkmId, type, null, matchingShift.id, pin, layout)
        } else {
            kkmService.getPrintHtml(kkmId, PrintDocumentType.DOCUMENT, documentId, null, pin, layout)
        }

        return ResponseEntity.ok()
            .contentType(MediaType.valueOf("text/html;charset=UTF-8"))
            .body(html)
    }

    @GetMapping("/{kkmId}/documents/{documentId}/print.pdf", produces = [MediaType.APPLICATION_PDF_VALUE])
    @Operation(
        summary = "Печатная форма документа (PDF)",
        description = """
            Генерирует и возвращает печатную форму документа (чека, отчета) в бинарном формате PDF.
            
            **Использование:**
            - Скачивание копии чека клиентом.
            - Отправка чека на печать через системный диалог ОС Android/iOS.
            
            Поддерживает автоопределение типа документа и выбор ширины чековой ленты (`layout`).
        """
    )
    @KkmApiResponses(
        ok = MSG_200_RECEIPT_PDF,
        forbidden = MSG_403_FORBIDDEN,
        notFound = MSG_404_DOCUMENT_NOT_FOUND
    )
    fun getDocumentPrintPdf(
        @PathVariable kkmId: String,
        @PathVariable documentId: String,
        @RequestParam(required = false) layout: ReceiptLayoutType?,
        @RequestHeader("Authorization") authHeader: String?
    ): ResponseEntity<ByteArray> {
        val pin = AuthHeaderUtils.extractPin(authHeader)
        val shifts = try {
            kkmService.listShifts(kkmId, 100, 0, pin)
        } catch (_: Exception) {
            emptyList()
        }
        val matchingShift = shifts.firstOrNull {
            it.id == documentId || it.openDocumentId == documentId || it.closeDocumentId == documentId
        }

        val bytes = if (matchingShift != null) {
            val type = if (matchingShift.closeDocumentId == documentId || matchingShift.id == documentId) {
                PrintDocumentType.CLOSE_SHIFT
            } else {
                PrintDocumentType.OPEN_SHIFT
            }
            kkmService.getPrintPdf(kkmId, type, null, matchingShift.id, pin, layout)
        } else {
            kkmService.getPrintPdf(kkmId, PrintDocumentType.DOCUMENT, documentId, null, pin, layout)
        }

        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header("Content-Disposition", "attachment; filename=\"document-$documentId.pdf\"")
            .body(bytes)
    }

    @GetMapping("/{kkmId}/documents/{documentId}/print.png", produces = [MediaType.IMAGE_PNG_VALUE])
    @Operation(
        summary = "Печатная форма документа (PNG)",
        description = """
            Рендерит печатную форму чека в растровое изображение формата PNG.
            
            **Применение:**
            - Отображение красивого предпросмотра чека на дисплее покупателя или в интерфейсе кассового терминала без необходимости поддержки HTML/PDF на клиенте.
        """
    )
    @KkmApiResponses(
        ok = "Успешное получение изображения документа",
        forbidden = MSG_403_FORBIDDEN,
        notFound = MSG_404_DOCUMENT_NOT_FOUND
    )
    fun getDocumentPrintPng(
        @PathVariable kkmId: String,
        @PathVariable documentId: String,
        @RequestParam(required = false) layout: ReceiptLayoutType?,
        @RequestHeader("Authorization") authHeader: String?
    ): ResponseEntity<ByteArray> {
        val pin = AuthHeaderUtils.extractPin(authHeader)
        val shifts = try {
            kkmService.listShifts(kkmId, 100, 0, pin)
        } catch (_: Exception) {
            emptyList()
        }
        val matchingShift = shifts.firstOrNull {
            it.id == documentId || it.openDocumentId == documentId || it.closeDocumentId == documentId
        }

        val imageBytes = if (matchingShift != null) {
            val type = if (matchingShift.closeDocumentId == documentId || matchingShift.id == documentId) {
                PrintDocumentType.CLOSE_SHIFT
            } else {
                PrintDocumentType.OPEN_SHIFT
            }
            kkmService.getPrintPng(kkmId, type, null, matchingShift.id, pin, layout)
        } else {
            kkmService.getPrintPng(kkmId, PrintDocumentType.DOCUMENT, documentId, null, pin, layout)
        }

        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_PNG)
            .body(imageBytes)
    }

    @PostMapping("/{kkmId}/documents/{documentId}/delivery/retry")
    @Operation(
        summary = "Повторная отправка чека по каналам",
        description = """
            Инициирует повторную ручную отправку сформированного чека по всем активным каналам связи (Email, Telegram, WhatsApp и т.д.).
            
            **Особенности отправки:**
            - Отправляет бинарные вложения чека (PDF-версия или изображение чека).
            - Каналы связи, настроенные только на отправку ссылки ОФД (LINK), пропускаются во избежание дублирования трафика.
            - Требует указания ПИН-кода в заголовке `Authorization`.
        """
    )
    @KkmApiResponses(
        ok = MSG_200_DELIVERY_RETRY,
        forbidden = MSG_403_FORBIDDEN,
        notFound = MSG_404_DOCUMENT_NOT_FOUND
    )
    fun retryReceiptDelivery(
        @PathVariable kkmId: String,
        @PathVariable documentId: String,
        @RequestHeader("Authorization") authHeader: String?
    ): DeliveryRetryResponse {
        val pin = AuthHeaderUtils.extractPin(authHeader)
        val results = deliveryApi.retryReceiptDelivery(kkmId, documentId, pin)
        return DeliveryRetryResponse(
            results = results.map { (ch, ok) -> DeliveryRetryItemResponse(channel = ch, success = ok) }
        )
    }
}
