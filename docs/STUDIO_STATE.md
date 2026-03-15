# Studio State

## Project
MSF (Minecraft Structured Format)
Repo: https://github.com/johnverheek/msf

## Current Version
v1.0.0 — In progress (pre-release)

## Spec
Revision: V1_N
Status: Stable — no open 🔴 issues. No spec gate required for v1.0.0.

## Implementation
Sessions completed: 1–13
Last session: Session 13 — entity & block entity bridge, canonical test vectors
Test count: msf-core 201+ | msf-cli 35 | msf-fabric 41 gametests

## Active Branch
feature/v1.0.0-entity-bridge

## Next Action
Merge feature/v1.0.0-entity-bridge → develop, then release phase (app-release skill)
Gate: docs/gates/v1.0.0/IMPLEMENTATION_GATE.md (complete)

## v1.0.0 Release Scope

### Session 13 ✅ Complete
- RegionExtractor.extractEntities() / extractBlockEntities() wired in executeExtract
- MsfWriter.writeFile(MsfFile, CompressionType, Consumer) overload added
- Canonical test vectors committed: minimal, zstd, lz4, brotli, entities
- 5 canonical vector validation tests in MsfReferenceFileTest
- 4 new gametests: armor stand round-trip, chest round-trip, feature flags, no-entity place

### code-release task (after Session 13)
- GitHub Actions: Maven Central publish for msf-core on tag push
- GitHub Actions: Modrinth + CurseForge publish for msf-fabric on tag push
- GitHub Release: msf-cli fat-jar attached as release asset
- README badges: Maven Central, Modrinth, CurseForge

### app-documentation task (parallel with code-release)
- README benchmark table: .msf vs .litematic vs .nbt file sizes (3+ test cases)
- README install section with /msf extract and /msf place usage examples

## Deferred to v1.1.0
- /msf list and /msf preview in-game commands
- Layers support in /msf extract (multi-layer extraction)
- .schem (MCEdit) and Sponge format conversion in msf-cli
- Rotation and mirror flags in /msf place
- Mod Menu integration

## Deferred to v1.2.0+
- NeoForge / Quilt multi-loader support (new msf-neoforge module)

## Won't Fix
- In-house NBT external library fallback — deliberate design decision for interface consistency
