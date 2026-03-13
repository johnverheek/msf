# Format Gate — v1.1.0
Revision: V1_O

## Round Summary

V1_O is an editorial and gap-fill round driven by community feedback triaged in the v1.1.0 planning gate. One normative addition (region overlap rule in §6.4 with corresponding warning code in §3.5.1), one read-side obligation for circular dependencies (§6.4), and eight non-normative or editorial additions. No binary wire format changes. No changes to existing normative requirements.

## Issues Closed

| ID | Severity | Summary | Resolution |
|----|----------|---------|------------|
| O-1 | 🟡 | Region overlap rule missing (§6.4) | Added normative prohibition + OVERLAPPING_REGIONS warning code |
| O-2 | 🟠 | 4 GiB ceiling unacknowledged (§3.6) | Added non-normative forward-compat note |
| O-3 | 🟠 | Lighting hints bit 3 undefined (§3.3) | Added stub paragraph noting layout TBD |
| O-4 | 🟡 | No test vector for cross-language implementers | Added Appendix G: 207-byte minimal file with annotated hex dump |
| O-5 | 🟠 | Per-region palette rationale missing (§4.1) | Added design rationale note |
| O-6 | 🟠 | No zstd level guidance (§7.2) | Added SHOULD recommendation for level 3, advisory for 6–9 |
| O-7 | 🟠 | No thumbnail dimension guidance (§5.2) | Added SHOULD recommendation for 256×256 or 128×128 |
| O-8 | 🟡 | Data version validation depth unclear (§4.3) | Added writer/reader obligations with converter exception |
| O-9 | ✏️ | Region name in §7.1 (V1_N alignment) | Verified removal applied |
| O-10 | ✏️ | Warning table update for OVERLAPPING_REGIONS | Applied with O-1 |
| O-11 | ✏️ | Circular dependency lacks reader obligation (§6.4) | Added reader warning + fallback |

## Issues Deferred

None.

## Spec Status

Open 🔴 issues: 0

## Implementation Notes

- Appendix G test vector: implementations MUST compute both xxHash3-64 checksums independently and verify against the structural bytes provided. The vector is 207 bytes total.
- OVERLAPPING_REGIONS warning code: implementations adding this code to MsfWarning.Code enum should detect overlap during layer index parsing by comparing each pair of regions within a layer for axis-aligned bounding box intersection where at least one shared block position exists.
- Data version validation (O-8): msf-core converters (NBT, Litematica) operate without Minecraft registries and are explicitly exempted from registry validation per the new §4.3 language. Only msf-fabric bridge performs registry validation.

## Gating Statement

No 🔴 issues remain open. Spec is ready to gate Sessions 13–14.
