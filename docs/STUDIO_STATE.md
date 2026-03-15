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
Session 13b: Port msf-fabric 1.21.1 → 1.21.11
Skill: code-mc-implementation
Gate: docs/gates/v1.0.0/PLANNING_GATE.md

## v1.0.0 Release Scope

### Completed
- Session 13: EntityBridge, BlockEntityBridge, RegionExtractor/RegionPlacer entity+block entity support, feature flags, round-trip gametests, canonical test vectors (commit 1fb89dd)
- CI/CD: release.yml with 4-job workflow, credential-absent skip behavior, Maven Central POM, signing

### Session 13b (code-mc-implementation) — next
- Port msf-fabric from Minecraft 1.21.1 to 1.21.11
- Target: Fabric API 0.141.3+1.21.11, Fabric Loader 0.18.4
- Fix mapping renames in bridge and command classes (crash confirmed at MsfCommands.java:186)
- Update fabric.mod.json suffix to +1.21.11
- Owner runs in-game RTM on 1.21.11 after this session

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
