package kz.mybrain.superkassa.core.application.http.utils

import kz.mybrain.superkassa.core.domain.exception.ErrorMessages
import kz.mybrain.superkassa.core.domain.exception.ForbiddenException

/**
 * Утилиты для работы с заголовком Authorization.
 * Поддерживает формат Bearer токена для будущей совместимости с JWT.
 */
object AuthHeaderUtils {
    /**
     * Извлекает PIN из заголовка Authorization.
     *
     * Поддерживает форматы:
     * - "Bearer <pin>" - для будущей совместимости с JWT
     * - "<pin>" - текущий формат (просто PIN без префикса)
     *
     * @param authHeader Значение заголовка Authorization или null
     * @return PIN код
     * @throws ForbiddenException Если заголовок отсутствует или невалиден
     */
    fun extractPin(authHeader: String?): String {
        if (authHeader.isNullOrBlank()) {
            throw ForbiddenException(ErrorMessages.unauthorized())
        }

        val trimmed = authHeader.trim()

        if (trimmed.equals("Bearer", ignoreCase = true)) {
            throw ForbiddenException(ErrorMessages.unauthorized())
        }

        // Поддержка формата "Bearer <pin>"
        if (trimmed.startsWith("Bearer ", ignoreCase = true)) {
            val pin = trimmed.substring(7).trim()
            if (pin.isBlank()) {
                throw ForbiddenException(ErrorMessages.unauthorized())
            }
            return pin
        }

        // Поддержка формата просто "<pin>"
        return trimmed
    }
}
