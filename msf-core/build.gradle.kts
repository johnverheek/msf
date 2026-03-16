plugins {
    // Built-in Gradle plugin; enables sign(publications) for Maven Central requirement
    signing
}

dependencies {
    implementation(libs.lz4)
    implementation(libs.zstd)
    implementation(libs.brotli)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Maven Central requires a sources JAR and a javadoc JAR alongside the main artifact.
// withSourcesJar() and withJavadocJar() register them as additional publication artifacts.
java {
    withSourcesJar()
    withJavadocJar()
}

// Suppress doclint — internal @see references to spec sections are intentional prose,
// not resolvable class/method links, and should not fail the javadoc build.
tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
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
                url.set("https://github.com/johnverheek/msf")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("johnverheek")
                        name.set("johnverheek")
                        url.set("https://github.com/johnverheek")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/johnverheek/msf.git")
                    developerConnection.set("scm:git:ssh://github.com/johnverheek/msf.git")
                    url.set("https://github.com/johnverheek/msf")
                }
            }
        }
    }

    repositories {
        // Sonatype OSSRH staging repository — credentials supplied via project properties.
        // In CI: set ORG_GRADLE_PROJECT_mavenUsername and ORG_GRADLE_PROJECT_mavenPassword
        // env vars; Gradle maps them to project properties automatically.
        maven {
            name = "sonatype"
            url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = findProperty("mavenUsername") as String? ?: ""
                password = findProperty("mavenPassword") as String? ?: ""
            }
        }
    }
}

// Signing is active only when GPG key project properties are present.
// In CI: set ORG_GRADLE_PROJECT_signingKey and ORG_GRADLE_PROJECT_signingPassword.
// Locally: signing is skipped, allowing normal builds without a key.
signing {
    val signingKey = findProperty("signingKey") as String?
    val signingPassword = findProperty("signingPassword") as String?
    if (!signingKey.isNullOrBlank() && !signingPassword.isNullOrBlank()) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications)
    }
}
