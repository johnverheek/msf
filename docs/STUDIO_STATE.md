# Studio State — MSF (Minecraft Structured Format)

## Project

| Field | Value |
|-------|-------|
| Repo | https://github.com/johnverheek/msf |
| Type | Ecosystem (format spec + reference implementation) |
| License | MIT |
| Version Target | v1.0.0 |
| Phase | Implementation — pre-release |

## Deliverables

| Deliverable | Description | Status |
|-------------|-------------|--------|
| MSF Specification V1 | Binary format spec for Minecraft schematics | V1_N closed |
| msf-core | Pure Java 21 library — parsing, encoding, checksum, compression | Sessions 1–12 complete |
| msf-fabric | Fabric 1.21.1 bridge — registry resolution, extract/place commands | Sessions 1–12 complete |
| msf-cli | Standalone CLI — inspect, validate, format conversion | Sessions 9–11 complete |
| msf-neoforge | NeoForge bridge | Not started |

## Spec Status

| Revision | Status | Notes |
|----------|--------|-------|
| V1_A–V1_G | Closed | Initial spec stabilization |
| V1_H | Closed | Pre-Session 2 review |
| V1_I–V1_N | Closed | Post-implementation refinements through Session 12 |

No open spec issues. Spec is stable for v1.0.0 gating.

## Session History

| Session | Scope | Status |
|---------|-------|--------|
| 1 | Gradle scaffold, MsfException hierarchy, MsfWarning/Code, MsfHeader, MsfReader/MsfWriter (header only), JUnit 5 tests | Complete |
| 2 | Global palette, metadata block, layer index, region encoding/decoding, bit packing, compression, biome data | Complete |
| 3 | Entity block, block entity block, full MsfFile round trip | Complete |
| 4–8 | Iterative implementation and spec-driven refinements | Complete |
| 9 | msf-cli module with inspect/validate subcommands | Complete |
| 10 | V1_N cleanup, .nbt ↔ .msf conversion with in-house NBT reader/writer | Complete |
| 11 | .litematic ↔ .msf conversion with intermediate routing | Complete |
| 12 | In-game /msf extract and /msf place commands for round-trip validation | Complete |

Test suite: 269+ tests across all three modules.

## Branch Model

| Branch | Purpose |
|--------|---------|
| `main` | Tagged releases only. Always deployable. |
| `develop` | Integration branch. All feature work merges here first. |
| `feature/*` | Feature branches off `develop`. |
| `release/vX.Y.Z` | Release prep — final fixes, version bumps, changelog. Merges to `main` and back to `develop`. |
| `fix/*` | Bugfix branches off `develop` (or `main` for hotfixes). |

## What's Next

The project needs a v1.0.0 planning gate to sequence remaining work before first release. Known areas to assess:

- CI/CD pipeline (GitHub Actions — build, test, publish)
- Release configuration (Maven Central for msf-core, Modrinth/CurseForge for msf-fabric)
- NeoForge port (scope decision: v1.0.0 or deferred)
- Documentation (README finalization, user guide, spec site)
- Community presence (Modrinth/CurseForge mod pages)
- Any remaining spec or implementation gaps surfaced during planning
