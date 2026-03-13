# Spec Review Context — MSF

## Spec Location
Repo: https://github.com/johnverheek/msf
Read from: /mnt/project/docs/MSF_Specification_V1.md (Claude Code)
Write to: /mnt/user-data/outputs/MSF_Specification_V1.md — present for download after every round

## Revision History

| Round | Label | Key Changes | Status |
|-------|-------|-------------|--------|
| 1 | V1_A | Initial review | Closed |
| 2 | V1_B | Follow-up | Closed |
| 3 | V1_C | Follow-up | Closed |
| 4 | V1_D | Follow-up | Closed |
| 5 | V1_E | Follow-up | Closed |
| 6 | V1_F | Follow-up | Closed |
| 7 | V1_G | Follow-up | Closed |
| 8 | V1_H | Post-Session 1 review | Closed |
| 9 | V1_I | Follow-up | Closed |
| 10 | V1_J | Follow-up | Closed |
| 11 | V1_K | Follow-up | Closed |
| 12 | V1_L | Follow-up | Closed |
| 13 | V1_M | Bit-packing worked example, multi-layer Appendix, blockstate 1.13+ requirement, Java edition scope floor | Closed |
| 14 | V1_N | Removed region name from region header, corrected Appendix G byte counts | Closed |
| 15 | V1_O | Region overlap rule, 4 GiB note, lighting hints stub, test vector appendix, palette rationale, zstd level, thumbnail dimensions, data version validation, circular dependency reader obligation | Closed |

**Current round:** V1_O (closed)
**Last issue ID used:** O-11

## Terminology Table

| Term | Never use |
|------|-----------|
| Block (file section) | Chunk, segment, part |
| Block (Minecraft) | Cube, voxel |
| Region | Sub-region, subvolume (in normative text) |
| Layer | Phase (except in descriptive examples) |
| Palette | Registry (for the palette), dictionary, lookup |
| Reader | Parser, decoder, deserializer |
| Writer | Encoder, serializer |
| Feature flag | Capability flag, option bit |
| MC data version | Minecraft version (ambiguous — could mean release string) |
| Anchor point | Origin (ambiguous — regions have origins) |
| Canonical facing | Default facing, authored facing |

## Project-Specific Review Rules

- Reserved field handling: warn on read, clear-and-warn on write. Never reject.
- All multi-byte integers are little-endian. Magic bytes are individual bytes, not a multi-byte integer.
- The 48-byte header is permanently frozen. Any proposal to change header fields is a V2 discussion, not a V1 amendment.
- Feature flags are authoritative for optional block presence. Offset fields are secondary.
- Severity escalation: any formula, count, or byte boundary gap where two implementations would diverge is 🔴, never 🟡.
- Read/write symmetry is mandatory: every reader obligation has a writer counterpart and vice versa.

## Gating Dependency

Spec must be clean before Sessions 13–14 begin (msf-cli and msf-fabric enhancements for v1.1.0).
