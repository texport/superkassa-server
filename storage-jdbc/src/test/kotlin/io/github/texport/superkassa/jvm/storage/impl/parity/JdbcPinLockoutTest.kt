package io.github.texport.superkassa.jvm.storage.impl.parity

import io.github.texport.superkassa.core.domain.api.exception.SuperkassaException
import io.github.texport.superkassa.core.presentation.api.SuperkassaApi
import io.github.texport.superkassa.jvm.storage.impl.parity.JdbcKassa.Companion.ADMIN_PIN
import io.github.texport.superkassa.jvm.storage.impl.parity.JdbcKassa.Companion.KKM
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Счёт неверных пинов лежит в базе узла: блокировка, заработанная
 * перебором, переживает перезапуск, проходит сама и уходит вместе с кассой.
 */
class JdbcPinLockoutTest {
    private val kassa = JdbcKassa()

    @Test
    fun `пятый неверный пин запирает кассу, и перезапуск узла её не отпирает`() {
        repeat(4) { assertEquals("USER_NOT_FOUND", refusal(kassa) { currentUser(KKM, WRONG_PIN) }.code) }
        assertEquals("PIN_LOCKED", refusal(kassa) { currentUser(KKM, WRONG_PIN) }.code)

        val restarted = JdbcKassa(dir = kassa.dir, clock = kassa.clock, register = false)

        assertEquals("PIN_LOCKED", refusal(restarted) { currentUser(KKM, ADMIN_PIN) }.code)
    }

    @Test
    fun `блокировка проходит сама, и верный пин обнуляет счёт`() {
        repeat(5) { refusal(kassa) { currentUser(KKM, WRONG_PIN) } }
        kassa.clock.move(FIRST_LOCK_MS)

        assertEquals("Айгерим", kassa.api.currentUser(KKM, ADMIN_PIN).name)
        repeat(4) { assertEquals("USER_NOT_FOUND", refusal(kassa) { currentUser(KKM, WRONG_PIN) }.code) }
    }

    @Test
    fun `удалённая касса уносит свой счёт`() {
        repeat(5) { refusal(kassa) { currentUser(KKM, WRONG_PIN) } }

        kassa.storage.deleteKkmCompletely(KKM)

        assertEquals(0, kassa.storage.pinAttempts.getAndUpdate(KKM) { it }.failures)
    }

    private fun refusal(on: JdbcKassa, call: SuperkassaApi.() -> Unit) =
        assertFailsWith<SuperkassaException> { on.api.call() }

    private companion object {
        const val WRONG_PIN = "0001"
        const val FIRST_LOCK_MS = 30_000L
    }
}
