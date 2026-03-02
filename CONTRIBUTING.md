# Contributing to MSF

## Getting Started

1. Clone the repository
2. Ensure Java 21 is installed: `java -version`
3. Build the project: `./gradlew build`
4. Run tests: `./gradlew test`

## Project Structure

```
msf/
  msf-core/       Pure Java 21, no Minecraft dependencies
  msf-fabric/     Fabric bridge, depends on msf-core
  docs/           Specification and documentation
```

The full specification lives at `docs/MSF_Specification_V1.md`. Read it before making changes to parsing or encoding logic — every normative requirement is referenced by spec section in the implementation.

## Module Boundaries

**msf-core** must remain free of all Fabric and Minecraft dependencies. Blockstate strings, entity type strings, and biome identifiers are opaque UTF-8 in msf-core — never validated against a registry. NBT payloads are raw bytes — never deserialized. If a change requires Minecraft registry lookup it belongs in msf-fabric.

**msf-fabric** contains zero MSF parsing logic. It delegates entirely to msf-core and handles only the conversion between MSF model types and Minecraft types.

## Branch Naming

- `feat/description` — new functionality
- `fix/description` — bug fixes
- `refactor/description` — code changes with no behaviour change
- `docs/description` — documentation only
- `test/description` — test additions or corrections

## Commit Messages

Follow Conventional Commits:

```
feat: add biome data round trip support
fix: correct packed array length overflow for large regions
refactor: extract compression type handling into sealed class
docs: update spec Section 7.5 with overflow guidance
test: add boundary condition tests for BitPackedArray
```

## Code Standards

See [docs/CODING_STANDARDS.md](CODING_STANDARDS.md) for full detail. Key requirements:

- Java 21 — records, sealed classes, pattern matching, switch expressions. No preview features.
- 4 space indentation, 120 character line limit
- `@NotNull` / `@Nullable` on all public method signatures
- `Optional<T>` over null on public APIs
- `Consumer<MsfWarning>` parameter on all read and write methods
- Records for all immutable model classes
- Builders for complex object construction

## Testing Requirements

Every change affecting parsing or encoding must include:

- Happy path round trip — write then read produces identical data
- Boundary conditions — minimum values, maximum values
- Failure modes — the specific exception type the spec requires, not a generic exception
- If the spec says warn and continue, the test must verify the warning was emitted AND parsing continued

Tests live in `msf-core/src/test/java/` mirroring the main source structure. Synthetic binary test files for known-good and corruption cases live in `msf-core/src/test/resources/`.

## Spec References in Code

When implementing a normative requirement, cite the spec section inline:

```java
// Palette ID 0 is always AIR per spec Section 4.3 — write unconditionally
palette.add(0, "minecraft:air");
```

## Pull Requests

- Reference the spec section if the change implements or corrects a normative requirement
- All tests must pass: `./gradlew test`
- No new Minecraft or Fabric dependencies in msf-core
