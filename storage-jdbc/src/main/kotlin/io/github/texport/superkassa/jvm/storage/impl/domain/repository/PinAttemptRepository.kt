package io.github.texport.superkassa.jvm.storage.impl.domain.repository

import io.github.texport.superkassa.core.domain.api.model.auth.PinAttempts

/**
 * Счёт неверных пинов по кассам (pin_attempts).
 *
 * Счёт живёт в базе, а не в памяти процесса: блокировка, заработанная
 * перебором пина, переживает перезапуск узла и видна всем узлам,
 * которые делят одну базу.
 */
interface PinAttemptRepository {
    /**
     * Счёт кассы, взятый под запись до конца транзакции.
     *
     * У кассы без счёта он заводится пустым: иначе две попытки подряд
     * прочитали бы «счёта нет» обе, и одна из них потерялась бы.
     */
    fun lock(cashboxId: String): PinAttempts

    fun save(cashboxId: String, attempts: PinAttempts)

    fun delete(cashboxId: String)
}
