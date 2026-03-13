plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

dependencies {
    implementation(project(":msf-core"))
    implementation("info.picocli:picocli:4.7.5")
    testImplementation(project(":msf-core"))
}

// Produce a self-contained executable fat JAR using Shadow.
tasks.shadowJar {
    archiveBaseName.set("msf-cli")
    archiveVersion.set("${project.version}")
    archiveClassifier.set("")
    manifest {
        attributes["Main-Class"] = "dev.msf.cli.MsfCli"
    }
}

// Rename the plain jar so it does not collide with the shadow jar filename.
tasks.jar {
    archiveClassifier.set("plain")
}

// Include shadow jar in the standard build lifecycle.
tasks.build {
    dependsOn(tasks.shadowJar)
}
