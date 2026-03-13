dependencies {
    implementation(libs.lz4)
    implementation(libs.zstd)
    implementation(libs.brotli)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

publishing {
    publications {
        named<MavenPublication>("mavenJava") {
            groupId = "dev.msf"
            artifactId = "msf-core"
            version = "${project.version}"
            pom {
                name.set("MSF Core")
                description.set("Core library for the MSF (Minecraft Structured Format) schematic format")
                url.set("https://github.com/jverheek/msf")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
            }
        }
    }
}
