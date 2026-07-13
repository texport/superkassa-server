package kz.mybrain.superkassa.core.application.http.controllers

import io.github.texport.superkassa.core.presentation.api.SuperkassaApi
import io.github.texport.superkassa.core.presentation.api.model.shift.ReportResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_REPORT_ACCEPTED
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_400_BAD_REQUEST
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_403_FORBIDDEN
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_404_KKM_NOT_FOUND
import kz.mybrain.superkassa.core.application.http.annotation.KkmApiResponses
import kz.mybrain.superkassa.core.application.http.utils.AuthHeaderUtils
import org.springframework.web.bind.annotation.*

/**
 * Контроллер для работы с отчетами.
 * Отвечает за создание X-отчетов.
 */
@RestController
@RequestMapping("/kkm/{kkmId}/report")
@Tag(name = "Отчеты", description = "Создание отчетов ККМ")
class ReportsController(private val kkmService: SuperkassaApi) {

    /**
     * Сформировать X-отчет (без закрытия смены).
     */
    @PostMapping
    @Operation(
        summary = "Создать X-отчет",
        description = """
            Создает X-отчет (промежуточный отчет) без закрытия смены.

            Что делает метод:
            - Формирует X-отчет с текущими данными по смене
            - Отправляет данные в ОФД
            - Возвращает результат создания отчета

            X-отчет показывает:
            - Текущее состояние смены
            - Количество и сумму операций
            - Статистику по операциям
            - Не закрывает смену (в отличие от Z-отчета)

            Требования:
            - ККМ должна быть зарегистрирована и находиться в состоянии ACTIVE
            - Смена должна быть открыта
            - ПИН-код должен быть передан в заголовке Authorization (Bearer <pin> или просто <pin>)
            - ПИН-код должен соответствовать пользователю с правами CASHIER или ADMIN

            Что передавать:
            - kkmId (в пути): Идентификатор ККМ
            - Authorization (в заголовке): ПИН-код пользователя в формате "Bearer <pin>" или просто "<pin>"
            - Тело запроса может быть пустым (все параметры берутся из пути и заголовка)

            Что возвращается:
            - ReportResult с полями:
              * documentId: Идентификатор сгенерированного фискального документа отчета в БД.
              * deliveryStatus: Статус доставки отчета в ОФД/клиенту (ONLINE_OK, ONLINE_ERROR, OFFLINE_QUEUED, NOT_SENT).
              * deliveryError: Текст ошибки доставки, если отправка завершилась неудачно (опционально).
              * deliveryPayload: Бинарное представление сгенерированного отчета (опционально).

            Важно:
            - X-отчет не закрывает смену, можно продолжать работу
            - Для закрытия смены используйте POST /kkm/{kkmId}/shift/close
            - X-отчет можно создавать многократно в течение смены
        """
    )
    @KkmApiResponses(
        ok = MSG_200_REPORT_ACCEPTED,
        badRequest = MSG_400_BAD_REQUEST,
        forbidden = MSG_403_FORBIDDEN,
        notFound = MSG_404_KKM_NOT_FOUND
    )
    fun createXReport(
        @PathVariable kkmId: String,
        @RequestHeader("Authorization") authHeader: String?
    ): ReportResponse {
        val pin = AuthHeaderUtils.extractPin(authHeader)
        return kkmService.createReport(kkmId, pin)
    }
}
