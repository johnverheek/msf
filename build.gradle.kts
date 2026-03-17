plugins {
    java
}

allprojects {
    group = "dev.msf"
    version = "1.0.0"

    repositories {
        mavenCentral()
        maven("https://maven.fabricmc.net")
        maven("https://maven.terraformersmc.com/")
    }
}

subprojects {
    apply(plugin = "maven-publish")

    // msf-fabric uses fabric-loom which provides java-library + java extension itself;
    // all java-dependent configuration is handled by msf-fabric's own build file.
    if (project.name != "msf-fabric") {
        apply(plugin = "java-library")
        java {
            sourceCompatibility = JavaVersion.VERSION_21
            targetCompatibility = JavaVersion.VERSION_21
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
        }
        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
        }
        dependencies {
            testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
            testRuntimeOnly("org.junit.platform:junit-platform-launcher")
        }
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
        configure<PublishingExtension> {
            publications {
                create<MavenPublication>("mavenJava") {
                    from(components["java"])
                }
            }
        }
    }
}
