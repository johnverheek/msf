pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net")
        gradlePluginPortal()
    }
}

rootProject.name = "msf"

include("msf-core")
include("msf-fabric")
