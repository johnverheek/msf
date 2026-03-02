# ADR-0003: Two-Module Architecture (msf-core / msf-fabric)

**Status:** Accepted
**Date:** 2026-03-01

## Context

The MSF reference implementation needs to be useful both as a standalone Java library (for tool authors who don't use Fabric) and as a Fabric mod dependency (for mod authors who want native Minecraft integration).

A single module containing both Minecraft API calls and format parsing logic cannot be published to Maven Central without pulling in the entire Minecraft dependency graph. It also cannot be used by non-Fabric tools.

## Decision

Split the implementation into two modules with a strict one-way dependency:

**msf-core:** Pure Java 21 library. Zero Fabric or Minecraft dependencies. All format logic lives here. Published to Maven Central. Any Java tool can depend on it.

**msf-fabric:** Fabric bridge. Depends on msf-core. Resolves MSF opaque strings against Minecraft registries. Converts between MSF model types and Minecraft types. Contains zero MSF parsing logic — delegates entirely to msf-core. Published to Modrinth and CurseForge.

The boundary is enforced by the module dependency declaration: msf-fabric depends on msf-core, but msf-core declares no dependency on msf-fabric or any Minecraft artifact.

## Rationale

This is the minimum viable split that achieves both publishing targets. A tool author building a standalone desktop schematic editor can depend on `dev.msf:msf-core` from Maven Central without any Fabric involvement. A mod author can depend on msf-fabric and get native Minecraft type integration without re-implementing any format logic.

Any Minecraft version upgrade (1.21 → 1.22) touches only msf-fabric. msf-core is version-stable against Minecraft changes because it treats all Minecraft-specific strings as opaque UTF-8.

## Consequences

- The module boundary is non-negotiable — violations are caught in code review
- Blockstate strings, entity type strings, and biome identifiers are opaque in msf-core
- NBT payloads are raw bytes in msf-core — msf-fabric handles deserialization
- UUID stripping from binary NBT is implemented in msf-core (UuidStripper) because the binary NBT format is stable and does not require Minecraft API access
- Adding a new Minecraft version requires a new msf-fabric artifact but no change to msf-core
