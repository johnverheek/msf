plugins {
    id("fabric-loom")
}

dependencies {
    // Minecraft + mappings (provided by Loom — not packaged into the output jar)
    minecraft("com.mojang:minecraft:${libs.versions.minecraft.get()}")
    mappings("net.fabricmc:yarn:${libs.versions.yarn.get()}:v2")

    modImplementation("net.fabricmc:fabric-loader:${libs.versions.fabricLoader.get()}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${libs.versions.fabricApi.get()}")

    // msf-core is bundled into the output jar (no Minecraft dependency)
    include(implementation(project(":msf-core"))!!)
}

// Testmod source set — compiled against main sources + Minecraft
sourceSets {
    val testmod by creating {
        val main = sourceSets.main.get()
        compileClasspath += main.compileClasspath + main.output
        runtimeClasspath += main.runtimeClasspath + main.output
    }
}

loom {
    runs {
        named("server") {
            isIdeConfigGenerated = false
        }
        named("client") {
            isIdeConfigGenerated = false
        }
        create("gametest") {
            server()
            name("Game Test")
            source(sourceSets["testmod"])
            vmArg("-ea")
            property("fabric-api.gametest")
            property("fabric-api.gametest.report-file",
                project.layout.buildDirectory.file("test-results/gametest/TEST-gametest.xml").get().asFile.absolutePath)
        }
    }
}

// Allow JUnit standalone tests in the regular test source set (CanonicalFacingTest etc.)
dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = "dev.msf"
            artifactId = "msf-fabric"
            version = "${project.version}+1.21.11"
            pom {
                name.set("MSF Fabric")
                description.set("Fabric bridge for the MSF (Minecraft Structured Format) schematic format")
                url.set("https://github.com/johnverheek/msf")
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
