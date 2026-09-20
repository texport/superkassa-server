package io.github.texport.superkassa.jvm.storage.impl.domain.model

/**
 * Пользователь ККМ (кассир/администратор).
 *
 * Открытого пина в записи нет: вход сверяется по [pinHash], а хранить
 * пин рядом с его хешем значило бы раздавать чужие учётные данные.
 */
data class KkmUserRecord(
    val id: String,
    val cashboxId: String,
    val name: String,
    val role: String,
    val pinHash: String,
    val createdAt: Long
)
