# Format Gate — v1.0.0
Revision: V1_O

## Round Summary
Corrective amendment round targeting Section 8.1. Two 🔴 issues identified and resolved. Issue A corrects the missing coordinate space qualifier on entity position fields — the root cause of the entity placement bug found during in-game RTM. Issue B corrects the incomplete NBT payload exclusion list, which omitted rotation data despite yaw and pitch being present as typed fields. Both fixes are editorial corrections to normative description only — no binary layout changes. The implementation (Session 13e) was already correct; the spec now matches it.

## Issues Closed

| ID | Severity | Summary | Resolution |
|----|----------|---------|------------|
| A | 🔴 | Section 8.1 entity position fields missing "relative to anchor" qualifier — independent implementations would store absolute world coordinates | Added "(relative to anchor)" to all three f64 position field labels in Section 8.1 layout table. Added normative paragraph in Section 8.2 requiring anchor-relative storage on write and anchor-offset application on read. Added explicit prohibition on storing absolute world coordinates. |
| B | 🔴 | Section 8.1 NBT payload exclusion list omits rotation — "excludes position and type" does not cover yaw/pitch typed fields, allowing divergent implementations to include rotation in NBT payload | Updated layout comment from "excludes position and type" to "excludes position, rotation, and type". Added normative sentence in Section 8.2 requiring writers to strip rotation data from entity NBT before writing. |

## Issues Deferred
None.

## Spec Status
Open 🔴 issues: 0

## Implementation Notes
Session 13e fixed both defects in the reference implementation before this spec round ran. `EntityBridge.fromEntity()` strips `Pos` and `Rotation` from entity NBT and stores anchor-relative position values. `EntityBridge.toMinecraft()` applies the placement anchor offset to stored position values before spawning. The spec now matches the implementation. No code changes required as a result of this round.

## Gating Statement
No 🔴 issues remain open. Spec is ready to gate v1.0.0 release.
