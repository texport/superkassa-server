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
    implementation(project(":shared-strings"))
    implementation(libs.superkassa.core)
    implementation(libs.slf4j.api)

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
                "io.github.texport.superkassa.jvm.delivery.impl.PrintDeliveryAdapter*",
                "io.github.texport.superkassa.jvm.delivery.impl.JpsPrintDeliveryAdapter*"
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
