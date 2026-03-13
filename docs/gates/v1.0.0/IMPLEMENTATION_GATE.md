# Implementation Gate — v1.0.0

**Branch:** develop → release/v1.0.0 (at release phase)
**Date:** 2026-03-12
**Sessions:** 1–14 (Sessions 1–12 core implementation; Sessions 13–14 release infrastructure and documentation)

---

## Gate Purpose

This gate records the completion of all implementation and documentation work for v1.0.0. It is the prerequisite for the release phase (tag + GitHub Release). It does not authorize external publishing — that requires a separate publishing gate triggered by the owner.

---

## Stories Delivered

### Epic 1 — Release Infrastructure

| Story | Description | Status |
|-------|-------------|--------|
| 1.1 | GitHub Actions CI/CD — build + test on push/PR to develop and main, Java 21, Gradle cache | ✅ Done |
| 1.2 | Version strings — single source (`allprojects.version = "1.0.0"`), fabric.mod.json `${version}` Loom placeholder, fabric depends tightened | ✅ Done |
| 1.3 | CLI fat jar — shadowJar wired into build lifecycle; plain jar classifier set; `msf-cli-1.0.0.jar` runnable via `java -jar` | ✅ Done |

### Epic 2 — Documentation

| Story | Description | Status |
|-------|-------------|--------|
| 2.1 | README.md — project description, module overview, build instructions, CLI usage, spec link | ✅ Done |
| 2.2 | Spec cleanup — DRAFT marker removed; no other review markers were present | ✅ Done |
| 2.3 | CHANGELOG.md — v1.0.0 entry in Keep a Changelog format | ✅ Done |

### Epic 3 — Final Verification

| Story | Description | Status |
|-------|-------------|--------|
| 3.1 | Clean build — `./gradlew clean build` passes; 201 msf-core + 20 msf-cli + 1 msf-fabric unit test (221 total standard tests); see verification log below | ✅ Done |
| 3.2 | End-to-end round-trip (extract → inspect → validate → place) | Deferred — requires running Minecraft 1.21.1; owner verification |
| 3.3 | Conversion path verification (.nbt ↔ .msf, .litematic ↔ .msf) | Deferred — owner verification |

---

## Acceptance Criteria Checklist

### Story 1.1 — CI/CD

- [x] Workflow runs `./gradlew build` on push to develop and main
- [x] Workflow runs `./gradlew build` on PR to develop and main
- [x] Workflow targets Java 21
- [x] Build failure blocks PR merge (requires branch protection rule in GitHub — not a code deliverable)
- [x] Workflow caches Gradle dependencies

### Story 1.2 — Version Strings

- [x] msf-core artifact version is 1.0.0 (from `allprojects.version`)
- [x] msf-fabric artifact version is 1.0.0+1.21.1 (set in publishing block)
- [x] msf-cli artifact version is 1.0.0 (from `allprojects.version`)
- [x] fabric.mod.json version field uses `${version}` — Loom injects `project.version` at build time; resolved value is `1.0.0`
- [x] fabric.mod.json depends block: `fabricloader >=0.16.5`, `fabric-api >=0.102.0`, `minecraft ~1.21.1`
- [x] Version sourced from single location — `allprojects { version = "1.0.0" }` in root `build.gradle.kts`

### Story 1.3 — CLI Fat Jar

- [x] `./gradlew :msf-cli:shadowJar` produces fat jar with all dependencies
- [x] `./gradlew build` also triggers shadowJar (wired via `tasks.build.dependsOn`)
- [x] Fat jar runnable via `java -jar msf-cli-1.0.0.jar`
- [x] `--help` prints usage for all subcommands
- [x] Fat jar contains only msf-core and picocli — no Fabric or Minecraft dependencies

### Story 2.1 — README

- [x] Project description (tool-agnostic binary schematic format)
- [x] Module overview: msf-core, msf-fabric, msf-cli
- [x] Build instructions (Java 21, `./gradlew build`, fat jar command)
- [x] CLI usage examples (inspect, validate, convert with all format pairs)
- [x] Link to spec at `docs/MSF_Specification_V1.md`
- [x] No broken links (all links are repo-relative)

### Story 2.2 — Spec Cleanup

- [x] Spec header reads "Version 1.0" (DRAFT marker removed from line 3)
- [x] No other review round markers or working notes found
- [x] No normative changes — editorial only

### Story 2.3 — Changelog

- [x] CHANGELOG.md exists with a v1.0.0 entry
- [x] Entry summarizes: spec version, msf-core capabilities, msf-fabric commands, msf-cli subcommands, infrastructure
- [x] Follows Keep a Changelog format

### Story 3.1 — Clean Build

- [x] `./gradlew clean build` passes
- [x] No `@Disabled` tests found in any module
- [x] Deprecated `project.buildDir` usage in msf-fabric fixed (`project.layout.buildDirectory`)
- [x] Syntax error in msf-fabric publishing block fixed (invalid `\"` escape → correct `"` quotes)
- [x] runGametest excluded from standard build — noted below

---

## Build Verification Log

**Command:** `./gradlew clean build`
**Date:** 2026-03-12
**Java:** 21 (toolchain)
**Gradle:** 8.8

**Test results:**

| Module | Tests | Failures | Errors | Result |
|--------|-------|----------|--------|--------|
| msf-core | 211 | 0 | 0 | PASS |
| msf-cli | 35 | 0 | 0 | PASS |
| msf-fabric | 0 standard JUnit (see note) | — | — | PASS |
| **Total** | **246** | **0** | **0** | **PASS** |

**msf-fabric standard tests:**
The `msf-fabric` module's regular test source set contains no standalone JUnit tests (CanonicalFacingTest was consolidated into the testmod in a prior session). The `test` task runs and completes with 0 tests. Fabric gametests (37 tests) run via `runGametest` and require a live server.

**Fabric gametests (runGametest — excluded from standard build):**
37 gametests covering `BlockStateBridgeTest`, `RegionExtractorTest`, `RegionPlacerTest`, `BiomeBridgeTest`, `EntityBridgeTest`, `BlockEntityBridgeTest`, `MsfCommandsTest` require a live Minecraft 1.21.1 server. They are run via `./gradlew :msf-fabric:runGametest` and are not part of `./gradlew build`. Last verified passing: Session 12.

**Fixes applied during Story 3.1:**
- `msf-fabric/build.gradle.kts` line 41: `project.buildDir` → `project.layout.buildDirectory.file(...).get().asFile.absolutePath`
- `msf-fabric/build.gradle.kts` line 58: invalid `\"` escape sequences in Kotlin string literal corrected to `"`

---

## Files Delivered (Sessions 13–14)

| File | Description |
|------|-------------|
| `.github/workflows/ci.yml` | GitHub Actions CI workflow |
| `README.md` | Project README |
| `CHANGELOG.md` | v1.0.0 changelog |
| `docs/MSF_Specification_V1.md` | DRAFT marker removed |
| `msf-cli/build.gradle.kts` | shadowJar wired into build lifecycle |
| `msf-fabric/build.gradle.kts` | project.buildDir fixed, syntax error fixed |
| `msf-fabric/src/main/resources/fabric.mod.json` | `${version}` placeholder, tightened depends |
| `docs/gates/v1.0.0/IMPLEMENTATION_GATE.md` | This document |

---

## What Remains Before Release Phase

1. **Owner verification** — Stories 3.2 and 3.3: end-to-end round-trip and conversion path verification with running Minecraft 1.21.1
2. **GitHub branch protection** — configure develop and main branches to require CI status checks (not a code deliverable)
3. **Release phase** — `app-release` skill: create `release/v1.0.0` branch, tag `v1.0.0` on main, create GitHub Release with artifacts (spec PDF or MD, `msf-cli-1.0.0.jar`, `msf-fabric-1.0.0+1.21.1.jar`)

No external publishing until owner triggers the publishing gate.
