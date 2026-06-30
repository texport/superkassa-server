plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.detekt)
    `maven-publish`
    jacoco
}

group = "io.github.texport"
version = libs.versions.superkassaCore.get()

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "superkassa-time-java"
            from(components["java"])
        }
    }
}

repositories {
    mavenLocal()
    mavenCentral()
    google()
}

detekt {
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    allRules = true
    autoCorrect = true
}

dependencies {
    implementation(libs.superkassa.core.domain)
    implementation(libs.slf4j.api)
    
    testImplementation(kotlin("test"))
    detektPlugins(libs.detekt.formatting)
}

kotlin {
    jvmToolchain(libs.versions.javaTargetCore.get().toInt())
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = libs.versions.javaTargetCore.get()
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
            limit {
                minimum = "1.0".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
