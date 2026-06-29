package io.github.texport.superkassa.jvm.settings.validation

internal object ValidationErrors {

    val SQLITE_NOT_ALLOWED_ERROR = errorMsg(
        "SQLite database is not allowed in server mode. Use PostgreSQL or MySQL.",
        "Использование базы данных SQLite запрещено в режиме сервера. Используйте PostgreSQL или MySQL.",
        "Сервер режимінде SQLite деректер қорының пайдаланушы қолдануға тыйым салынады. PostgreSQL немесе MySQL пайдаланыңыз."
    )

    val SERVER_MODE_ONLY_ERROR = errorMsg(
        "Database settings repository can only be used in server mode.",
        "Репозиторий настроек базы данных может использоваться только в режиме сервера.",
        "Деректер қорының параметрлері қоймасын тек сервер режимінде пайдалануға болады."
    )

    val OFD_PROTOCOL_VERSION_ERROR = errorMsg(
        "OFD protocol version must be a non-empty numeric string.",
        "Версия протокола ОФД должна быть непустой числовой строкой.",
        "СЖД (ОФД) хаттамасының нұсқасы бос емес сандық жол болуы керек."
    )

    val OFD_TIMEOUT_ERROR = errorMsg(
        "OFD timeout must be at least 5 seconds.",
        "Таймаут ОФД должен быть не менее 5 секунд.",
        "СЖД (ОФД) күту уақыты кемінде 5 секунд болуы керек."
    )

    val OFD_RECONNECT_INTERVAL_ERROR = errorMsg(
        "OFD reconnect interval must be at least 60 seconds.",
        "Интервал переподключения ОФД должен быть не менее 60 секунд.",
        "СЖД (ОФД) қайта қосылу аралығы кемінде 60 секунд болуы керек."
    )

    val STORAGE_ENGINE_BLANK = errorMsg(
        "Storage engine cannot be blank.",
        "Движок хранилища не может быть пустым.",
        "Қойма движогі бос болмауы керек."
    )

    val STORAGE_ENGINE_INVALID = errorMsg(
        "Storage engine must be one of: SQLITE, POSTGRESQL, MYSQL.",
        "Движок хранилища должен быть одним из: SQLITE, POSTGRESQL, MYSQL.",
        "Қойма движогі келесілердің бірі болуы керек: SQLITE, POSTGRESQL, MYSQL."
    )

    val JDBC_URL_BLANK = errorMsg(
        "Storage JDBC URL cannot be blank.",
        "JDBC URL хранилища не может быть пустым.",
        "Қойманың JDBC URL мекенжайы бос болмауы керек."
    )

    val JDBC_URL_INVALID_SCHEME = errorMsg(
        "Storage JDBC URL must start with 'jdbc:'.",
        "JDBC URL хранилища должен начинаться с 'jdbc:'.",
        "Қойманың JDBC URL мекенжайы 'jdbc:'-тен басталуы тиіс."
    )

    val NODE_ID_BLANK = errorMsg(
        "Node ID cannot be blank in server mode.",
        "Идентификатор узла не может быть пустым в режиме сервера.",
        "Сервер режимінде торап идентификаторы (Node ID) бос болмауы керек."
    )

    val DATABASE_USER_BLANK = errorMsg(
        "Database user cannot be blank in server mode.",
        "Имя пользователя базы данных не может быть пустым в режиме сервера.",
        "Сервер режимінде деректер қорының пайдаланушы аты бос болмауы керек."
    )

    val DATABASE_PASSWORD_BLANK = errorMsg(
        "Database password cannot be blank in server mode.",
        "Пароль базы данных не может быть пустым в режиме сервера.",
        "Сервер режимінде деректер қорының құпия сөзі бос болмауы керек."
    )

    val OFD_PROVIDER_KEY_BLANK = errorMsg(
        "OFD provider key cannot be blank.",
        "Ключ провайдера ОФД не может быть пустым.",
        "СЖД (ОФД) провайдерінің кілті бос болмауы керек."
    )

    val CHANNEL_NAME_BLANK = errorMsg(
        "Channel name cannot be blank.",
        "Имя канала не может быть пустым.",
        "Арна атауы бос болмауы керек."
    )

    fun unknownChannel(channel: String): String = errorMsg(
        "Unknown delivery channel: $channel. Supported: PRINT, EMAIL, SMS, TELEGRAM, WHATSAPP.",
        "Неизвестный канал доставки: $channel. Поддерживаются: PRINT, EMAIL, SMS, TELEGRAM, WHATSAPP.",
        "Белгісіз жеткізу арнасы: $channel. Келесілерге қолдау көрсетіледі: PRINT, EMAIL, SMS, TELEGRAM, WHATSAPP."
    )

    fun payloadTypeBlank(channel: String): String = errorMsg(
        "Payload type cannot be blank for channel $channel.",
        "Тип полезной нагрузки не может быть пустым для канала $channel.",
        "Пайдалы жүктеме түрі $channel арнасы үшін бос болмауы керек."
    )

    fun documentFormatBlank(channel: String): String = errorMsg(
        "Document format cannot be blank for channel $channel.",
        "Формат документа не может быть пустым для канала $channel.",
        "Құжат форматы $channel арнасы үшін бос болмауы керек."
    )

    fun destinationBlank(channel: String): String = errorMsg(
        "Destination cannot be blank for channel $channel.",
        "Назначение доставки не может быть пустым для канала $channel.",
        "Жеткізу орны $channel арнасы үшін бос болмауы керек."
    )

    val EMAIL_DESTINATION_INVALID = errorMsg(
        "Destination for EMAIL channel must be a valid email address.",
        "Назначение для канала EMAIL должно быть корректным адресом электронной почты.",
        "EMAIL арнасына арналған мекенжай дұрыс электрондық пошта болуы керек."
    )

    fun phoneDestinationInvalid(channel: String): String = errorMsg(
        "Destination for $channel channel must be a valid phone number.",
        "Назначение для канала $channel должно быть корректным номером телефона.",
        "$channel арнасына арналған мекенжай дұрыс телефон нөмірі болуы керек."
    )

    val TELEGRAM_DESTINATION_INVALID = errorMsg(
        "Destination for TELEGRAM channel must be a numeric chat ID.",
        "Назначение для канала TELEGRAM должно быть числовым идентификатором чата.",
        "TELEGRAM арнасына арналған мекенжай сандық чат идентификаторы болуы керек."
    )

    val PRINT_CONFIG_MISSING = errorMsg(
        "Print config is missing.",
        "Конфигурация печати отсутствует.",
        "Басып шығару конфигурациясы жоқ."
    )

    val PRINT_PAPER_WIDTH_INVALID = errorMsg(
        "Print channel paper width must be positive.",
        "Ширина бумаги канала печати должна быть положительной.",
        "Басып шығару арнасының қағаз ені оң сан болуы керек."
    )

    val PRINT_HOST_INVALID = errorMsg(
        "Print channel host is invalid.",
        "Хост канала печати некорректен.",
        "Басып шығару арнасының хосты қате."
    )

    val PRINT_PORT_INVALID = errorMsg(
        "Print channel port must be in range 1..65535.",
        "Порт канала печати должен быть в диапазоне 1..65535.",
        "Басып шығару арнасының порты 1..65535 ауқымында болуы керек."
    )

    val EMAIL_CONFIG_MISSING = errorMsg(
        "Email config is missing.",
        "Конфигурация почты отсутствует.",
        "Электрондық пошта конфигурациясы жоқ."
    )

    val EMAIL_PARAMS_INVALID = errorMsg(
        "Email host, from, user or password cannot be blank. Port must be in range 1..65535. From must be a valid email.",
        "Хост, отправитель, пользователь или пароль почты не могут быть пустыми. Порт должен быть в диапазоне 1..65535. Отправитель должен быть корректным email.",
        "Электрондық пошта хосты, жіберуші, пайдаланушы немесе құпия сөз бос болмауы керек. Порт 1..65535 ауқымында болуы керек. Жіберуші дұрыс электрондық пошта болуы тиіс."
    )

    val SMS_CONFIG_MISSING = errorMsg(
        "SMS config is missing.",
        "Конфигурация SMS отсутствует.",
        "SMS конфигурациясы жоқ."
    )

    val SMS_PROVIDER_URL_INVALID = errorMsg(
        "SMS provider URL must start with http:// or https://.",
        "URL провайдера SMS должен начинаться с http:// или https://.",
        "SMS провайдерінің URL мекенжайы http:// немесе https://-тен басталуы керек."
    )

    val SMS_API_KEY_BLANK = errorMsg(
        "SMS apiKey cannot be blank.",
        "API-ключ SMS не может быть пустым.",
        "SMS API кілті бос болмауы керек."
    )

    val TELEGRAM_CONFIG_MISSING = errorMsg(
        "Telegram config is missing.",
        "Конфигурация Telegram отсутствует.",
        "Telegram конфигурациясы жоқ."
    )

    val TELEGRAM_BOT_TOKEN_INVALID = errorMsg(
        "Telegram botToken is invalid (must be in format 'botID:botSecret').",
        "Токен бота Telegram некорректен (должен быть в формате 'botID:botSecret').",
        "Telegram бот токені қате ('botID:botSecret' форматында болуы керек)."
    )

    val WHATSAPP_CONFIG_MISSING = errorMsg(
        "WhatsApp config is missing.",
        "Конфигурация WhatsApp отсутствует.",
        "WhatsApp конфигурациясы жоқ."
    )

    val WHATSAPP_ACCESS_TOKEN_BLANK = errorMsg(
        "WhatsApp accessToken cannot be blank.",
        "Токен WhatsApp не может быть пустым.",
        "WhatsApp токені бос болмауы керек."
    )

    val WHATSAPP_PHONE_ID_INVALID = errorMsg(
        "WhatsApp phoneNumberId must consist only of digits.",
        "Идентификатор телефона WhatsApp должен состоять только из цифр.",
        "WhatsApp телефон идентификаторы тек сандардан тұруы керек."
    )
}
