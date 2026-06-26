plugins {
    alias(libs.plugins.detekt)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    jacoco
}

group = "kz.mybrain"
version = libs.versions.superkassaCore.get()

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    implementation(libs.superkassa.core)
    implementation(project(":storage-jdbc"))
    implementation(project(":server-settings"))
    implementation(project(":server-delivery"))
    implementation(project(":server-converter"))
    implementation(project(":time-java"))
    implementation(libs.superkassa.receipt.renderer)
    implementation(libs.superkassa.offline.queue)
    implementation(libs.superkassa.delivery)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ofd.network.client)
    implementation(libs.hikariCP)
    
    runtimeOnly(libs.sqlite.jdbc)
    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.mysql.connector.j)

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlin.reflect)
    implementation(libs.springdoc.openapi)
    implementation(libs.resilience4j)
    implementation(libs.spring.boot.starter.aspectj)

    testImplementation(kotlin("test"))
    testImplementation(libs.mockk)
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
    autoCorrect = true
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
                "*QueueWorker*",
                "*Mappers*",
                "*AuthHeaderUtils*",
                "*ApiResponseMessages*",
                "*Controller*"
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
