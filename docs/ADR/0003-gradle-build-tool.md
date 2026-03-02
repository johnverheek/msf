# ADR-0003: Gradle as Build Tool

**Status**: Accepted

**Date**: 2026-03-01

**Author(s)**: Project Team

## Context

Java projects require a build tool for compilation, testing, dependency management, and distribution. Main options:
- **Gradle**: Modern, flexible, fast builds, growing adoption
- **Maven**: Industry standard, XML-based, verbose but stable
- **Ant + Ivy**: Legacy approach, low-level control, maintenance burden

Project needs a tool that supports multi-module builds while providing good developer experience and fast builds.

## Decision

Use Gradle as the primary build tool with:
- Multi-module Gradle project structure
- Gradle wrapper (`gradlew`) for consistent builds
- Kotlin DSL for build configuration (`.gradle.kts` files, future consideration)
- Gradle 8+ version

## Rationale

### Pros
- **Performance**: Significantly faster builds than Maven (incremental compilation, parallel building)
- **Flexibility**: Gradle syntax is imperative and flexible vs Maven's declarative approach
- **Multi-module**: Excellent multi-module support (better than Maven in practice)
- **Modern**: Active development, up-to-date feature set
- **Wrapper**: `gradlew` ensures consistent builds across environments
- **Developer Experience**: Better IDE integration, faster feedback loop
- **Extensibility**: Custom tasks and plugins are straightforward
- **Community**: Growing adoption, excellent documentation, large community

### Cons
- **Learning Curve**: More complex than Maven for beginners
- **Configuration**: Flexibility means more decisions to make upfront
- **Debugging**: More difficult to debug build issues Than Maven

### Trade-offs
- Accept initial learning curve for long-term productivity gains
- Accept more configuration decisions for improved flexibility

## Consequences

### Positive
- Multi-module project can be built efficiently
- Individual modules can be built/tested independently
- Incremental compilation saves time during development
- Gradle plugins provide rich ecosystem
- Team can adopt Gradle expertise for other projects

### Negative
- Team must learn Gradle-specific concepts
- Some Maven plugins not available in Gradle (rare)
- Build files require maintenance as project evolves

## Alternatives Considered

### Alternative 1: Apache Maven
- **Pros**: Industry standard, more stable, larger ecosystem, less learning required
- **Cons**: Slower builds, verbose XML, less flexible for complex projects
- **Why rejected**: Build speed and flexibility are important; Gradle better fits modern development

### Alternative 2: Ant + Ivy
- **Pros**: Fine-grained control, lightweight
- **Cons**: Low-level, verbose, maintenance burden, outdated
- **Why rejected**: Too low-level for modern project needs, Gradle superior

### Alternative 3: Bazel
- **Pros**: Extremely fast, hermetic builds, great for monorepos
- **Cons**: Steep learning curve, overkill for current project size, complex setup
- **Why rejected**: Premature optimization; Gradle sufficient, easier to adopt

## Implementation Notes

### Gradle Wrapper
Include in repository:
```
./gradlew       # Unix/Linux/macOS
./gradlew.bat   # Windows
gradle/wrapper/gradle-wrapper.jar
gradle/wrapper/gradle-wrapper.properties
```

Developers use `./gradlew` instead of `gradle` command.

### Root Build File
```gradle
plugins {
    id 'java'
}

allprojects {
    group = 'com.example'
    version = '1.0.0'
}

subprojects {
    apply plugin: 'java'
    // Common configuration
}
```

### Common Tasks
```bash
./gradlew build              # Build all modules
./gradlew test               # Run all tests
./gradlew :msf-core:build    # Build specific module
./gradlew clean              # Clean build artifacts
./gradlew tasks              # List available tasks
```

### Future: Kotlin DSL
Consider migrating to Gradle Kotlin DSL (`build.gradle.kts`) for:
- Type safety in build scripts
- IDE autocompletion
- Refactoring support

```kotlin
// build.gradle.kts
plugins {
    java
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
```

## Related ADRs
- ADR-0001: Multi-Module Architecture
- ADR-0002: Java 21 with Modern Features

## References
- [Gradle Official Documentation](https://docs.gradle.org/)
- [Gradle User Manual - Multi-project Builds](https://docs.gradle.org/current/userguide/multi_project_builds.html)
- [Gradle vs Maven Comparison](https://gradle.org/maven-vs-gradle/)
- [Gradle Wrapper Documentation](https://docs.gradle.org/current/userguide/gradle_wrapper.html)

## Approval
- [x] Approved by Project Lead (2026-03-01)
- [x] Gradle configured with wrapper: 2026-03-01
