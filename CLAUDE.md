# CLAUDE.md — MSF Project

## What This Project Is

MSF (Minecraft Structured Format) is a binary file format specification and reference implementation for Minecraft structure schematics. Tool-agnostic. File extension `.msf`. Not a mod — a format any tool can implement.

## Repository Structure
```
msf/
  docs/
    MSF_Specification_V1.md    — the normative spec (source of truth)
    ARCHITECTURE.md            — module boundaries, package structure
    CODING_STANDARDS.md        — code conventions
    ROADMAP.md                 — session and spec review history
    STUDIO_STATE.md            — current project state (studio workflow)
    STUDIO_MANIFEST.md         — installed skills
    gates/                     — gate documents by version
    modules/                   — per-module documentation
  msf-core/                    — pure Java 21, zero Minecraft dependencies
  msf-fabric/                  — Fabric 1.21.1 bridge
  msf-cli/                     — standalone CLI tool
  build.gradle.kts             — shared build configuration
  settings.gradle.kts          — module declarations
  gradle/libs.versions.toml    — version catalog
```

## Module Boundaries (non-negotiable)

**msf-core:** Zero Fabric or Minecraft dependencies. Blockstate strings, entity type strings, biome identifiers are opaque UTF-8 — never interpreted. NBT payloads are raw bytes — never deserialized. All MSF parsing, encoding, checksum, compression, and data model logic lives here.

**msf-fabric:** Resolves MSF strings against Minecraft registries. Contains zero MSF parsing logic — delegates entirely to msf-core.

**msf-cli:** Standalone CLI tool. Depends on msf-core only. Inspect, validate, and convert subcommands. Supports .nbt and .litematic format conversion.

## Technical Baseline

- Java 21 — records, sealed classes, pattern matching, switch expressions. No preview features.
- Gradle with Kotlin DSL, version catalog
- Minecraft 1.21.1, Fabric Loader 0.16.5+, Fabric API 0.102.0+1.21.1
- In-house NBT reader/writer (no external NBT library)

## Branch Model

- `main` — tagged releases only
- `develop` — integration branch
- `feature/*` — feature work off develop
- `release/vX.Y.Z` — release prep
- `fix/*` — bugfixes

## Studio Workflow

This project uses an AI studio workflow with gate documents.

- **Planning** → `docs/gates/vX.Y.Z/PLANNING_GATE.md`
- **Format spec rounds** → `docs/gates/vX.Y.Z/FORMAT_GATE.md`
- **Implementation** → `docs/gates/vX.Y.Z/IMPLEMENTATION_GATE.md`
- **Release** → managed by app-release skill

Current state is always in `docs/STUDIO_STATE.md`. Read it first.

## Key Design Decisions

- Spec gates implementation — no coding session begins until the preceding spec round closes with no critical issues
- Core header is frozen permanently — 48 bytes, no exceptions
- Unknown blocks are always skippable via length prefixes
- Minor versions are always backwards compatible — spec guarantee
- Palette ID 0 is always AIR — format invariant
- YZX block ordering for cache-friendly vertical operations
- UUID remapping on paste is a spec requirement
- Pending ticks from Litematica are silently dropped — rested-state placement is correct behavior
- Property ordering in converters uses accept-and-warn rather than bundling blocks.json

## Environment Constraints (coding sessions)

- Network access is disabled — no apt, brew, curl, wget, pip, npm
- Gradle wrapper must be written directly — do not run `gradle wrapper`
- LZ4 coordinates are `org.lz4:lz4-java` (not `net.jpountz.lz4:lz4-java`)
- Root `build.gradle.kts` must include `apply(plugin = "maven-publish")` in subprojects block
- `settings.gradle.kts` must include pluginManagement block with Fabric Maven

## Code Conventions

- Records for immutable model classes
- Builders for complex object construction
- `Optional<T>` over null returns on public APIs
- `@NotNull` / `@Nullable` on all public method signatures
- Unmodifiable collections from public APIs
- 4 space indentation, 120 character line limit
- Conventional Commits: `feat:` `fix:` `docs:` `test:` `refactor:`
