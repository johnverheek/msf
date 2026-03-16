dependencies {
    implementation(project(":msf-core"))
    implementation("info.picocli:picocli:4.7.5")
    testImplementation(project(":msf-core"))
}

// Produce a self-contained executable fat JAR.
// dependsOn(configurations.runtimeClasspath) is required so :msf-core:jar is built
// before the zipTree expansion runs during a clean build (Session 11).
tasks.jar {
    dependsOn(configurations.runtimeClasspath)
    archiveBaseName.set("msf-cli")
    archiveVersion.set("${project.version}")
    archiveClassifier.set("")
    manifest {
        attributes["Main-Class"] = "dev.msf.cli.MsfCli"
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
