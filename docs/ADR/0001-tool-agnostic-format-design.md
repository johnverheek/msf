# ADR-0001: Tool-Agnostic Format Design

**Status:** Accepted
**Date:** 2026-03-01

## Context

Minecraft schematic formats have historically been tied to specific tools (WorldEdit's `.schem`, Litematica's `.litematic`, NBTEdit's `.nbt`). This creates fragmentation — schematics are not portable between tools, and tool authors cannot build on each other's work without format translation layers.

MSF needs to be a format any tool can implement, with no single tool or organization owning it.

## Decision

MSF is a binary file format specification. No tool owns it. Any tool may implement it. The specification is the authority, not any particular implementation.

Consequences of this decision that are baked into the format design:
- The spec defines binary layout and encoding. It does not define rendering, viewport behavior, or UI.
- The spec ships with a reference implementation (this repository) but the reference implementation has no special authority over other implementations.
- Blockstate strings are stored as opaque UTF-8 in the format. Interpretation against Minecraft registries is a tool concern.
- The versioning contract (Section 12 of the spec) is a binding commitment to all implementers, not just this codebase.

## Rationale

Tying a format to a tool creates a single point of failure. If the tool is abandoned, the format becomes inaccessible. If the tool makes a breaking change, all dependent tools break. A tool-agnostic format survives tool churn.

The builder community works across multiple tools. A portable format lowers the barrier to sharing schematics across the entire ecosystem rather than within a single tool's user base.

## Consequences

- The spec must be stable and versioned independently of any implementation
- The reference implementation must not assume capabilities beyond what the spec defines
- msf-core must be publishable as a standalone Maven library with no mod loader dependency
- Future breaking changes require a major version increment — a high bar by design
