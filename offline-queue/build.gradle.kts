plugins {
    alias(libs.plugins.detekt)
    alias(libs.plugins.kotlin.multiplatform)
    jacoco
}

group = "io.github.texport"
version = "1.0.1"

repositories {
    mavenLocal()
    mavenCentral()
}

detekt {
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    allRules = true
    autoCorrect = true
    source.setFrom(files("src/commonMain/kotlin", "src/jvmMain/kotlin", "src/iosMain/kotlin"))
}

dependencies {
    detektPlugins(libs.detekt.formatting)
}

kotlin {
    jvm()
    
    iosArm64()
    iosX64()
    iosSimulatorArm64()

    jvmToolchain(libs.versions.java.get().toInt())

    sourceSets {
        commonMain {
            dependencies {
                // Core offline-queue logic has no external dependencies
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        jvmMain {
            dependencies {
                implementation(libs.slf4j.api)
            }
        }
        jvmTest {
            // JVM-specific tests
        }
    }

    targets.all {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                }
            }
        }
    }
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    exclude("**/build/generated/**")
}

jacoco {
    toolVersion = libs.versions.jacocoVersion.get()
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
    finalizedBy(tasks.named("jacocoTestReport"))
}

val jacocoTestReport = tasks.register<JacocoReport>("jacocoTestReport") {
    description = "Generates Jacoco code coverage report for the JVM target."
    dependsOn(tasks.named("jvmTest"))
    classDirectories.setFrom(files(tasks.named("compileKotlinJvm")))
    sourceDirectories.setFrom(files("src/commonMain/kotlin", "src/jvmMain/kotlin"))
    executionData.setFrom(files(layout.buildDirectory.file("jacoco/jvmTest.exec")))
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}
