package io.github.texport.superkassa.jvm.settings.api

/**
 * Исключение, выбрасываемое при обнаружении некорректной конфигурации сервера в процессе валидации настроек.
 * Обычно указывает на некорректную настройку базы данных, параметров ОФД или каналов доставки чеков.
 *
 * @param message Локализованное сообщение об ошибке (содержит трехъязычные переводы).
 */
class IllegalServerConfigurationException(message: String) : RuntimeException(message)
