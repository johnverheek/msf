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
Sessions completed: 1–12
Last session: Session 12 — in-game /msf extract and /msf place commands, round-trip validation
Test count: 269+

## Active Branch
feature/v1.0.0-entity-bridge

## Next Action
Session 13: Entity & BlockEntity fabric bridge
Skill: code-mc-implementation
Gate: docs/gates/v1.0.0/PLANNING_GATE.md

## v1.0.0 Release Scope

### Session 13 (code-mc-implementation)
- EntityBridge: Minecraft Entity ↔ MsfEntity conversion, UUID stripping enforced
- BlockEntityBridge: Minecraft BlockEntity ↔ MsfBlockEntity conversion, UUID stripping enforced
- RegionExtractor: extend to capture entities and block entities within selection bounds
- RegionPlacer: extend to spawn entities (new UUIDs) and restore block entities after block placement
- Feature flag bits 0 and 1 wired end-to-end in /msf extract and /msf place
- Canonical test vectors in test/resources (minimal, zstd, lz4, brotli, entities)

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
