plugins {
    alias(libs.plugins.detekt)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    jacoco
}

group = "kz.mybrain"
version = libs.versions.superkassaCore.get()

repositories {
    mavenLocal()
    mavenCentral()
    google()
}

dependencies {
    implementation(project(":server-settings"))
    implementation(libs.superkassa.core.domain)
    implementation(libs.superkassa.core.data)
    implementation(project(":offline-queue"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.slf4j.api)
    implementation(libs.sqlite.jdbc)
    implementation(libs.postgresql)
    implementation(libs.mysql.connector.j)
    implementation(libs.hikariCP)

    testImplementation(libs.superkassa.core.presentation)
    testImplementation(kotlin("test"))
    testImplementation(libs.mockk)
    testImplementation(libs.superkassa.time.java)
    testImplementation(libs.superkassa.delivery)
    testImplementation(libs.superkassa.receipt.renderer)
    detektPlugins(libs.detekt.formatting)
}

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())
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
                "*.DefaultStorageConnectorRegistry",
                "*.DefaultStorageBootstrap",
                "kz.mybrain.superkassa.storage.data.jdbc.Jdbc*Repository",
                "kz.mybrain.superkassa.storage.data.jdbc.JdbcStorageSession",
                "kz.mybrain.superkassa.storage.data.jdbc.HikariConfigFactory",
                "kz.mybrain.superkassa.storage.data.jdbc.HikariStorageBootstrap",
                "kz.mybrain.superkassa.storage.data.jdbc.MysqlConnector",
                "kz.mybrain.superkassa.storage.data.jdbc.PostgresConnector",
                "kz.mybrain.superkassa.core.data.adapter.*",
                "kz.mybrain.superkassa.storage.domain.*",
                "kz.mybrain.superkassa.storage.application.health.*",
                "kz.mybrain.superkassa.storage.application.session.*",
                "kz.mybrain.superkassa.storage.application.bootstrap.*",
                "kz.mybrain.superkassa.storage.application.connector.*",
                "kz.mybrain.superkassa.storage.application.migration.*",
                "kz.mybrain.superkassa.storage.data.migration.*"
            )
            limit {
                minimum = "1.0".toBigDecimal()
            }
        }
        rule {
            element = "CLASS"
            includes = listOf(
                "kz.mybrain.superkassa.storage.data.jdbc.JdbcKkmUserRepository",
                "kz.mybrain.superkassa.storage.data.jdbc.JdbcShiftRepository"
            )
            limit {
                minimum = "0.85".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
