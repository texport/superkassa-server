package io.github.texport.superkassa.jvm.storage.impl.parity

import io.github.texport.superkassa.core.domain.api.exception.SuperkassaException
import io.github.texport.superkassa.core.presentation.api.model.kkm.KkmInitDirectRequest
import io.github.texport.superkassa.core.presentation.api.model.user.UserRole
import kz.kazakhtelecom.proto.v203.CommandTypeEnum
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Касса заводится только с ответом БФД.
 *
 * Без связи или при отказе БФД владелец получает отказ с причиной, и
 * в базе ничего не появляется: «успех» без записи оставлял кассу,
 * которой нет ни в списке, ни у ядра.
 */
class JdbcKkmRegistrationTest {
    private val kassa = JdbcKassa()

    @Test
    fun `БФД недоступен - отказ с причиной, касса не записана`() {
        kassa.bfd.unreachableOnce()

        val refusal = assertFailsWith<SuperkassaException> { kassa.api.initKkm(request()) }

        assertEquals("OFD_COMMAND_FAILED", refusal.code)
        assertNull(kassa.storage.findKkmBySystemId(SYSTEM_ID))
    }

    @Test
    fun `БФД отказал в сведениях о кассе - отказ с кодом БФД, касса не записана`() {
        kassa.bfd.reject(CommandTypeEnum.COMMAND_INFO, BFD_REFUSAL)

        val refusal = assertFailsWith<SuperkassaException> { kassa.api.initKkm(request()) }

        assertEquals("OFD_COMMAND_FAILED", refusal.code)
        assertEquals(true, "code=$BFD_REFUSAL" in refusal.trilingualMessage.ru, refusal.trilingualMessage.ru)
        assertNull(kassa.storage.findKkmBySystemId(SYSTEM_ID))
    }

    @Test
    fun `БФД ответил - касса записана и видна по номеру в БФД`() {
        val created = kassa.api.initKkm(request())

        assertEquals(created.kkmId, kassa.storage.findKkmBySystemId(SYSTEM_ID)?.id)
    }

    @Test
    fun `администратор новой кассы входит пином, заданным при заведении`() {
        val created = kassa.api.initKkm(request())

        val admin = kassa.api.currentUser(created.kkmId, ADMIN_PIN)

        assertEquals(UserRole.ADMIN, admin.role)
        assertEquals(1, kassa.api.listUsers(created.kkmId, ADMIN_PIN).size)
    }

    @Test
    fun `без пина администратора касса не заводится, а БФД не спрашивается`() {
        val asked = kassa.bfd.requests.size

        val refusal = assertFailsWith<SuperkassaException> { kassa.api.initKkm(request().copy(adminPin = null)) }

        assertEquals("KKM_ADMIN_PIN_REQUIRED", refusal.code)
        assertEquals(asked, kassa.bfd.requests.size)
        assertNull(kassa.storage.findKkmBySystemId(SYSTEM_ID))
    }

    private fun request() = KkmInitDirectRequest(
        ofdId = "KAZAKHTELECOM", ofdEnvironment = "TEST", ofdSystemId = SYSTEM_ID,
        ofdToken = JdbcKassa.TOKEN.toString(), kkmKgdId = "010101099999", factoryNumber = "KZT0000099",
        manufactureYear = 2026, oked = "47111", adminPin = ADMIN_PIN
    )

    private companion object {
        const val ADMIN_PIN = "7391"
        const val SYSTEM_ID = "200600"
        const val BFD_REFUSAL = 3
    }
}
