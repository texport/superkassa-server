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
    implementation(project(":shared-strings"))
    implementation(libs.superkassa.core)
    implementation(libs.superkassa.offline.queue)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.slf4j.api)
    implementation(libs.sqlite.jdbc)
    implementation(libs.postgresql)
    implementation(libs.mysql.connector.j)
    implementation(libs.hikariCP)

    testImplementation(libs.superkassa.core)
    testImplementation(kotlin("test"))
    testImplementation(libs.mockk)
    testImplementation(project(":server-time"))
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
                "io.github.texport.superkassa.jvm.storage.impl.data.jdbc.Jdbc*Repository",
                "io.github.texport.superkassa.jvm.storage.impl.data.jdbc.JdbcStorageSession",
                "io.github.texport.superkassa.jvm.storage.impl.data.jdbc.HikariConfigFactory",
                "io.github.texport.superkassa.jvm.storage.impl.data.jdbc.HikariStorageBootstrap",
                "io.github.texport.superkassa.jvm.storage.impl.data.jdbc.MysqlConnector",
                "io.github.texport.superkassa.jvm.storage.impl.data.jdbc.PostgresConnector",
                "io.github.texport.superkassa.jvm.storage.impl.adapter.*",
                "io.github.texport.superkassa.jvm.storage.impl.domain.*",
                "io.github.texport.superkassa.jvm.storage.impl.application.health.*",
                "io.github.texport.superkassa.jvm.storage.impl.application.session.*",
                "io.github.texport.superkassa.jvm.storage.impl.application.bootstrap.*",
                "io.github.texport.superkassa.jvm.storage.impl.application.connector.*",
                "io.github.texport.superkassa.jvm.storage.impl.application.migration.*",
                "io.github.texport.superkassa.jvm.storage.impl.data.migration.*"
            )
            limit {
                minimum = "1.0".toBigDecimal()
            }
        }
        rule {
            element = "CLASS"
            includes = listOf(
                "io.github.texport.superkassa.jvm.storage.impl.data.jdbc.JdbcKkmUserRepository",
                "io.github.texport.superkassa.jvm.storage.impl.data.jdbc.JdbcShiftRepository"
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
