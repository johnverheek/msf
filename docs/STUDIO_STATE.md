# Studio State

## Project
MSF (Minecraft Structured Format)
Repo: https://github.com/johnverheek/msf

## Current Version
v1.0.0 — Released (Staged)

## Spec
Revision: V1_O
Status: Stable — no open issues.

## Implementation
Sessions completed: 1–13f
Last session: Session 13f — BlockPosArgument relative coordinates, gametest API migration to Fabric 0.141.x, debug cleanup

## Active Branch
develop (post-merge)

## Release Status
Tag: v1.0.0 — created on main
Publishing: Staged
- GitHub Release: msf-cli fat-jar attached (GITHUB_TOKEN, no secrets required)
- Maven Central: deferred — add MAVEN_USERNAME, MAVEN_PASSWORD, GPG_KEY, GPG_PASSWORD secrets when ready
- Modrinth: deferred — create mod page, replace PLACEHOLDER_MODRINTH_PROJECT_ID in release.yml, add MODRINTH_TOKEN
- CurseForge: deferred — create mod page, replace PLACEHOLDER_CURSEFORGE_PROJECT_ID in release.yml, add CURSEFORGE_TOKEN

## Next Planning Items (v1.1.0)
- Minecraft 26.1 port (after ecosystem stabilises — track Sodium and Litematica 26.1 releases as trigger)
- Placement rotation based on player facing in /msf place
- Entity capture policy — living mob exclusion option in /msf extract
- /msf list and /msf preview in-game commands
- Layers support in /msf extract (multi-layer extraction)
- .schem (MCEdit) and Sponge format conversion in msf-cli
- msf-cli output header (app version, format version, MC data version)
- README benchmark table and install documentation (app-documentation task)

## Deferred to v1.2.0+
- NeoForge and Quilt multi-loader support (new msf-neoforge module)

## Won't Fix
- In-house NBT external library fallback — deliberate design decision
