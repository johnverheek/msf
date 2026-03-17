# Studio State

## Project
MSF (Minecraft Structured Format)
Repo: https://github.com/johnverheek/msf

## Current Version
v1.1.0 — In Implementation

## Spec
Revision: V1_O
Status: Stable — no open issues. No spec changes required for v1.1.0.

## Implementation
Sessions completed: 1–15
Last session: Session 15 — /msf place --rotate and --mirror flags (Epic 3)

## Active Branch
feature/v1.1.0

## Release Status
v1.0.0 — Tag created on main. Staged publishing.
- GitHub Release: msf-cli fat-jar attached
- Maven Central: deferred (secrets not configured)
- Modrinth: deferred (mod page not created)
- CurseForge: deferred (mod page not created)

## Platform Policy
Target: Minecraft 1.21.11, Fabric API 0.141.3+1.21.11, Fabric Loader 0.18.4
Policy: Will remain on 1.21.11 until owner declares readiness for current-version maintenance cycle. Minecraft 26.1 port deferred to v1.2.0. Trigger: Sodium and Litematica stable on 26.1 AND owner sign-off.

## Next Planning Items (v1.2.0)
- Minecraft 26.1 port (trigger: Sodium + Litematica stable on 26.1, owner sign-off)
- Ghost block preview rendering (v1.1.0 delivers bounding box; ghost block is v1.2.0 goal)
- NeoForge and Quilt multi-loader support (new msf-neoforge module)

## Deferred Indefinitely (owner decision)
- .schem (MCEdit) and Sponge format conversion

## Won't Fix
- In-house NBT external library fallback — deliberate design decision
