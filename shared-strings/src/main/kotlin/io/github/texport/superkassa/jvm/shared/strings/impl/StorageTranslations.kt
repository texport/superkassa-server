package io.github.texport.superkassa.jvm.shared.strings.impl

import io.github.texport.superkassa.jvm.shared.strings.api.ErrorKey
import io.github.texport.superkassa.jvm.shared.strings.api.TrilingualString
import io.github.texport.superkassa.jvm.shared.strings.api.key.StorageErrorKey

/**
 * Тексты отказов хранилища.
 *
 * Держатся отдельно от общего перечня: отказов хранилища стало больше,
 * а общий класс переводов и без них подошёл к своему пределу.
 */
internal object StorageTranslations {

    val entries: Map<ErrorKey, TrilingualString> = mapOf(
        StorageErrorKey.DATABASE_ERROR to TrilingualString(
            en = "Storage error. Repeat the operation or contact the node administrator.",
            ru = "Ошибка хранилища. Повторите операцию или обратитесь к администратору узла.",
            kk = "Қойма қатесі. Әрекетті қайталаңыз немесе түйін әкімшісіне хабарласыңыз."
        ),
        StorageErrorKey.USER_PIN_TAKEN to TrilingualString(
            en = "This PIN is already taken on this cash register. Set a different one.",
            ru = "Такой пин на этой кассе уже занят. Задайте другой.",
            kk = "Бұл пин осы кассада бос емес. Басқасын енгізіңіз."
        ),
        StorageErrorKey.DUPLICATE_RECORD to TrilingualString(
            en = "A record with the same data already exists on this cash register.",
            ru = "Запись с такими же данными на этой кассе уже есть.",
            kk = "Дәл сондай деректері бар жазба бұл кассада бұрыннан бар."
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
        )
    )
}
