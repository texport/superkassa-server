package kz.mybrain.superkassa.core.application.http.controllers

import io.github.texport.superkassa.core.presentation.api.SuperkassaApi
import io.github.texport.superkassa.core.presentation.api.model.common.VatRateResponse
import io.github.texport.superkassa.core.presentation.api.model.reference.AuthModeResponse
import io.github.texport.superkassa.core.presentation.api.model.reference.BrandingColorResponse
import io.github.texport.superkassa.core.presentation.api.model.reference.CashOperationTypeResponse
import io.github.texport.superkassa.core.presentation.api.model.reference.CoreModeResponse
import io.github.texport.superkassa.core.presentation.api.model.reference.DeliveryStatusResponse
import io.github.texport.superkassa.core.presentation.api.model.reference.DocumentTypeResponse
import io.github.texport.superkassa.core.presentation.api.model.reference.KkmModeResponse
import io.github.texport.superkassa.core.presentation.api.model.reference.KkmStateResponse
import io.github.texport.superkassa.core.presentation.api.model.reference.OfdCommandStatusResponse
import io.github.texport.superkassa.core.presentation.api.model.reference.OfdCommandTypeResponse
import io.github.texport.superkassa.core.presentation.api.model.reference.OfdEnvironmentResponse
import io.github.texport.superkassa.core.presentation.api.model.reference.OfdProviderResponse
import io.github.texport.superkassa.core.presentation.api.model.reference.PaperWidthResponse
import io.github.texport.superkassa.core.presentation.api.model.reference.PaymentTypeResponse
import io.github.texport.superkassa.core.presentation.api.model.reference.PrintDocumentTypeResponse
import io.github.texport.superkassa.core.presentation.api.model.reference.ReceiptDomainTypeResponse
import io.github.texport.superkassa.core.presentation.api.model.reference.ReceiptLanguageResponse
import io.github.texport.superkassa.core.presentation.api.model.reference.ReceiptLayoutTypeResponse
import io.github.texport.superkassa.core.presentation.api.model.reference.ReceiptOperationTypeResponse
import io.github.texport.superkassa.core.presentation.api.model.reference.ShiftStatusResponse
import io.github.texport.superkassa.core.presentation.api.model.reference.TaxRegimeResponse
import io.github.texport.superkassa.core.presentation.api.model.reference.UserRoleResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import kz.mybrain.superkassa.core.application.http.annotation.KkmApiResponses
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Контроллер для работы с общими справочниками/словарями системы.
 */
@RestController
@RequestMapping("/dictionaries")
@Tag(
    name = "Справочники и Словари",
    description = "Универсальные справочники и списки допустимых значений перечислений (enums)"
)
class DictionariesController(private val kkmService: SuperkassaApi) {

    @GetMapping("/payment-types")
    @Operation(
        summary = "Справочник типов оплат",
        description = """
            Возвращает список поддерживаемых типов оплат для фискальных чеков.
            
            Используется для:
            - Заполнения поля `type` в блоке платежей `payments` при регистрации продажи/возврата.
            
            Допустимые значения:
            - `CASH` — Наличные средства
            - `CARD` — Банковская платежная карта
            - `ELECTRONIC` — Электронные деньги / онлайн-платежи
            - `MOBILE` — Мобильный QR-перевод
            - `CREDIT` — Оплата в кредит (нет в схеме 2.0.4)
            - `TARE` — Оплата тарой (нет в схеме 2.0.4)

            Поле `supported` говорит, принимает ли такой вид оплаты действующая
            версия протокола узла. Недоступный вид оплаты следует показывать
            заблокированным, а не узнавать о запрете отказом пробитого чека.
        """
    )
    @KkmApiResponses(ok = "Справочник типов оплат успешно получен")
    fun getPaymentTypes(): List<PaymentTypeResponse> = kkmService.getPaymentTypes()

    @GetMapping("/vat-groups")
    @Operation(
        summary = "Справочник ставок НДС",
        description = """
            Возвращает налоговые группы НДС, поддерживаемые кассой.

            Тот же состав, что и у `GET /vat-rates`: справочник кассира лежит
            рядом с остальными, отдельного источника значений не заводится.

            Допустимые значения:
            - `NO_VAT` — Без НДС
            - `VAT_0` — НДС 0%
            - `VAT_5` — НДС 5%
            - `VAT_10` — НДС 10%
            - `VAT_16` — НДС 16%
        """
    )
    @KkmApiResponses(ok = "Справочник ставок НДС успешно получен")
    fun getVatGroups(): List<VatRateResponse> = kkmService.listVatRates()

    @GetMapping("/domain-kinds")
    @Operation(
        summary = "Справочник видов отрасли",
        description = """
            Возвращает виды отрасли для отраслевых реквизитов чека (`domain.type`).

            Вид отрасли определяет обязательный подблок реквизитов: услуги
            и гостиницы — `services`, нефтепродукты — `gasOil`, такси — `taxi`,
            стоянки — `parking`. Торговля не требует ни одного.

            Допустимые значения:
            - `DOMAIN_TRADING` — Торговля
            - `DOMAIN_SERVICES` — Сфера услуг
            - `DOMAIN_GASOIL` — Нефтепродукты
            - `DOMAIN_HOTELS` — Гостиницы
            - `DOMAIN_TAXI` — Такси
            - `DOMAIN_PARKING` — Стоянка
        """
    )
    @KkmApiResponses(ok = "Справочник видов отрасли успешно получен")
    fun getDomainKinds(): List<ReceiptDomainTypeResponse> = kkmService.getReceiptDomainTypes()

    @GetMapping("/document-types")
    @Operation(
        summary = "Справочник типов документов",
        description = """
            Возвращает список всех типов документов, генерируемых кассовым аппаратом.
            
            Допустимые значения:
            - `SHIFT_OPEN` — Документ открытия смены
            - `SHIFT_CLOSE` — Документ закрытия смены (Z-отчет)
            - `SALE` — Чек продажи
            - `RETURN` — Чек возврата продажи
            - `BUY` — Чек покупки
            - `BUY_RETURN` — Чек возврата покупки
            - `CASH_IN` — Внесение наличных средств
            - `CASH_OUT` — Изъятие наличных средств
            - `X_REPORT` — Сменный отчет без гашения (X-отчет)
        """
    )
    @KkmApiResponses(ok = "Справочник типов документов успешно получен")
    fun getDocumentTypes(): List<DocumentTypeResponse> = kkmService.getDocumentTypes()

    @GetMapping("/user-roles")
    @Operation(
        summary = "Справочник ролей пользователей",
        description = """
            Возвращает возможные роли пользователей (кассиров/администраторов) ККМ.
            
            Используется для:
            - Управления правами доступа к кассовым операциям.
            
            Допустимые значения:
            - `ADMIN` — Администратор кассы (полный доступ к настройкам, отчетам, закрытию смены)
            - `CASHIER` — Кассир (доступ к продажам, возвратам, внесениям и изъятиям)
        """
    )
    @KkmApiResponses(ok = "Справочник ролей пользователей успешно получен")
    fun getUserRoles(): List<UserRoleResponse> = kkmService.getUserRoles()

    @GetMapping("/tax-regimes")
    @Operation(
        summary = "Справочник налоговых режимов",
        description = """
            Возвращает список поддерживаемых налоговых режимов организации.
            
            Используется при:
            - Фискализации/параметризации ККМ.
            
            Допустимые значения:
            - `NO_VAT` — Субъект не является плательщиком НДС
            - `VAT_PAYER` — Организация является плательщиком НДС
            - `MIXED` — Смешанный режим налогообложения
        """
    )
    @KkmApiResponses(ok = "Справочник налоговых режимов успешно получен")
    fun getTaxRegimes(): List<TaxRegimeResponse> = kkmService.getTaxRegimes()

    @GetMapping("/paper-widths")
    @Operation(
        summary = "Справочник ширины чековой ленты",
        description = """
            Возвращает варианты ширины чековой ленты для шаблонов печати.
            
            Допустимые значения:
            - `58` — Узкая лента 58мм
            - `80` — Стандартная лента 80мм
            - `FULL` — Полноэкранный/мобильный формат отображения
        """
    )
    @KkmApiResponses(ok = "Справочник ширины чековой ленты успешно получен")
    fun getPaperWidths(): List<PaperWidthResponse> = kkmService.getPaperWidths()

    @GetMapping("/branding-colors")
    @Operation(
        summary = "Справочник цветов брендирования",
        description = """
            Возвращает список доступных акцентных цветов для оформления чеков.
            
            Допустимые значения (HEX-коды):
            - `#000000` — Классический черный
            - `#007AFF` — Синий бренд
            - `#34C759` — Зеленый бренд
            - `#FF9500` — Оранжевый бренд
            - `#AF52DE` — Фиолетовый бренд
        """
    )
    @KkmApiResponses(ok = "Справочник цветов брендирования успешно получен")
    fun getBrandingColors(): List<BrandingColorResponse> = kkmService.getBrandingColors()

    @GetMapping("/kkm-states")
    @Operation(
        summary = "Справочник состояний ККМ",
        description = """
            Возвращает возможные состояния кассового аппарата (статусы ядра ККМ).
            
            Допустимые значения:
            - `REGISTRATION` — Касса находится в стадии первоначальной настройки/фискализации
            - `ACTIVE` — Касса активна, смена открыта, готова к выбиванию чеков
            - `BLOCKED` — Касса заблокирована (смена > 24ч или исчерпан лимит автономности)
            - `PROGRAMMING` — Касса находится в режиме программирования/обновления параметров
        """
    )
    @KkmApiResponses(ok = "Справочник состояний ККМ успешно получен")
    fun getKkmStates(): List<KkmStateResponse> = kkmService.getKkmStates()

    @GetMapping("/kkm-modes")
    @Operation(
        summary = "Справочник режимов работы ККМ",
        description = """
            Возвращает возможные режимы подключения ККМ.
            
            Допустимые значения:
            - `ONLINE` — Онлайн-режим с прямой отправкой фискальных документов в ОФД
            - `OFFLINE` — Автономный (офлайн) режим с накоплением документов в очереди
        """
    )
    @KkmApiResponses(ok = "Справочник режимов работы ККМ успешно получен")
    fun getKkmModes(): List<KkmModeResponse> = kkmService.getKkmModes()

    @GetMapping("/shift-statuses")
    @Operation(
        summary = "Справочник статусов смены",
        description = """
            Возвращает возможные состояния кассовой смены.
            
            Допустимые значения:
            - `OPEN` — Кассовая смена открыта
            - `CLOSED` — Кассовая смена закрыта
            
            Смена, открытая дольше суток, состояния не меняет: касса
            перестаёт оформлять кассовые операции и отвечает отказом
            `SHIFT_LONGER_THAN_DAY`, пока смену не закроют.
        """
    )
    @KkmApiResponses(ok = "Справочник статусов смены успешно получен")
    fun getShiftStatuses(): List<ShiftStatusResponse> = kkmService.getShiftStatuses()

    @GetMapping("/delivery-statuses")
    @Operation(
        summary = "Справочник статусов отправки в ОФД",
        description = """
            Возвращает статусы доставки документов в ОФД.
            
            Допустимые значения:
            - `DELIVERED` — Документ успешно передан и принят сервером ОФД
            - `PENDING` — Документ ожидает отправки в очереди
            - `FAILED` — Попытка отправки завершилась ошибкой (будет выполнен повтор)
        """
    )
    @KkmApiResponses(ok = "Справочник статусов отправки успешно получен")
    fun getDeliveryStatuses(): List<DeliveryStatusResponse> = kkmService.getDeliveryStatuses()

    @GetMapping("/ofd-command-statuses")
    @Operation(
        summary = "Справочник статусов выполнения команд ОФД",
        description = """
            Возвращает возможные статусы выполнения фоновых команд взаимодействия с ОФД.
            
            Допустимые значения:
            - `SUCCESS` — Команда успешно выполнена
            - `PENDING` — Команда находится в очереди на выполнение
            - `RUNNING` — Команда выполняется в данный момент
            - `FAILED` — Команда завершилась ошибкой
        """
    )
    @KkmApiResponses(ok = "Справочник статусов выполнения команд успешно получен")
    fun getOfdCommandStatuses(): List<OfdCommandStatusResponse> = kkmService.getOfdCommandStatuses()

    @GetMapping("/receipt-operation-types")
    @Operation(
        summary = "Справочник типов фискальных операций чека",
        description = """
            Возвращает типы фискальных операций чека.
            
            Допустимые значения:
            - `SELL` — Продажа товара/услуги
            - `SELL_RETURN` — Возврат продажи
            - `BUY` — Покупка
            - `BUY_RETURN` — Возврат покупки
        """
    )
    @KkmApiResponses(ok = "Справочник типов фискальных операций успешно получен")
    fun getReceiptOperationTypes(): List<ReceiptOperationTypeResponse> = kkmService.getReceiptOperationTypes()

    @GetMapping("/ofd-environments")
    @Operation(
        summary = "Справочник сред взаимодействия с ОФД",
        description = """
            Возвращает список поддерживаемых сред взаимодействия с серверами ОФД.
            
            Допустимые значения:
            - `DEV` — Среда разработки (эмуляторы)
            - `TEST` — Тестовый сервер ОФД (песочница)
            - `PROD` — Продуктивный фискальный сервер ОФД
        """
    )
    @KkmApiResponses(ok = "Справочник сред взаимодействия с ОФД успешно получен")
    fun getOfdEnvironments(): List<OfdEnvironmentResponse> = kkmService.getOfdEnvironments()

    @GetMapping("/ofd-providers")
    @Operation(
        summary = "Справочник провайдеров ОФД",
        description = """
            Возвращает список провайдеров ОФД с официальными веб-сайтами.
            
            Допустимые значения:
            - `KAZAKHTELECOM` — АО «Казахтелеком» (сайт: oofd.kz)
            - `BFD` — ОФД БФД

            Адрес ОФД задаёт узел по провайдеру и контуру; хост и порт при заведении кассы не передаются.
        """
    )
    @KkmApiResponses(ok = "Справочник провайдеров ОФД успешно получен")
    fun getOfdProviders(): List<OfdProviderResponse> = kkmService.getOfdProviders()

    @GetMapping("/core-modes")
    @Operation(
        summary = "Справочник режимов работы ядра",
        description = """
            Возвращает режимы работы физического или виртуального ядра ККМ.
            
            Допустимые значения:
            - `DESKTOP` — Локальное десктопное приложение/терминал
            - `SERVER` — Облачный кластерный сервер фискализации
        """
    )
    @KkmApiResponses(ok = "Справочник режимов работы ядра успешно получен")
    fun getCoreModes(): List<CoreModeResponse> = kkmService.getCoreModes()

    @GetMapping("/auth-modes")
    @Operation(
        summary = "Справочник режимов авторизации",
        description = """
            Возвращает поддерживаемые режимы авторизации пользователей кассы.
            
            Допустимые значения:
            - `NONE` — Авторизация не требуется (публичный терминал)
            - `BEARER` — Авторизация через JWT-токен в HTTP-заголовке
        """
    )
    @KkmApiResponses(ok = "Справочник режимов авторизации успешно получен")
    fun getAuthModes(): List<AuthModeResponse> = kkmService.getAuthModes()

    @GetMapping("/receipt-languages")
    @Operation(
        summary = "Справочник языков чеков",
        description = """
            Возвращает поддерживаемые языки для печатных макетов чека.
            
            Допустимые значения:
            - `RU` — Только русский язык
            - `KK` — Только казахский язык
            - `MIXED` — Двуязычный чек (русский и казахский одновременно)
        """
    )
    @KkmApiResponses(ok = "Справочник языков чеков успешно получен")
    fun getReceiptLanguages(): List<ReceiptLanguageResponse> = kkmService.getReceiptLanguages()

    @GetMapping("/receipt-layout-types")
    @Operation(
        summary = "Справочник типов макетов чека",
        description = """
            Возвращает форматы рендеринга шаблонов чека.
            
            Допустимые значения:
            - `TAPE_80MM` — Для принтеров с шириной ленты 80мм
            - `TAPE_58MM` — Для принтеров с шириной ленты 58мм
            - `FULLSCREEN` — Полностраничный PDF/A4 макет
        """
    )
    @KkmApiResponses(ok = "Справочник типов макетов чека успешно получен")
    fun getReceiptLayoutTypes(): List<ReceiptLayoutTypeResponse> = kkmService.getReceiptLayoutTypes()

    @GetMapping("/print-document-types")
    @Operation(
        summary = "Справочник типов печатных документов",
        description = """
            Возвращает типы выводимых на печать документов кассы.
            
            Допустимые значения:
            - `DOCUMENT` — Фискальный чек (продажа, возврат)
            - `X_REPORT` — Сменный X-отчет
            - `OPEN_SHIFT` — Квитанция об открытии смены
            - `CLOSE_SHIFT` — Квитанция о закрытии смены (Z-отчет)
        """
    )
    @KkmApiResponses(ok = "Справочник типов печатных документов успешно получен")
    fun getPrintDocumentTypes(): List<PrintDocumentTypeResponse> = kkmService.getPrintDocumentTypes()

    @GetMapping("/ofd-command-types")
    @Operation(
        summary = "Справочник типов команд ОФД",
        description = """
            Возвращает типы отправляемых пакетов данных при интеграции с ОФД.
            
            Допустимые значения:
            - `TICKET` — Команда фискального чека
            - `OPEN_SHIFT` — Команда открытия смены
            - `CLOSE_SHIFT` — Команда закрытия смены
            - `CASH_IN` — Команда внесения наличных
            - `CASH_OUT` — Команда изъятия наличных
            - `GET_COUNTERS` — Запрос счетчиков с ОФД
            - `GET_SERVICE_INFO` — Запрос служебной информации
        """
    )
    @KkmApiResponses(ok = "Справочник типов команд ОФД успешно получен")
    fun getOfdCommandTypes(): List<OfdCommandTypeResponse> = kkmService.getOfdCommandTypes()

    @GetMapping("/cash-operation-types")
    @Operation(
        summary = "Справочник типов операций с наличными",
        description = """
            Возвращает типы нефискальных операций с наличными деньгами в кассе.
            
            Допустимые значения:
            - `CASH_IN` — Внесение разменной наличности в кассу
            - `CASH_OUT` — Изъятие (инкассация) наличности из кассы
        """
    )
    @KkmApiResponses(ok = "Справочник типов операций с наличными успешно получен")
    fun getCashOperationTypes(): List<CashOperationTypeResponse> = kkmService.getCashOperationTypes()
}
