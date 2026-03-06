dependencies {
    implementation(project(":msf-core"))
    implementation("info.picocli:picocli:4.7.5")
    testImplementation(project(":msf-core"))
}

// Produce a self-contained executable fat JAR.
// The regular `jar` task is replaced with a fat jar that includes all runtime dependencies.
tasks.jar {
    manifest {
        attributes["Main-Class"] = "dev.msf.cli.MsfCli"
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
