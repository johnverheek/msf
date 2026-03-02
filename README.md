# MSF

A Java 21 multi-module project built with Gradle.

## Modules

- **msf-core**: Core functionality
- **msf-fabric**: Fabric module (depends on msf-core)

## Building

```bash
./gradlew build
```

## Testing

```bash
./gradlew test
```

## Project Structure

Each module has its own `src/main/java` and `src/test/java` directories for sources and tests.
