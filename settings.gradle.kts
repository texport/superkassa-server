pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "superkassa-server"

include("server-settings", "server-delivery", "server-converter", "storage-jdbc", "server", "time-java", "offline-queue")

