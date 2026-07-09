package kz.mybrain.superkassa.core.application.http.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_KKM_DELETED
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_KKM_INIT
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_400_BAD_STATUS_OR_SHIFT
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_400_VALIDATION
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_403_FORBIDDEN
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_404_KKM_NOT_FOUND
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_409_DELETE_BLOCKED
import kz.mybrain.superkassa.core.application.http.annotation.KkmApiResponses
import kz.mybrain.superkassa.core.application.http.toResponse
import kz.mybrain.superkassa.core.application.http.utils.AuthHeaderUtils
import kz.mybrain.superkassa.core.presentation.facade.SuperkassaApi
import kz.mybrain.superkassa.core.presentation.model.*
import org.springframework.web.bind.annotation.*

/**
 * Контроллер для ввода/вывода ККМ из/в эксплуатацию.
 * Отвечает за регистрацию, инициализацию и удаление ККМ.
 */
@RestController
@RequestMapping("/kkm")
@Tag(name = "Ввод/Вывод из/в эксплуатацию ККМ", description = "Регистрация, инициализация и удаление ККМ")
class KkmDecommissioningController(private val kkmService: SuperkassaApi) {

    /**
     * Упрощенная инициализация ККМ без черновика.
     * Используется для ККМ, которые уже были зарегистрированы в ОФД ранее.
     * Все необходимые данные получаются из ОФД автоматически.
     */
    @PostMapping("/init")
    @Operation(
        operationId = "03_initKkm",
        summary = "Инициализация ККМ без черновика",
        description = """
            Выполняет первичную инициализацию (фискализацию) кассового аппарата в локальной базе данных Superkassa.
            Применяется для ККМ, которые уже были зарегистрированы в ОФД и имеют действующий системный ID и токен доступа.
            
            **Что делает метод:**
            1. Проверяет переданные параметры подключения к ОФД (провайдер, системный ID, токен).
            2. Подключается к ОФД и запрашивает актуальную регистрационную карточку ККМ.
            3. Автоматически извлекает из ОФД следующие данные:
               - Регистрационный номер ККМ в КГД
               - Заводской номер и год выпуска
               - Сервисные данные (название организации налогоплательщика, БИН/ИИН, юридический адрес)
            4. Сохраняет кассовый аппарат в базе данных со статусом `ACTIVE`.
            5. Регистрирует первого пользователя (администратора кассы) с правами доступа.
            
            **Требования:**
            - Касса с указанным системным ID должна быть зарегистрирована в ОФД.
            - Переданный токен ОФД должен быть активным.
            - Запрос требует авторизации по ПИН-коду (передается в заголовке Authorization).
            
            **Параметры:**
            - **Authorization** (заголовок): ПИН-код первого администратора (например, `0000`).
            - **RequestBody**: Объект `KkmInitSimpleRequest` с полями:
              - `ofdId`: Код провайдера ОФД (например, `KAZAKHTELECOM`).
              - `ofdEnvironment`: Окружение ОФД (`TEST` или `PROD`).
              - `ofdSystemId`: Уникальный числовой ID кассы в системе ОФД.
              - `ofdToken`: Токен авторизации подключения к ОФД.
            
            **Возвращаемые коды:**
            - 200 OK: Касса успешно инициализирована, возвращен объект `KkmResponse`.
            - 400 Bad Request: Ошибка валидации параметров запроса.
            - 403 Forbidden: Неверный ПИН-код администратора.
        """
    )
    @KkmApiResponses(
        ok = MSG_200_KKM_INIT,
        badRequest = MSG_400_VALIDATION,
        forbidden = MSG_403_FORBIDDEN
    )
    fun initKkm(
        @RequestHeader("Authorization") authHeader: String?,
        @RequestBody @Valid request: KkmInitSimpleRequest
    ): KkmResponse {
        val pin = AuthHeaderUtils.extractPin(authHeader)
        return kkmService.initKkmSimple(pin, request).toResponse()
    }

    /**
     * Сгенерировать заводской номер и год выпуска для регистрации ККМ в ОФД.
     * Ничего не создаёт в базе, просто возвращает значения.
     */
    @GetMapping("/factory-info")
    @Operation(
        operationId = "02_generateFactoryInfo",
        summary = "Сгенерировать заводской номер и год выпуска",
        description = """
            Генерирует и возвращает уникальный заводской номер ККМ и год выпуска.
            
            Этот эндпоинт необходим на этапе подготовки к регистрации кассового аппарата в ОФД и налоговых органах:
            - Заводской номер генерируется по специальному алгоритму производителя, проходящему валидацию в ОФД.
            - Год выпуска возвращается в соответствии с текущим календарным годом.
            
            Важно:
            - Метод носит вспомогательный характер.
            - Вызов этого метода не создаёт ККМ в базе данных Superkassa.
            - Полученный заводской номер нужно использовать в последующем запросе инициализации ККМ.
            
            Метод является публичным и не требует авторизации.
        """
    )
    @KkmApiResponses(
        ok = MSG_200_KKM_INIT,
        badRequest = MSG_400_VALIDATION,
        forbidden = MSG_403_FORBIDDEN
    )
    fun generateFactoryInfo(): FactoryNumberResponse {
        // PIN не требуется, метод не привязан к конкретной ККМ
        return kkmService.generateFactoryInfo()
    }

    /**
     * Удалить ККМ (требуется режим программирования, закрытая смена и права администратора).
     */
    @DeleteMapping("/{kkmId}")
    @Operation(
        operationId = "05_deleteKkm",
        summary = "Удалить ККМ",
        description = """
            Удаляет ККМ из системы полностью.
            
            Требования:
            - ККМ должна быть в режиме программирования (POST /kkm/{kkmId}/programming/enter)
            - Смена должна быть закрыта (нет открытой смены)
            - Очередь команд должна быть пуста
            - Требуются права администратора
            
            Операция необратима. После удаления все данные ККМ (настройки, пользователи, счетчики) 
            будут удалены из системы.
        """
    )
    @KkmApiResponses(
        ok = MSG_200_KKM_DELETED,
        badRequest = MSG_400_BAD_STATUS_OR_SHIFT,
        forbidden = MSG_403_FORBIDDEN,
        notFound = MSG_404_KKM_NOT_FOUND,
        conflict = MSG_409_DELETE_BLOCKED
    )
    fun deleteKkm(
        @PathVariable kkmId: String,
        @RequestHeader("Authorization") authHeader: String?
    ): Map<String, Boolean> {
        val pin = AuthHeaderUtils.extractPin(authHeader)
        return mapOf("ok" to kkmService.deleteKkm(kkmId, pin))
    }
}
