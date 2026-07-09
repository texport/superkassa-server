package io.github.texport.superkassa.jvm.shared.strings.impl

import io.github.texport.superkassa.jvm.shared.strings.api.ErrorKey
import io.github.texport.superkassa.jvm.shared.strings.api.ErrorResolver
import io.github.texport.superkassa.jvm.shared.strings.api.TrilingualString
import io.github.texport.superkassa.jvm.shared.strings.api.key.DeliveryErrorKey
import io.github.texport.superkassa.jvm.shared.strings.api.key.DeliveryTemplateKey
import io.github.texport.superkassa.jvm.shared.strings.api.key.SettingsErrorKey
import io.github.texport.superkassa.jvm.shared.strings.api.key.StorageErrorKey
import io.github.texport.superkassa.jvm.shared.strings.api.key.TimeDebugKey
import io.github.texport.superkassa.jvm.shared.strings.api.key.TimeErrorKey
import io.github.texport.superkassa.jvm.shared.strings.api.key.WebErrorKey

class DefaultErrorResolver : ErrorResolver {

    private val translations: Map<ErrorKey, TrilingualString> = mapOf(
        SettingsErrorKey.SQLITE_NOT_ALLOWED to TrilingualString(
            en = "SQLite database is not allowed in server mode. Use PostgreSQL or MySQL.",
            ru = "Использование базы данных SQLite запрещено в режиме сервера. Используйте PostgreSQL или MySQL.",
            kk = "Сервер режимінде SQLite деректер қорының пайдаланушы қолдануға тыйым салынады. " +
                "PostgreSQL немесе MySQL пайдаланыңыз."
        ),
        SettingsErrorKey.SERVER_MODE_ONLY to TrilingualString(
            en = "Database settings repository can only be used in server mode.",
            ru = "Репозиторий настроек базы данных может использоваться только в режиме сервера.",
            kk = "Деректер қорының параметрлері қоймасын тек сервер режимінде пайдалануға болады."
        ),
        SettingsErrorKey.OFD_PROTOCOL_VERSION to TrilingualString(
            en = "OFD protocol version must be a non-empty numeric string.",
            ru = "Версия протокола ОФД должна быть непустой числовой строкой.",
            kk = "СЖД (ОФД) хаттамасының нұсқасы бос емес сандық жол болуы керек."
        ),
        SettingsErrorKey.OFD_TIMEOUT to TrilingualString(
            en = "OFD timeout must be at least 5 seconds.",
            ru = "Таймаут ОФД должен быть не менее 5 секунд.",
            kk = "СЖД (ОФД) күту уақыты кемінде 5 секунд болуы керек."
        ),
        SettingsErrorKey.OFD_RECONNECT_INTERVAL to TrilingualString(
            en = "OFD reconnect interval must be at least 60 seconds.",
            ru = "Интервал переподключения ОФД должен быть не менее 60 секунд.",
            kk = "СЖД (ОФД) қайта қосылу аралығы кемінде 60 секунд болуы керек."
        ),
        SettingsErrorKey.STORAGE_ENGINE_BLANK to TrilingualString(
            en = "Storage engine cannot be blank.",
            ru = "Движок хранилища не может быть пустым.",
            kk = "Қойма движогі бос болмауы керек."
        ),
        SettingsErrorKey.STORAGE_ENGINE_INVALID to TrilingualString(
            en = "Storage engine must be one of: SQLITE, POSTGRESQL, MYSQL.",
            ru = "Движок хранилища должен быть одним из: SQLITE, POSTGRESQL, MYSQL.",
            kk = "Қойма движогі келесілердің бірі болуы керек: SQLITE, POSTGRESQL, MYSQL."
        ),
        SettingsErrorKey.JDBC_URL_BLANK to TrilingualString(
            en = "Storage JDBC URL cannot be blank.",
            ru = "JDBC URL хранилища не может быть пустым.",
            kk = "Қойманың JDBC URL мекенжайы бос болмауы керек."
        ),
        SettingsErrorKey.JDBC_URL_INVALID_SCHEME to TrilingualString(
            en = "Storage JDBC URL must start with 'jdbc:'.",
            ru = "JDBC URL хранилища должен начинаться с 'jdbc:'.",
            kk = "Қойманың JDBC URL мекенжайы 'jdbc:'-тен басталуы тиіс."
        ),
        SettingsErrorKey.NODE_ID_BLANK to TrilingualString(
            en = "Node ID cannot be blank in server mode.",
            ru = "Идентификатор узла не может быть пустым в режиме сервера.",
            kk = "Сервер режимінде торап идентификаторы (Node ID) бос болмауы керек."
        ),
        SettingsErrorKey.DATABASE_USER_BLANK to TrilingualString(
            en = "Database user cannot be blank in server mode.",
            ru = "Имя пользователя базы данных не может быть пустым в режиме сервера.",
            kk = "Сервер режимінде деректер қорының пайдаланушы аты бос болмауы керек."
        ),
        SettingsErrorKey.DATABASE_PASSWORD_BLANK to TrilingualString(
            en = "Database password cannot be blank in server mode.",
            ru = "Пароль базы данных не может быть пустым в режиме сервера.",
            kk = "Сервер режимінде деректер қорының құпия сөзі бос болмауы керек."
        ),
        SettingsErrorKey.OFD_PROVIDER_KEY_BLANK to TrilingualString(
            en = "OFD provider key cannot be blank.",
            ru = "Ключ провайдера ОФД не может быть пустым.",
            kk = "СЖД (ОФД) провайдерінің кілті бос болмауы керек."
        ),
        SettingsErrorKey.CHANNEL_NAME_BLANK to TrilingualString(
            en = "Channel name cannot be blank.",
            ru = "Имя канала не может быть пустым.",
            kk = "Арна атауы бос болмауы керек."
        ),
        SettingsErrorKey.UNKNOWN_DELIVERY_CHANNEL to TrilingualString(
            en = "Unknown delivery channel: {0}. Supported: PRINT, EMAIL, SMS, TELEGRAM, WHATSAPP.",
            ru = "Неизвестный канал доставки: {0}. Поддерживаются: PRINT, EMAIL, SMS, TELEGRAM, WHATSAPP.",
            kk = "Белгісіз жеткізу арнасы: {0}. Келесілерге қолдау көрсетіледі: PRINT, EMAIL, SMS, TELEGRAM, WHATSAPP."
        ),
        SettingsErrorKey.PAYLOAD_TYPE_BLANK to TrilingualString(
            en = "Payload type cannot be blank for channel {0}.",
            ru = "Тип полезной нагрузки не может быть пустым для канала {0}.",
            kk = "Пайдалы жүктеме түрі {0} арнасы үшін бос болмауы керек."
        ),
        SettingsErrorKey.DOCUMENT_FORMAT_BLANK to TrilingualString(
            en = "Document format cannot be blank for channel {0}.",
            ru = "Формат документа не может быть пустым для канала {0}.",
            kk = "Құжат форматы {0} арнасы үшін бос болмауы керек."
        ),
        SettingsErrorKey.DESTINATION_BLANK to TrilingualString(
            en = "Destination cannot be blank for channel {0}.",
            ru = "Назначение доставки не может быть пустым для канала {0}.",
            kk = "Жеткізу орны {0} арнасы үшін бос болмауы керек."
        ),
        SettingsErrorKey.EMAIL_DESTINATION_INVALID to TrilingualString(
            en = "Destination for EMAIL channel must be a valid email address.",
            ru = "Назначение для канала EMAIL должно быть корректным адресом электронной почты.",
            kk = "EMAIL арнасына арналған мекенжай дұрыс электрондық пошта болуы керек."
        ),
        SettingsErrorKey.PHONE_DESTINATION_INVALID to TrilingualString(
            en = "Destination for {0} channel must be a valid phone number.",
            ru = "Назначение для канала {0} должно быть корректным номером телефона.",
            kk = "{0} арнасына арналған мекенжай дұрыс телефон нөмірі болуы керек."
        ),
        SettingsErrorKey.TELEGRAM_DESTINATION_INVALID to TrilingualString(
            en = "Destination for TELEGRAM channel must be a numeric chat ID.",
            ru = "Назначение для канала TELEGRAM должно быть числовым идентификатором чата.",
            kk = "TELEGRAM арнасына арналған мекенжай сандық чат идентификаторы болуы керек."
        ),
        SettingsErrorKey.PRINT_CONFIG_MISSING to TrilingualString(
            en = "Print config is missing.",
            ru = "Конфигурация печати отсутствует.",
            kk = "Басып шығару конфигурациясы жоқ."
        ),
        SettingsErrorKey.PRINT_PAPER_WIDTH_INVALID to TrilingualString(
            en = "Print channel paper width must be positive.",
            ru = "Ширина бумаги канала печати должна быть положительной.",
            kk = "Басып шығару арнасының қағаз ені оң сан болуы керек."
        ),
        SettingsErrorKey.PRINT_HOST_INVALID to TrilingualString(
            en = "Print channel host is invalid.",
            ru = "Хост канала печати некорректен.",
            kk = "Басып шығару арнасының хосты қате."
        ),
        SettingsErrorKey.PRINT_PORT_INVALID to TrilingualString(
            en = "Print channel port must be in range 1..65535.",
            ru = "Порт канала печати должен быть в диапазоне 1..65535.",
            kk = "Басып шығару арнасының порты 1..65535 ауқымында болуы керек."
        ),
        SettingsErrorKey.EMAIL_CONFIG_MISSING to TrilingualString(
            en = "Email config is missing.",
            ru = "Конфигурация почты отсутствует.",
            kk = "Электрондық пошта конфигурациясы жоқ."
        ),
        SettingsErrorKey.EMAIL_PARAMS_INVALID to TrilingualString(
            en = "Email host, from, user or password cannot be blank. " +
                "Port must be in range 1..65535. From must be a valid email.",
            ru = "Хост, отправитель, пользователь или пароль почты не могут быть пустыми. " +
                "Порт должен быть в диапазоне 1..65535. Отправитель должен быть корректным email.",
            kk = "Электрондық пошта хосты, жіберуші, пайдаланушы немесе құпия сөз бос болмауы керек. " +
                "Порт 1..65535 ауқымында болуы керек. Жіберуші дұрыс электрондық пошта болуы тиіс."
        ),
        SettingsErrorKey.SMS_CONFIG_MISSING to TrilingualString(
            en = "SMS config is missing.",
            ru = "Конфигурация SMS отсутствует.",
            kk = "SMS конфигурациясы жоқ."
        ),
        SettingsErrorKey.SMS_PROVIDER_URL_INVALID to TrilingualString(
            en = "SMS provider URL must start with http:// or https://.",
            ru = "URL провайдера SMS должен начинаться с http:// или https://.",
            kk = "SMS провайдерінің URL мекенжайы http:// немесе https://-тен басталуы керек."
        ),
        SettingsErrorKey.SMS_API_KEY_BLANK to TrilingualString(
            en = "SMS apiKey cannot be blank.",
            ru = "API-ключ SMS не может быть пустым.",
            kk = "SMS API кілті бос болмауы керек."
        ),
        SettingsErrorKey.TELEGRAM_CONFIG_MISSING to TrilingualString(
            en = "Telegram config is missing.",
            ru = "Конфигурация Telegram отсутствует.",
            kk = "Telegram конфигурациясы жоқ."
        ),
        SettingsErrorKey.TELEGRAM_BOT_TOKEN_INVALID to TrilingualString(
            en = "Telegram botToken is invalid (must be in format 'botID:botSecret').",
            ru = "Токен бота Telegram некорректен (должен быть в формате 'botID:botSecret').",
            kk = "Telegram бот токені қате ('botID:botSecret' форматында болуы керек)."
        ),
        SettingsErrorKey.WHATSAPP_CONFIG_MISSING to TrilingualString(
            en = "WhatsApp config is missing.",
            ru = "Конфигурация WhatsApp отсутствует.",
            kk = "WhatsApp конфигурациясы жоқ."
        ),
        SettingsErrorKey.WHATSAPP_ACCESS_TOKEN_BLANK to TrilingualString(
            en = "WhatsApp accessToken cannot be blank.",
            ru = "Токен WhatsApp не может быть пустым.",
            kk = "WhatsApp токені бос болмауы керек."
        ),
        SettingsErrorKey.WHATSAPP_PHONE_ID_INVALID to TrilingualString(
            en = "WhatsApp phoneNumberId must consist only of digits.",
            ru = "Идентификатор телефона WhatsApp должен состоять только из цифр.",
            kk = "WhatsApp телефон идентификаторы тек сандардан тұруы керек."
        ),
        SettingsErrorKey.SETTINGS_FROZEN_SERVER_MODE to TrilingualString(
            en = "Settings cannot be modified via API in SERVER mode.",
            ru = "Настройки не могут быть изменены через API в режиме SERVER.",
            kk = "Параметрлерді SERVER режимінде API арқылы өзгерту мүмкін емес."
        ),
        SettingsErrorKey.SETTINGS_FROZEN_DISALLOWED to TrilingualString(
            en = "Settings changes are frozen. Allow changes in configuration file first.",
            ru = "Изменение настроек заморожено. Разрешите изменения в файле конфигурации.",
            kk = "Параметрлерді өзгерту бұғатталған. Алдымен конфигурация файлында рұқсат етіңіз."
        ),
        // Delivery Translations
        DeliveryErrorKey.SMS_DESTINATION_REQUIRED to TrilingualString(
            en = "SMS destination (phone) required",
            ru = "Требуется номер телефона получателя SMS",
            kk = "SMS алушының телефон нөмірі қажет"
        ),
        DeliveryErrorKey.SMS_PROVIDER_NOT_CONFIGURED to TrilingualString(
            en = "SMS provider URL not configured",
            ru = "Не настроен URL-адрес провайдера SMS",
            kk = "SMS-провайдердің URL-мекенжайы бапталмаған"
        ),
        DeliveryErrorKey.EMAIL_DESTINATION_REQUIRED to TrilingualString(
            en = "Email destination required",
            ru = "Требуется адрес электронной почты",
            kk = "Электрондық пошта мекенжайы қажет"
        ),
        DeliveryErrorKey.EMAIL_PAYLOAD_MISSING to TrilingualString(
            en = "No payload",
            ru = "Отсутствуют данные чека",
            kk = "Чек деректері жоқ"
        ),
        DeliveryErrorKey.EMAIL_SEND_FAILED to TrilingualString(
            en = "Email sending failed: {0}",
            ru = "Ошибка отправки почты: {0}",
            kk = "Пошта жіберу қатесі: {0}"
        ),
        DeliveryErrorKey.PRINTER_NOT_FOUND to TrilingualString(
            en = "Printer '{0}' not found in the OS",
            ru = "Принтер '{0}' не найден в операционной системе",
            kk = "'{0}' принтері операциялық жүйеде табылмады"
        ),
        DeliveryErrorKey.PRINT_PAYLOAD_MISSING to TrilingualString(
            en = "No print payload",
            ru = "Отсутствуют данные для печати",
            kk = "Басып шығаруға арналған деректер жоқ"
        ),
        DeliveryErrorKey.PRINT_FAILED to TrilingualString(
            en = "Print failed: {0}",
            ru = "Ошибка печати: {0}",
            kk = "Басып шығару қатесі: {0}"
        ),
        DeliveryErrorKey.LOCAL_PRINT_FAILED to TrilingualString(
            en = "OS local print failed: {0}",
            ru = "Ошибка локальной печати ОС: {0}",
            kk = "Операциялық жүйенің жергілікті басып шығару қатесі: {0}"
        ),
        DeliveryErrorKey.TELEGRAM_CHAT_ID_REQUIRED to TrilingualString(
            en = "Telegram chat_id required",
            ru = "Требуется ID чата Telegram",
            kk = "Telegram чат идентификаторы қажет"
        ),
        DeliveryErrorKey.WHATSAPP_PHONE_REQUIRED to TrilingualString(
            en = "WhatsApp phone number required",
            ru = "Требуется номер телефона WhatsApp",
            kk = "WhatsApp телефон нөмірі қажет"
        ),
        DeliveryErrorKey.HTTP_DELIVERY_FAILED to TrilingualString(
            en = "Delivery via {0} failed with status {1}. Response: {2}",
            ru = "Доставка через {0} завершилась ошибкой с кодом {1}. Ответ: {2}",
            kk = "{0} арқылы жеткізу {1} кодымен қате аяқталды. Жауап: {2}"
        ),
        DeliveryErrorKey.HTTP_DELIVERY_ERROR to TrilingualString(
            en = "Delivery via {0} failed: {1}",
            ru = "Ошибка доставки через {0}: {1}",
            kk = "{0} арқылы жеткізу қатесі: {1}"
        ),
        // Time Translations
        TimeErrorKey.TIME_OUT_OF_RANGE to TrilingualString(
            en = "System time is out of allowed range (2020-2100)",
            ru = "Системное время вне допустимого диапазона (2020-2100)",
            kk = "Жүйелік уақыт рұқсат етілген ауқымнан тыс (2020-2100)"
        ),
        TimeErrorKey.TIME_MONOTONIC_SKEW to TrilingualString(
            en = "Monotonic clock skew detected (system clock adjusted)",
            ru = "Обнаружен сбой монотонного времени (перевод стрелок)",
            kk = "Монотонды уақыттың ауытқуы анықталды (сағат тілі ауыстырылды)"
        ),
        TimeErrorKey.TIME_REFERENCE_SKEW to TrilingualString(
            en = "Time is desynchronized from reference source",
            ru = "Время рассинхронизировано с эталонным",
            kk = "Уақыт эталонды уақытпен синхрондалмаған"
        ),
        // Storage Translations
        StorageErrorKey.DATABASE_ERROR to TrilingualString(
            en = "Database error: {0}",
            ru = "Ошибка базы данных: {0}",
            kk = "Деректер қорының қатесі: {0}"
        ),
        StorageErrorKey.USER_ROLE_INVALID to TrilingualString(
            en = "Invalid user role in database: {0}",
            ru = "Неверная роль пользователя в БД: {0}",
            kk = "Деректер қорындағы пайдаланушының қате рөлі: {0}"
        ),
        StorageErrorKey.SHIFT_STATUS_INVALID to TrilingualString(
            en = "Invalid shift status in database: {0}",
            ru = "Неверный статус смены в БД: {0}",
            kk = "Деректер қорындағы ауысымның қате статусы: {0}"
        ),
        StorageErrorKey.INVALID_BASE_64_FORMAT to TrilingualString(
            en = "Invalid Base64 format in database",
            ru = "Неверный формат Base64 в БД",
            kk = "Деректер қорындағы Base64 қате форматы"
        ),
        StorageErrorKey.INVALID_TAX_REGIME to TrilingualString(
            en = "Invalid tax regime in database: {0}",
            ru = "Неверный режим налогообложения в БД: {0}",
            kk = "Деректер қорындағы қате салық режимі: {0}"
        ),
        StorageErrorKey.INVALID_VAT_GROUP to TrilingualString(
            en = "Invalid VAT group in database: {0}",
            ru = "Неверная группа НДС в БД: {0}",
            kk = "Деректер қорындағы қате ҚҚС тобы: {0}"
        ),
        // Time Debug Logs (English only)
        TimeDebugKey.CLOCK_SKEW_DETECTED to TrilingualString(
            en = "Monotonic clock skew detected (system clock adjusted): skew={0} ms",
            ru = "Monotonic clock skew detected (system clock adjusted): skew={0} ms",
            kk = "Monotonic clock skew detected (system clock adjusted): skew={0} ms"
        ),
        TimeDebugKey.REFERENCE_NOT_HTTP to TrilingualString(
            en = "Reference time source is not an HTTP URL: {0}",
            ru = "Reference time source is not an HTTP URL: {0}",
            kk = "Reference time source is not an HTTP URL: {0}"
        ),
        TimeDebugKey.INVALID_REFERENCE_URL to TrilingualString(
            en = "Invalid reference time URL: {0}",
            ru = "Invalid reference time URL: {0}",
            kk = "Invalid reference time URL: {0}"
        ),
        TimeDebugKey.FAILED_TO_FETCH_REFERENCE to TrilingualString(
            en = "Failed to fetch reference time from {0}",
            ru = "Failed to fetch reference time from {0}",
            kk = "Failed to fetch reference time from {0}"
        ),
        TimeDebugKey.FAILED_TO_PARSE_DATE to TrilingualString(
            en = "Failed to parse Date header of reference time from {0}",
            ru = "Failed to parse Date header of reference time from {0}",
            kk = "Failed to parse Date header of reference time from {0}"
        ),
        // Delivery Templates
        DeliveryTemplateKey.EMAIL_SUBJECT to TrilingualString(
            en = "Receipt {0}",
            ru = "Чек {0}",
            kk = "Чек {0}"
        ),
        DeliveryTemplateKey.EMAIL_BODY_URL to TrilingualString(
            en = "Link to receipt: {0}",
            ru = "Ссылка на чек: {0}",
            kk = "Чекке сілтеме: {0}"
        ),
        DeliveryTemplateKey.EMAIL_BODY_ATTACHMENT to TrilingualString(
            en = "Receipt is in attachment.",
            ru = "Чек во вложении.",
            kk = "Чек қосымшада."
        ),
        DeliveryTemplateKey.SMS_BODY_URL to TrilingualString(
            en = "Receipt: {0}",
            ru = "Чек: {0}",
            kk = "Чек: {0}"
        ),
        DeliveryTemplateKey.SMS_BODY_READY to TrilingualString(
            en = "Receipt {0} is ready",
            ru = "Чек {0} готов",
            kk = "{0} чегі дайын"
        ),
        WebErrorKey.METHOD_NOT_ALLOWED to TrilingualString(
            en = "Request method not supported: {0}",
            ru = "Метод запроса не поддерживается: {0}",
            kk = "Сұраныс әдісіне қолдау көрсетілмейді: {0}"
        ),
        WebErrorKey.MISSING_PARAMETER to TrilingualString(
            en = "Missing required parameter: {0}",
            ru = "Отсутствует обязательный параметр: {0}",
            kk = "Міндетті параметр жетіспейді: {0}"
        ),
        WebErrorKey.UNSUPPORTED_MEDIA_TYPE to TrilingualString(
            en = "Media type not supported",
            ru = "Неподдерживаемый тип содержимого",
            kk = "Қолдау көрсетілмейтін мазмұн түрі"
        ),
        WebErrorKey.RESOURCE_NOT_FOUND to TrilingualString(
            en = "Requested resource was not found",
            ru = "Запрошенный ресурс не найден",
            kk = "Сұралған ресурс табылмады"
        ),
        WebErrorKey.INTERNAL_ERROR to TrilingualString(
            en = "Internal server error",
            ru = "Внутренняя ошибка сервера",
            kk = "Сервердің ішкі қатесі"
        ),
        WebErrorKey.CRITICAL_ERROR to TrilingualString(
            en = "Critical system error occurred",
            ru = "Произошла критическая системная ошибка",
            kk = "Критикалық жүйелік қате орын алды"
        )
    )

    override fun resolve(key: ErrorKey): TrilingualString {
        return translations[key] ?: TrilingualString("Unknown error", "Неизвестная ошибка", "Белгісіз қате")
    }
}
