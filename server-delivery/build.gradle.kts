plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.detekt)
    jacoco
}

repositories {
    mavenLocal()
    mavenCentral()
    google()
}

dependencies {
    implementation(libs.superkassa.core.domain)
    implementation(libs.superkassa.delivery)
    implementation(libs.slf4j.api)
    implementation(libs.jakarta.mail)

    testImplementation(kotlin("test"))
    testImplementation(libs.mockk)
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
                "*SmsDeliveryAdapter*",
                "*EmailDeliveryAdapter*",
                "*WhatsAppDeliveryAdapter*",
                "*TelegramDeliveryAdapter*",
                "*PrintDeliveryAdapter*",
                "*BaseHttpDeliveryAdapter*"
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
