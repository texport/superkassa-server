package kz.mybrain.superkassa.core.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.tags.Tag
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springdoc.core.customizers.PropertyCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun customOpenAPI(): OpenAPI {
        return OpenAPI()
            .info(
                Info().title("Superkassa Core API")
                    .version("1.0")
                    .description(
                        "API сервиса Superkassa Core для управления ККМ, сменами и чеками."
                    )
            )
    }

    /**
     * Security-схема для авторизации по PIN через заголовок Authorization.
     *
     * Схема:
     * - тип: apiKey
     * - заголовок: Authorization
     * - формат значения: "Bearer <pin>" или просто "<pin>" (не JWT)
     *
     * Схема автоматически подключается ко всем операциям, в которых присутствует
     * заголовок Authorization, за исключением публичных endpoint’ов без этого заголовка.
     */
    @Bean
    fun pinSecurityCustomizer(): OpenApiCustomizer {
        return OpenApiCustomizer { openApi ->
            val components = openApi.components ?: Components()

            // Регистрируем security-схему, если она ещё не зарегистрирована
            val schemeName = "PinAuthorization"
            val existingSchemes = components.securitySchemes ?: mutableMapOf()
            if (!existingSchemes.containsKey(schemeName)) {
                val pinScheme =
                    SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .`in`(SecurityScheme.In.HEADER)
                        .name("Authorization")
                        .description(
                            "PIN кассира/администратора. Формат: \"Bearer <pin>\" или \"<pin>\" (не JWT)."
                        )
                components.addSecuritySchemes(schemeName, pinScheme)
            }
            openApi.components = components

            val requirement = SecurityRequirement().addList(schemeName)

            // Подключаем security-схему ко всем операциям, где есть заголовок Authorization.
            openApi.paths?.forEach { (_, pathItem) ->
                pathItem.readOperations().forEach operationLoop@{ operation ->
                    val hasAuthHeader =
                        operation.parameters?.any { parameter ->
                            parameter.`in`.equals("header", ignoreCase = true) &&
                                parameter.name.equals("Authorization", ignoreCase = true)
                        } ?: false

                    if (!hasAuthHeader) return@operationLoop

                    val security =
                        (operation.security ?: mutableListOf()).apply {
                            if (none { it.containsKey(schemeName) }) {
                                add(requirement)
                            }
                        }
                    operation.security = security
                }
            }
        }
    }

    /**
     * Кастомайзер для установки порядка тегов в Swagger UI.
     * Теги будут отображаться в указанном порядке.
     *
     * ВАЖНО: После изменения этого метода необходимо перезапустить приложение,
     * чтобы изменения вступили в силу.
     */
    @Bean
    fun tagsOrderCustomizer(): OpenApiCustomizer {
        return OpenApiCustomizer { openApi ->
            // Определяем желаемый порядок тегов
            val tagOrder = listOf(
                "О Superkassa",
                "Диагностика",
                "Настройки Superkassa",
                "Режим программирования ККМ",
                "Управление кассирами и операторами ККМ",
                "Ввод/Вывод из/в эксплуатацию ККМ",
                "Управление ККМ",
                "Диагностика ККМ",
                "Управление счетчиками ККМ",
                "Внесение и изъятие наличных денег",
                "Чеки",
                "Отчеты",
                "Управление сменой(Z-Отчет) ККМ"
            )

            // Получаем существующие теги из всех операций
            val allTags = mutableSetOf<String>()
            openApi.paths?.forEach { (_, pathItem) ->
                pathItem.readOperations().forEach { operation ->
                    operation.tags?.forEach { tagName ->
                        allTags.add(tagName)
                    }
                }
            }

            // Получаем существующие теги из openApi.tags (если есть)
            val existingTags = openApi.tags?.toMutableList() ?: mutableListOf()

            // Добавляем теги из операций, которых нет в existingTags
            allTags.forEach { tagName ->
                if (existingTags.none { it.name == tagName }) {
                    existingTags.add(Tag().name(tagName))
                }
            }

            // Создаем карту существующих тегов по имени
            val tagsMap = existingTags.associateBy { it.name }

            // Создаем новый список тегов в нужном порядке
            val orderedTags = mutableListOf<Tag>()

            // Добавляем теги в указанном порядке
            for (tagName in tagOrder) {
                tagsMap[tagName]?.let { tag ->
                    orderedTags.add(tag)
                }
            }

            // Добавляем оставшиеся теги, которых нет в списке порядка (на случай, если появятся новые)
            for (tag in existingTags) {
                if (!tagOrder.contains(tag.name)) {
                    orderedTags.add(tag)
                }
            }

            // Устанавливаем упорядоченный список тегов
            openApi.tags = orderedTags
        }
    }

    @Bean
    fun kmpSchemaConverter(): io.swagger.v3.core.converter.ModelConverter {
        return io.swagger.v3.core.converter.ModelConverter { type, context, chain ->
            var resolved = if (chain.hasNext()) chain.next().resolve(type, context, chain) else null
            if (resolved == null && type.type is Class<*>) {
                val clazz = type.type as Class<*>
                if (type.isSkipSchemaName) {
                    org.slf4j.LoggerFactory.getLogger(OpenApiConfig::class.java).warn(
                        "Swagger ModelResolver returned null for subtype ${clazz.name}. Applying fallback schema to prevent NullPointerException."
                    )
                    resolved = io.swagger.v3.oas.models.media.Schema<Any>().apply {
                        name = clazz.simpleName
                    }
                }
            }
            val anns = type.ctxAnnotations
            if (anns != null && resolved != null) {
                val kmpSchema = anns.firstOrNull {
                    it is kz.mybrain.superkassa.core.presentation.annotations.Schema
                } as? kz.mybrain.superkassa.core.presentation.annotations.Schema
                if (kmpSchema != null) {
                    if (kmpSchema.description.isNotEmpty()) {
                        resolved.description = kmpSchema.description
                    }
                    if (kmpSchema.example.isNotEmpty()) {
                        resolved.example = kmpSchema.example
                    }
                    if (kmpSchema.type.isNotEmpty()) {
                        resolved.type = kmpSchema.type
                    }
                    if (kmpSchema.format.isNotEmpty()) {
                        resolved.format = kmpSchema.format
                    }
                    if (kmpSchema.minimum.isNotEmpty()) {
                        resolved.minimum = java.math.BigDecimal(kmpSchema.minimum)
                    }
                    if (kmpSchema.maximum.isNotEmpty()) {
                        resolved.maximum = java.math.BigDecimal(kmpSchema.maximum)
                    }
                    if (kmpSchema.allowableValues.isNotEmpty()) {
                        resolved.enum = kmpSchema.allowableValues.toList()
                    }
                    if (kmpSchema.minLength > 0) {
                        resolved.minLength = kmpSchema.minLength
                    }
                    if (kmpSchema.maxLength < Int.MAX_VALUE) {
                        resolved.maxLength = kmpSchema.maxLength
                    }
                }
            }
            resolved
        }
    }

    @Bean
    fun kmpPropertyCustomizer(): PropertyCustomizer {
        return PropertyCustomizer { schema, type ->
            val anns = type.ctxAnnotations
            if (anns != null) {
                val kmpSchema = anns.firstOrNull {
                    it is kz.mybrain.superkassa.core.presentation.annotations.Schema
                } as? kz.mybrain.superkassa.core.presentation.annotations.Schema
                if (kmpSchema != null && kmpSchema.hidden) {
                    return@PropertyCustomizer null
                }
            }
            schema
        }
    }
}
