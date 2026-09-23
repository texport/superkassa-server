plugins {
    alias(libs.plugins.detekt)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    jacoco
}

group = "io.github.texport"
version = libs.versions.serverVersion.get()

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation(libs.resilience4j)
    implementation(platform(libs.spring.boot.dependencies))
    implementation(libs.superkassa.core)

    implementation(project(":storage-jdbc"))
    implementation(project(":server-settings"))
    implementation(project(":server-delivery"))
    implementation(project(":server-converter"))
    implementation(project(":server-time"))
    implementation(project(":shared-strings"))

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.hikariCP)
    
    runtimeOnly(libs.sqlite.jdbc)
    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.mysql.connector.j)

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlin.reflect)
    implementation(libs.springdoc.openapi)
    implementation(libs.spring.boot.starter.aspectj)

    testImplementation(kotlin("test"))
    testImplementation(libs.mockk)
    testImplementation(libs.archunit)
    detektPlugins(libs.detekt.formatting)
}

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(libs.versions.java.get()))
    }
}

detekt {
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    allRules = true
    // Автоправка переписывает исходники на месте; на CI её правки пропадают
    // вместе с раннером, а исправленное нарушение проверку не роняет.
    autoCorrect = System.getenv("CI") == null
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = libs.versions.java.get()
}

jacoco {
    toolVersion = libs.versions.jacocoVersion.get()
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    violationRules {
        rule {
            element = "CLASS"
            excludes = listOf(
                // Типы ниже объявлены только для описания схемы OpenAPI:
                // контроллеры возвращают Map, эти классы нигде не создаются,
                // исполняемого кода в них нет и покрывать тестами нечего.
                "*HealthResponse*",
                "*SystemInfoResponse*",
                "*StorageInfoResponse*",
                "*SystemStatisticsResponse*",
                "*SystemFeaturesResponse*",
                "*SuperkassaApplication*",
                "*ConsoleLoader*",
                "*OfdHealthIndicator*",
                "*OfdHealthStatus*",
                "*KkmApiResponsesOperationCustomizer*",
                "*ServicesConfig*",
                "*AdaptersConfig*",
                "*OpenApiConfig*",
                "*GlobalExceptionHandler*",
                "*KkmPathBodyValidator*",
                "*Mappers*",
                "*AuthHeaderUtils*",
                "*ApiResponseMessages*",
                "*Controller*",
                "*TraceIdFilter*",
                "*Dto*",
                "*TrilingualLogConverter*",
                "*ServerDeliveryServiceAdapter*"
            )
            limit {
                minimum = "1.0".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

// Версия узла и версия ядра внутри него — из сборки, а не из строки в yml.
// Узел сообщал о себе `1.0`, пока на деле собирался из 1.0.6 с ядром 1.4.4:
// значение в `application.yml` никто не обновлял, а КГД версию ПО спрашивает.
springBoot {
    buildInfo {
        properties {
            additional.set(mapOf("coreVersion" to libs.versions.superkassaCore.get()))
        }
    }
}
