plugins {
    alias(libs.plugins.detekt)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
}

group = "kz.mybrain"
version = "1.0"

repositories {
    mavenLocal()
    mavenCentral()
    google()
}



dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    implementation(libs.superkassa.core)
    implementation(libs.superkassa.storage.jdbc)
    implementation(libs.superkassa.j2se.adapters)
    implementation(libs.superkassa.time.java)
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
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}




detekt {
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    allRules = true
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
}
