plugins {
    alias(libs.plugins.detekt)
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.spring.boot) apply false
}

group = "io.github.texport"
version = "1.0"

detekt {
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    allRules = true
}

/*
 * Модули ядра, чьи классы уже лежат внутри superkassa-core-jvm. Каналы доставки
 * и тестовый БФД ядра тянут их отдельными артефактами; второй экземпляр тех же
 * классов на пути классов лишь ждёт, когда разойдётся с первым.
 */
val modulesInsideCoreJar = listOf(
    "superkassa-core-domain-jvm",
    "superkassa-core-data-jvm",
    "superkassa-core-presentation-jvm",
    "superkassa-core-string-jvm",
    "superkassa-core-database-jvm",
    "superkassa-delivery-jvm",
    "superkassa-offline-queue-jvm",
    "superkassa-receipt-renderer-jvm"
)

subprojects {
    configurations.all {
        modulesInsideCoreJar.forEach { exclude(group = "io.github.texport", module = it) }
    }
}
