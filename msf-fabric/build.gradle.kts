dependencies {
    implementation(project(":msf-core"))

    // Fabric and Minecraft dependencies are provided at runtime by the mod loader
    compileOnly("net.fabricmc:fabric-loader:${libs.versions.fabricLoader.get()}")
    compileOnly("net.fabricmc.fabric-api:fabric-api:${libs.versions.fabricApi.get()}")

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

publishing {
    publications {
        named<MavenPublication>("mavenJava") {
            groupId = "dev.msf"
            artifactId = "msf-fabric"
            version = "1.0.0+1.21.1"
            pom {
                name.set("MSF Fabric")
                description.set("Fabric bridge for the MSF (Minecraft Structured Format) schematic format")
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
