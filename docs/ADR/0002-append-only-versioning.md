# ADR-0002: Append-Only Versioning Contract

**Status:** Accepted
**Date:** 2026-03-01

## Context

Binary format versioning is hard to get right. Common failure modes:
- Removing features breaks existing readers silently
- Reassigning flag bits corrupts data in old files read by new code
- Requiring all readers to understand all versions creates an impossible maintenance burden

MSF needs a versioning model that gives implementers a strong guarantee: code written today will not be broken by a future version of the spec.

## Decision

MSF V1 evolves by addition only. Features are never removed. Feature flag bits are never reassigned. Block types are never retired. Field meanings within defined blocks never change.

The minor version is a capability advertisement, not a compatibility gate. Any V1 reader can read any V1 file. A V1.0 reader encountering a V1.7 file skips unknown blocks via their length prefix and reads everything it understands.

The header is permanently frozen at 48 bytes. No future V1 minor version will alter it.

A major version increment is reserved for structural emergencies — situations where the core format has a flaw so fundamental it cannot be corrected by addition. The bar is extraordinarily high.

## Rationale

Mod developers and tool authors cannot track a fast-moving format. They need confidence that a schematic saved today will be readable by their tool next year without a code update. The append-only rule gives them that confidence.

The block length prefix mechanism (every block starts with a u32 byte count) is the mechanical guarantee that makes the append-only rule enforceable. Without it, readers cannot skip unknown blocks and the rule collapses.

## Consequences

- Every block in the format must begin with a u32 block length field — this is non-negotiable
- Reserved feature flag bits 10–31 may be assigned meaning in future minor versions but never in conflicting ways
- Deprecated features stay in the binary contract permanently
- Major version increments fracture the ecosystem and should be treated as architectural failures of the original design
- The spec's Section 12 versioning contract is normative and binding on this codebase as much as on any other implementer
