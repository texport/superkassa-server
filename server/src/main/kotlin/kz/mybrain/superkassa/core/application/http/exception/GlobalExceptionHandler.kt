package kz.mybrain.superkassa.core.application.http.exception

import kz.mybrain.superkassa.core.domain.exception.SuperkassaException
import kz.mybrain.superkassa.core.presentation.model.ApiErrorResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@ControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(SuperkassaException::class)
    fun handleSuperkassaException(ex: SuperkassaException): ResponseEntity<ApiErrorResponse> {
        val status = HttpStatus.resolve(ex.status) ?: HttpStatus.INTERNAL_SERVER_ERROR
        return ResponseEntity.status(status)
            .body(
                ApiErrorResponse(
                    code = ex.code,
                    message = ex.message ?: "[EN] Service error / [RU] Ошибка сервиса / [KK] Сервис қатесі"
                )
            )
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(ex: IllegalArgumentException): ResponseEntity<ApiErrorResponse> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(
                ApiErrorResponse(
                    code = "INVALID_ARGUMENT",
                    message = ex.message ?: "[EN] Invalid argument / [RU] Некорректный аргумент / [KK] Жарамсыз аргумент"
                )
            )
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleSpringValidationException(
        ex: MethodArgumentNotValidException
    ): ResponseEntity<ApiErrorResponse> {
        val messages =
            ex.bindingResult.allErrors.joinToString("; ") { error ->
                val fieldName =
                    (error as? org.springframework.validation.FieldError)?.field
                        ?: error.objectName
                "$fieldName: ${error.defaultMessage}"
            }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiErrorResponse(code = "VALIDATION_ERROR", message = messages))
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatchException(
        ex: MethodArgumentTypeMismatchException
    ): ResponseEntity<ApiErrorResponse> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(
                ApiErrorResponse(
                    code = "INVALID_PARAM",
                    message = "[EN] Parameter '${ex.name}' has invalid type: ${ex.value} / " +
                        "[RU] Параметр '${ex.name}' имеет неверный тип: ${ex.value} / " +
                        "[KK] '${ex.name}' параметрінің типі қате: ${ex.value}"
                )
            )
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleJsonException(ex: HttpMessageNotReadableException): ResponseEntity<ApiErrorResponse> {
        logger.warn("Malformed JSON request: {}", ex.message, ex)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(
                ApiErrorResponse(
                    code = "INVALID_JSON",
                    message = "[EN] Malformed JSON request or invalid format / " +
                        "[RU] Некорректный запрос JSON или неверный формат / " +
                        "[KK] Қате JSON сұранысы немесе жарамсыз формат"
                )
            )
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<ApiErrorResponse> {
        logger.error("Unhandled exception", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(
                ApiErrorResponse(
                    code = "INTERNAL_ERROR",
                    message = "[EN] Internal server error / " +
                        "[RU] Внутренняя ошибка сервера / " +
                        "[KK] Сервердің ішкі қатесі"
                )
            )
    }

    @ExceptionHandler(Throwable::class)
    fun handleThrowable(ex: Throwable): ResponseEntity<ApiErrorResponse> {
        logger.error("Critical error (Throwable/Error)", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(
                ApiErrorResponse(
                    code = "CRITICAL_ERROR",
                    message = "[EN] Critical system error occurred / " +
                        "[RU] Произошла критическая системная ошибка / " +
                        "[KK] Критикалық жүйелік қате орын алды"
                )
            )
    }
}
