pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net")
        gradlePluginPortal()
    }
    plugins {
        id("fabric-loom") version "1.7.4"
    }
}

rootProject.name = "msf"

include("msf-core")
include("msf-fabric")
include("msf-cli")
