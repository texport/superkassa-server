package kz.mybrain.superkassa.core.http.controllers

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.texport.superkassa.core.presentation.api.SuperkassaApi
import io.github.texport.superkassa.core.presentation.api.model.user.UserResponse
import io.github.texport.superkassa.core.presentation.api.model.user.UserRole
import io.mockk.every
import io.mockk.mockk
import kz.mybrain.superkassa.core.application.http.controllers.KkmUsersController
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Пин кассира не покидает узел.
 *
 * Кассир вправе смотреть список пользователей кассы, и пока пин лежал
 * в ответе, он читал по нему учётные данные администратора. Проверка идёт
 * по готовому JSON, а не по полям объекта: ушедшее в сеть — это байты ответа.
 */
class KkmUserPinNotExposedTest {

    private val adminPin = "7419"
    private val service = mockk<SuperkassaApi>()
    private val controller = KkmUsersController(service)
    private val mapper = jacksonObjectMapper()

    @Test
    fun `user list response carries no pin`() {
        every { service.listUsers("kkm-1", adminPin) } returns
            listOf(
                UserResponse(userId = "u-1", name = "Администратор", role = UserRole.ADMIN),
                UserResponse(userId = "u-2", name = "Айгүл Серікова", role = UserRole.CASHIER)
            )

        val json = mapper.writeValueAsString(controller.listUsers("kkm-1", "Bearer $adminPin"))

        assertTrue(json.contains("\"userId\":\"u-1\""), json)
        assertFalse(json.contains("pin", ignoreCase = true), json)
        assertFalse(json.contains(adminPin), json)
    }

    @Test
    fun `current user response carries no pin`() {
        every { service.currentUser("kkm-1", adminPin) } returns
            UserResponse(userId = "u-1", name = "Администратор", role = UserRole.ADMIN)

        val json = mapper.writeValueAsString(controller.currentUser("kkm-1", "Bearer $adminPin"))

        assertFalse(json.contains("pin", ignoreCase = true), json)
        assertFalse(json.contains(adminPin), json)
    }
}
