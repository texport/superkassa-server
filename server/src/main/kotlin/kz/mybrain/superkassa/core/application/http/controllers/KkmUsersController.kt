package kz.mybrain.superkassa.core.application.http.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_USERS_LIST
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_USER_CREATED
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_USER_DELETED
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_200_USER_UPDATED
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_400_VALIDATION
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_403_FORBIDDEN
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_404_KKM_NOT_FOUND
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_404_NOT_FOUND
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_404_USER_OR_KKM_NOT_FOUND
import kz.mybrain.superkassa.core.application.http.ApiResponseMessages.MSG_409_PIN_BUSY
import kz.mybrain.superkassa.core.application.http.annotation.KkmApiResponses
import kz.mybrain.superkassa.core.application.http.utils.AuthHeaderUtils
import kz.mybrain.superkassa.core.presentation.facade.SuperkassaApi
import kz.mybrain.superkassa.core.presentation.model.*
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/kkm/{kkmId}")
@Tag(name = "Управление кассирами и операторами ККМ", description = "Операции с пользователями ККМ")
class KkmUsersController(private val kkmService: SuperkassaApi) {

    /** Получить список пользователей, зарегистрированных в ККМ. */
    @GetMapping("/users")
    @Operation(
        summary = "Получить список пользователей ККМ",
        description = """
            Возвращает список всех зарегистрированных операторов (кассиров и администраторов) для указанной ККМ.
            
            **Требования:**
            - Касса ККМ с указанным ID должна существовать.
            - Требуются права кассира (CASHIER) или администратора (ADMIN).
            - Авторизационный ПИН-код передается в заголовке `Authorization`.
            
            **Параметры:**
            - **kkmId** (путь): Идентификатор кассы.
            - **Authorization** (заголовок): ПИН-код кассира/администратора кассы в формате `Bearer <pin>` или просто `<pin>`.
            
            **Возвращаемая структура:**
            - Список объектов `UserResponse`, содержащих поля:
              - `id`: уникальный ID пользователя в системе
              - `name`: имя пользователя (ФИО)
              - `role`: роль (`CASHIER` или `ADMIN`)
        """
    )
    @KkmApiResponses(
        ok = MSG_200_USERS_LIST,
        forbidden = MSG_403_FORBIDDEN,
        notFound = MSG_404_KKM_NOT_FOUND
    )
    fun listUsers(
        @PathVariable kkmId: String,
        @RequestHeader("Authorization") authHeader: String?
    ): List<UserResponse> {
        val pin = AuthHeaderUtils.extractPin(authHeader)
        return kkmService.listUsers(kkmId, pin)
    }

    /** Создать нового пользователя (кассира/админа) для ККМ. */
    @PostMapping("/users")
    @Operation(
        summary = "Создать пользователя ККМ",
        description = """
            Регистрирует нового пользователя (кассира или администратора) для указанной ККМ.
            
            Каждый пользователь привязывается к кассе и имеет уникальный в рамках данной кассы ПИН-код для авторизации.
            
            **Требования:**
            - Требуются права администратора (ADMIN).
            - ПИН-код нового пользователя не должен быть занят другим пользователем этой же ККМ.
            - Авторизационный ПИН-код администратора передается в заголовке `Authorization`.
            
            **Параметры:**
            - **kkmId** (путь): Идентификатор кассы.
            - **Authorization** (заголовок): ПИН-код администратора в формате `Bearer <pin>` или просто `<pin>`.
            - **RequestBody**: Данные нового пользователя `UserCreateRequest`:
              - `name`: ФИО или логин пользователя.
              - `pin`: ПИН-код пользователя (должен состоять только из цифр).
              - `role`: Роль пользователя (`CASHIER` или `ADMIN`).
            
            **Возвращаемые коды:**
            - 200 OK: Пользователь успешно создан, возвращается объект `UserResponse`.
            - 400 Bad Request: Ошибка валидации параметров запроса.
            - 403 Forbidden: Недостаточно прав (не ADMIN) или неверный ПИН авторизации.
            - 409 Conflict: ПИН-код нового пользователя уже используется другим оператором кассы.
        """
    )
    @KkmApiResponses(
        ok = MSG_200_USER_CREATED,
        badRequest = MSG_400_VALIDATION,
        forbidden = MSG_403_FORBIDDEN,
        conflict = MSG_409_PIN_BUSY,
        notFound = MSG_404_KKM_NOT_FOUND
    )
    fun createUser(
        @PathVariable kkmId: String,
        @RequestHeader("Authorization") authHeader: String?,
        @RequestBody @Valid request: UserCreateRequest
    ): UserResponse {
        val pin = AuthHeaderUtils.extractPin(authHeader)
        return kkmService.createUser(kkmId, pin, request)
    }

    /** Обновить данные пользователя ККМ. */
    @PutMapping("/users/{userId}")
    @Operation(
        summary = "Редактировать пользователя ККМ",
        description = """
            Изменяет данные существующего пользователя ККМ (имя, роль или ПИН-код).
            
            **Требования:**
            - Требуются права администратора (ADMIN).
            - ПИН-код не должен пересекаться с ПИН-кодами других пользователей ККМ.
            - Авторизационный ПИН-код администратора передается в заголовке `Authorization`.
            
            **Параметры:**
            - **kkmId** (путь): Идентификатор кассы.
            - **userId** (путь): Уникальный ID редактируемого пользователя.
            - **Authorization** (заголовок): ПИН-код администратора в формате `Bearer <pin>` или просто `<pin>`.
            - **RequestBody**: Данные обновления `UserUpdateRequest`:
              - `name`: Новое имя пользователя.
              - `pin`: Новое значение ПИН-кода.
              - `role`: Новая роль (`CASHIER` или `ADMIN`).
            
            **Возвращаемые коды:**
            - 200 OK: Данные пользователя обновлены, возвращается объект `UserResponse`.
            - 403 Forbidden: Недостаточно прав (не ADMIN).
            - 409 Conflict: Указанный новый ПИН-код занят другим оператором.
        """
    )
    @KkmApiResponses(
        ok = MSG_200_USER_UPDATED,
        badRequest = MSG_400_VALIDATION,
        forbidden = MSG_403_FORBIDDEN,
        conflict = MSG_409_PIN_BUSY,
        notFound = MSG_404_USER_OR_KKM_NOT_FOUND
    )
    fun updateUser(
        @PathVariable kkmId: String,
        @PathVariable userId: String,
        @RequestHeader("Authorization") authHeader: String?,
        @RequestBody @Valid request: UserUpdateRequest
    ): UserResponse {
        val pin = AuthHeaderUtils.extractPin(authHeader)
        return kkmService.updateUser(kkmId, userId, pin, request)
    }

    /**   Удалить пользователя из ККМ. */
    @DeleteMapping("/users/{userId}")
    @Operation(
        summary = "Удалить пользователя ККМ",
        description = """
            Удаляет оператора (пользователя) из указанной ККМ. Удаленный пользователь больше не сможет авторизоваться.
            
            **Требования:**
            - Требуются права администратора (ADMIN).
            - Нельзя удалить самого себя (последнего активного администратора кассы).
            - Авторизационный ПИН-код администратора передается в заголовке `Authorization`.
            
            **Параметры:**
            - **kkmId** (путь): Идентификатор кассы.
            - **userId** (путь): ID удаляемого пользователя.
            - **Authorization** (заголовок): ПИН-код администратора в формате `Bearer <pin>`.
            
            **Возвращаемый ответ:**
            - Объект с полем `"ok": true` при успешном удалении.
        """
    )
    @KkmApiResponses(
        ok = MSG_200_USER_DELETED,
        forbidden = MSG_403_FORBIDDEN,
        notFound = MSG_404_NOT_FOUND
    )
    fun deleteUser(
        @PathVariable kkmId: String,
        @PathVariable userId: String,
        @RequestHeader("Authorization") authHeader: String?
    ): Map<String, Boolean> {
        val pin = AuthHeaderUtils.extractPin(authHeader)
        return mapOf("ok" to kkmService.deleteUser(kkmId, userId, pin))
    }
}
