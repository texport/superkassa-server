package kz.mybrain.superkassa.core.application.http.exception

import io.github.texport.superkassa.jvm.shared.strings.api.ErrorResolver
import io.github.texport.superkassa.jvm.shared.strings.api.key.WebErrorKey
import io.github.texport.superkassa.jvm.shared.strings.impl.DefaultErrorResolver
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
    private val errorResolver: ErrorResolver = DefaultErrorResolver()

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
        logger.warn("Malformed JSON request: {}", ex.message)
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

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException::class)
    fun handleNoResourceFoundException(
        ex: org.springframework.web.servlet.resource.NoResourceFoundException
    ): ResponseEntity<ApiErrorResponse> {
        logger.warn("Resource not found: {}", ex.message)
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(
                ApiErrorResponse(
                    code = WebErrorKey.RESOURCE_NOT_FOUND.code,
                    message = errorResolver.resolve(WebErrorKey.RESOURCE_NOT_FOUND).toString()
                )
            )
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupportedException(
        ex: org.springframework.web.HttpRequestMethodNotSupportedException
    ): ResponseEntity<ApiErrorResponse> {
        logger.warn("Method not supported: {}", ex.message)
        val msg = errorResolver.resolve(WebErrorKey.METHOD_NOT_ALLOWED)
            .formatArgs(ex.method)
            .toString()
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
            .body(
                ApiErrorResponse(
                    code = WebErrorKey.METHOD_NOT_ALLOWED.code,
                    message = msg
                )
            )
    }

    @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException::class)
    fun handleMissingParamsException(
        ex: org.springframework.web.bind.MissingServletRequestParameterException
    ): ResponseEntity<ApiErrorResponse> {
        logger.warn("Missing required parameter: {}", ex.message)
        val msg = errorResolver.resolve(WebErrorKey.MISSING_PARAMETER)
            .formatArgs(ex.parameterName)
            .toString()
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(
                ApiErrorResponse(
                    code = WebErrorKey.MISSING_PARAMETER.code,
                    message = msg
                )
            )
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException::class)
    fun handleHttpMediaTypeNotSupportedException(
        ex: org.springframework.web.HttpMediaTypeNotSupportedException
    ): ResponseEntity<ApiErrorResponse> {
        logger.warn("Media type not supported: {}", ex.message)
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
            .body(
                ApiErrorResponse(
                    code = WebErrorKey.UNSUPPORTED_MEDIA_TYPE.code,
                    message = errorResolver.resolve(WebErrorKey.UNSUPPORTED_MEDIA_TYPE).toString()
                )
            )
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<ApiErrorResponse> {
        val rootFrame = ex.stackTrace.firstOrNull()?.toString() ?: "unknown"
        logger.error("Unhandled exception: {} ({}) at {}", ex.javaClass.name, ex.message, rootFrame)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(
                ApiErrorResponse(
                    code = WebErrorKey.INTERNAL_ERROR.code,
                    message = errorResolver.resolve(WebErrorKey.INTERNAL_ERROR).toString()
                )
            )
    }

    @ExceptionHandler(Throwable::class)
    fun handleThrowable(ex: Throwable): ResponseEntity<ApiErrorResponse> {
        val rootFrame = ex.stackTrace.firstOrNull()?.toString() ?: "unknown"
        logger.error("Critical error (Throwable/Error): {} ({}) at {}", ex.javaClass.name, ex.message, rootFrame)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(
                ApiErrorResponse(
                    code = WebErrorKey.CRITICAL_ERROR.code,
                    message = errorResolver.resolve(WebErrorKey.CRITICAL_ERROR).toString()
                )
            )
    }
}
