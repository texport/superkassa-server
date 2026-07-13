package kz.mybrain.superkassa.core.application.http.controllers

import io.github.texport.superkassa.core.presentation.api.SuperkassaApi
import io.github.texport.superkassa.core.presentation.api.model.common.PaginatedResponse
import io.github.texport.superkassa.core.presentation.api.model.kkm.KkmListParams
import io.github.texport.superkassa.core.presentation.api.model.kkm.KkmResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_KKM_LIST
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_VERSION
import kz.mybrain.superkassa.core.application.http.annotation.KkmApiResponses
import kz.mybrain.superkassa.core.application.info.SystemInfoApplicationService
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Контроллер для получения информации о Superkassa.
 * Предоставляет информацию о версии, режиме работы и статистике системы.
 */
@RestController
@RequestMapping("/")
@Tag(name = "О Superkassa", description = "Информация о версии, режиме работы и статистике.")
class SuperkassaInfoController(
    private val systemInfoService: SystemInfoApplicationService,
    private val kkmService: SuperkassaApi,
    @Value("\${app.version:1.0}") private val appVersion: String
) {

    /**
     * Получить список зарегистрированных ККМ с поддержкой фильтрации, сортировки и пагинации.
     *
     * @param limit максимальное количество записей в ответе (по умолчанию 50).
     * @param offset смещение от начала списка (по умолчанию 0).
     * @param state фильтр по состоянию ККМ (например, ACTIVE, IDLE, INACTIVE).
     * @param search строка для текстового поиска по кассам.
     * @param sortBy поле для сортировки (например, createdAt, factoryNumber).
     * @param order направление сортировки (ASC или DESC).
     * @return [PaginatedResponse] со списком ККМ [KkmResponse].
     */
    @GetMapping("kkm")
    @Operation(
        summary = "Получить список ККМ (с фильтрацией)",
        description = """
            Возвращает постраничный список всех зарегистрированных кассовых аппаратов (ККМ) в системе.

            Позволяет искать кассы по текстовому запросу, фильтровать по их рабочему состоянию,
            а также настраивать сортировку и размер страницы.

            **Параметры фильтрации и пагинации:**
            - **limit** (number, опционально, по умолчанию 50): Количество возвращаемых касс на одной странице.
            - **offset** (number, опционально, по умолчанию 0): Смещение относительно начала списка для пагинации.
            - **state** (string, опционально): Состояние касс для фильтрации (например: `ACTIVE`, `IDLE`, `INACTIVE`).
            - **search** (string, опционально): Текстовый поисковый запрос по заводскому номеру или названию организации.
            - **sortBy** (string, опционально, по умолчанию `createdAt`): Поле для сортировки результатов (`createdAt`, `updatedAt`, `factoryNumber`).
            - **order** (string, опционально, по умолчанию `DESC`): Направление сортировки (`ASC` - по возрастанию, `DESC` - по убыванию).

            **Структура ответа:**
            - **items** (array): Массив сведений о кассах (объекты `KkmResponse`).
            - **total** (number): Общее количество найденных касс по заданным фильтрам.
            - **limit** (number): Использованный лимит записей на страницу.
            - **offset** (number): Использованное смещение выборки.
            - **hasMore** (boolean): Флаг наличия следующих страниц с результатами.

            Метод является публичным и не требует авторизации.
        """
    )
    @KkmApiResponses(ok = MSG_200_KKM_LIST)
    fun listKkms(
        @RequestParam(required = false, defaultValue = "50") limit: Int,
        @RequestParam(required = false, defaultValue = "0") offset: Int,
        @RequestParam(required = false) state: String?,
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false, defaultValue = "createdAt") sortBy: String,
        @RequestParam(required = false, defaultValue = "DESC") order: String
    ): PaginatedResponse<KkmResponse> {
        val params = KkmListParams(
            limit = limit,
            offset = offset,
            state = state,
            search = search,
            sortBy = sortBy,
            sortOrder = order
        )
        val result = kkmService.listKkms(params)
        return PaginatedResponse(
            items = result.items,
            total = result.total,
            limit = params.limit,
            offset = params.offset,
            hasMore = (params.offset + params.limit) < result.total
        )
    }

    /**
     * Получить информацию о версии, режиме работы, хранилище и возможностях Superkassa.
     *
     * @return [Map] с подробными характеристиками текущего запущенного узла.
     */
    @GetMapping("/info")
    @Operation(
        summary = "Информация о Superkassa",
        description = """
            Возвращает подробную информацию о системе Superkassa Core.

            Структура ответа:

            **name** (string): Название системы - всегда "Superkassa Core"

            **version** (string): Версия приложения, берется из конфигурации app.version

            **mode** (string): Режим работы системы:
            - DESKTOP - десктопное приложение для локального использования
            - SERVER - серверный режим для кластера

            **nodeId** (string): Уникальный идентификатор узла в кластере.
            Используется в SERVER режиме для идентификации узла при распределенной обработке.

            **ofdProtocolVersion** (string): Версия протокола ОФД, используемая для обмена с ОФД.
            Текущая версия: "203"

            **storage** (object): Информация о хранилище данных:
            - engine (string): Тип СУБД (SQLITE, POSTGRESQL, MYSQL)
            - jdbcUrl (string): JDBC URL для подключения к БД (пароль скрыт)

            **statistics** (object): Статистика системы:
            - registeredKkms (number): Количество зарегистрированных ККМ в системе

            **features** (object): Дополнительные возможности системы:
            - allowSettingsChanges (boolean): Разрешены ли изменения настроек через API
            - deliveryChannels (array<string>): Список каналов доставки документов (например, ["PRINT"])
            - ofdTimeoutSeconds (number): Общее время на обработку транзакции ОФД (сек., не менее 5)
            - ofdReconnectIntervalSeconds (number): Интервал между попытками восстановления связи (сек., не менее 60)

            Используется для мониторинга и диагностики системы.
        """
    )
    @KkmApiResponses(ok = MSG_200_VERSION)
    fun info(): Map<String, Any> {
        return systemInfoService.getInfo(appVersion)
    }
}
