# Release Gate — v1.0.0

## Release Summary
v1.0.0 is the first public release of MSF (Minecraft Structured Format). It ships a complete implementation of the V1_N specification — bit-packed, compressed, globally-paletteed schematic storage with full entity and block entity support across extract and place workflows. The core library is pure Java 21 with no Minecraft dependency and publishes to Maven Central. The Fabric bridge publishes to Modrinth and CurseForge. The CLI tool provides inspect, validate, and format-conversion subcommands. This release makes the format available for adoption by any tool author or builder mod.

## Version
v1.0.0 — first stable public release; initial minor version under the V1 format series.

## Breaking Change
Breaking: No

## Artifacts
| Artifact | Target | Status |
|----------|--------|--------|
| msf-core-1.0.0.jar | Maven Central (dev.msf:msf-core:1.0.0) | Pending CI/CD |
| msf-core-1.0.0-sources.jar | Maven Central | Pending CI/CD |
| msf-core-1.0.0-javadoc.jar | Maven Central | Pending CI/CD |
| msf-fabric-1.0.0+1.21.1.jar | Modrinth | Pending CI/CD |
| msf-fabric-1.0.0+1.21.1.jar | CurseForge | Pending CI/CD |
| msf-cli-1.0.0.jar | GitHub Release (fat-jar) | Pending CI/CD |

## Checklist
- [x] All P0 stories delivered (entity extraction, block entity extraction, feature flags wired, test vectors)
- [ ] **RegionPlacer entity and block entity placement — owner confirm**
- [ ] **In-game round-trip RTM verification (Stories 3.2 and 3.3) — owner run**
- [ ] P1 stories — EntityBridge / BlockEntityBridge presence confirmed by owner
- [x] P1 stories deferred — install docs and benchmark table deferred to app-documentation task (non-blocking)
- [x] Breaking change flag: No — consistent across planning gate and implementation
- [ ] Version strings consistent — owner verify (see Version String Checklist below)
- [ ] `./gradlew clean build` passes on feature/v1.0.0-entity-bridge
- [ ] No SNAPSHOT or dev suffix in any version field
- [x] CHANGELOG.md entry drafted (below)
- [x] Developer changelog complete
- [x] Player changelog drafted
- [x] Spec changelog note drafted (no normative changes)
- [ ] CI/CD pipeline configured — **pending code-release handoff**
- [ ] Modrinth and CurseForge release pages configured — **pending code-release and app-documentation**
- [ ] Release branch created: release/v1.0.0
- [ ] Owner approval
- [ ] Publishing decision: Published / Staged / Deferred

## Version String Checklist
Verify these files before creating the release branch:

| File | Field | Expected Value |
|------|-------|----------------|
| msf-core/build.gradle.kts | version | 1.0.0 |
| msf-fabric/build.gradle.kts | version | 1.0.0+1.21.1 |
| msf-cli/build.gradle.kts | version | 1.0.0 |
| msf-fabric/src/main/resources/fabric.mod.json | version | 1.0.0+1.21.1 |
| gradle.properties or root build.gradle.kts | mod_version or version | 1.0.0 |

## Published Artifacts
(Fill in after publish — URLs for each artifact)

---

## Release Notes

### For Players and Builders

**MSF v1.0.0 — First Public Release**

MSF (Minecraft Structured Format) is a new schematic format for Minecraft 1.21.1. It is tool-agnostic — designed so that any mod, tool, or utility can implement it, with no single tool owning the format.

**What ships in v1.0.0:**

- **Full extract and place support** — use `/msf extract` and `/msf place` in-game to save and restore structures including entities (armor stands, item frames, and more) and block entities (chests with contents, signs, and more).
- **Format conversion** — the `msf-cli` command-line tool converts between `.msf`, vanilla `.nbt`, and Litematica `.litematic` files.
- **Inspect and validate** — `msf-cli inspect` and `msf-cli validate` let you examine and verify any `.msf` file without Minecraft running.
- **Smaller files** — zstd compression and a global block palette produce significantly smaller files than raw `.nbt` or `.litematic` for typical structures.

**Installing:**
Requires Fabric Loader 0.16.5+ and Fabric API 0.102.0+1.21.1 for Minecraft 1.21.1.

**Basic usage:**
- Stand at one corner of your structure and run `/msf extract <x1> <y1> <z1> <x2> <y2> <z2> <filename>`
- To place: run `/msf place <filename>` facing the direction you want

**Schematics are saved in the `msf-schematics/` folder** in your server or world run directory.

---

## Developer Changelog

### [1.0.0] — 2026-03-14

#### feat: msf-core — complete V1_N spec implementation
- Global palette block read/write with AIR-at-index-0 invariant enforced
- Layer index block read/write, dependency validation, construction order
- Region data encoding and decoding: bit-packing, YZX ordering, all four compression types (none, zstd, lz4, brotli)
- Metadata block read/write including placement metadata, anchor, canonical facing, rotation compatibility, functional volume
- Entity block read/write: UUID stripping on write, typed position and entity type fields
- Block entity block read/write: anchor-relative coordinates, UUID stripping
- Biome data encoding (feature flag bit 2) stubbed for future use
- Header checksum (xxHash3-64 over bytes 0–39) and file checksum (xxHash3-64 over full content)
- `MsfWarning` / `Consumer<MsfWarning>` warning mechanism — zero stdout pollution
- `MsfException` hierarchy: MsfParseException, MsfVersionException, MsfChecksumException, MsfPaletteException, MsfCompressionException
- In-house NBT reader/writer — no external NBT library dependency

#### feat: msf-fabric — Fabric 1.21.1 bridge
- `/msf extract <x1> <y1> <z1> <x2> <y2> <z2> <filename>` — captures block regions, entities, and block entities
- `/msf place <filename>` — places block regions, spawns entities with new UUIDs, restores block entities
- `RegionExtractor` — converts Minecraft world volumes to MSF model; entity and block entity capture
- `RegionPlacer` — converts MSF model to Minecraft world; entity spawning with UUID reassignment
- `BlockStateBridge`, `EntityBridge`, `BlockEntityBridge` — bidirectional conversion between Minecraft types and MSF model types

#### feat: msf-cli — standalone CLI tool
- `msf inspect <file>` — human-readable file structure summary
- `msf validate <file>` — full checksum and structural validation with exit code
- `msf convert <input> <output>` — format conversion: `.msf` ↔ `.nbt` ↔ `.litematic`

#### test: canonical test vectors
- `minimal.msf` — single layer, single region, compression type 0x00 (none)
- `zstd.msf` — compression type 0x01
- `lz4.msf` — compression type 0x02
- `brotli.msf` — compression type 0x03
- `entities.msf` — feature flag bits 0 and 1 set, one entity, one block entity

#### test: 269+ unit tests across all three modules

---

## Spec Changelog Note

No normative changes in v1.0.0. The implementation now fully covers the V1_N specification. Spec revision remains V1_N. No action required for independent spec implementors.

---

## Git Commands

Run after owner approval, after CI/CD pipeline is verified passing, and after all checklist items are confirmed:

```
git checkout develop
git merge --no-ff feature/v1.0.0-entity-bridge
git push origin develop
git checkout -b release/v1.0.0
git push origin release/v1.0.0
```

After CI passes on release/v1.0.0 and owner gives final approval:

```
git checkout main
git merge --no-ff release/v1.0.0
git tag -a v1.0.0 -m "Release v1.0.0"
git push origin main --tags
git checkout develop
git merge --no-ff main
git push origin develop
git branch -d release/v1.0.0
git push origin --delete release/v1.0.0
```

## Communication
No breaking changes. No spec changes. First public release — standard mod page announcement on Modrinth and CurseForge. No external implementor outreach required at this stage.
