# Planning Gate — v1.1.0
Track: Minor
Branch: feature/v1.1.0-cli-fabric-ux

## What This Release Is

v1.1.0 exposes msf-core's existing capabilities through improved CLI and Fabric user interfaces. The core library is unchanged — this release is entirely about making the tools match the quality of the format. CLI gains convert flags, richer inspect output, and JSON mode. Fabric commands gain explicit coordinates, rotation, layer filtering, and new list/info subcommands. All output surfaces clearly distinguish the MSF format version from the implementation version. The spec receives a small editorial round (V1_O) with gap fills and non-normative additions that do not change the binary wire format.

## Semver Rationale

Minor bump — all changes are additive backwards-compatible features in CLI and Fabric modules with no binary format changes or API breaks.

## Breaking Change
Breaking: No

## Scope

In:
- CLI convert flags (compressor, compression level, entities toggle, metadata override)
- CLI inspect enhancements (palette statistics, JSON output mode)
- CLI warnings exposure flag
- CLI and Fabric version disambiguation (format version vs implementation version)
- Fabric `/msf place` with explicit coordinates, rotation, mirror, layer filter
- Fabric `/msf extract` with layer/entities flags and name override
- Fabric `/msf list` paginated listing
- Fabric `/msf info <file>` in-chat inspect
- Spec round V1_O: region overlap rule, 4 GiB forward-compat note, lighting hints stub, test vector appendix, per-region palette rationale, zstd level recommendation, thumbnail dimension recommendation, data version validation guidance
- Documentation: README comparison table, Maven coordinates, example .msf files, CHANGELOG population, badges, diagrams, entity docs, CLI help output, fabric.mod.json authors, Modrinth/CurseForge page polish
- Infrastructure: GitHub Actions CI, CLI binary releases on GitHub

Out:
- msf-core changes (no core library modifications)
- /msf preview particle hologram (v1.2+)
- /msf from-structure-block (v1.2+)
- msf diff subcommand (v1.2+)
- msf batch recursive conversion (v1.2+)
- msf compose multi-file merge (v1.2+)
- NeoForge module (deferred until demand proven)
- Web demo viewer (out of scope)
- Config file for Fabric mod (v1.2+ P2)
- Pipe support in CLI (deferred)
- Non-normative use cases section in spec (v1.2+)

## Stories

### Epic 1 — Version Disambiguation (cross-cutting)

**Story V1.1**: As a user, I want to see the MSF format version and tool version labeled distinctly in all output, so that I know whether a version number refers to the file format or the software.

Priority: P0
Module: msf-cli, msf-fabric
Spec reference: §3.2
Breaking: No

Acceptance Criteria:
- [ ] `msf --version` outputs implementation version (e.g., "msf-cli 1.1.0") on its own line
- [ ] `msf inspect` output includes both "Format version: V1.0" (from file header major.minor) and "msf-cli: 1.1.0" (build version)
- [ ] `msf validate` output includes both format version and implementation version
- [ ] Fabric `/msf info` output includes both "Format: V1.0" and "msf-fabric: 1.1.0+1.21.1"
- [ ] Labels MUST use "Format" for the spec version and the tool module name for the implementation version — never bare "Version"
- [ ] Format version displays as "V{major}.{minor}" (e.g., "V1.0", "V1.3") — not semver

### Epic 2 — CLI Enhancements (msf-cli)

**Story C1.1**: As a builder, I want to choose the compressor and compression level when converting files, so that I can optimize for file size or speed.

Priority: P0
Module: msf-cli
Spec reference: §7.2
Breaking: No

Acceptance Criteria:
- [ ] `convert` accepts `--compressor zstd|lz4|brotli|none` flag, defaults to zstd
- [ ] `convert` accepts `--compression-level <int>` flag, defaults to compressor's default
- [ ] Invalid compressor name produces a clear error message and non-zero exit code
- [ ] Invalid compression level for the chosen compressor produces a clear error message
- [ ] Round-trip test: convert with each compressor, validate output, verify identical block data

**Story C1.2**: As a builder, I want to toggle entity inclusion when converting, so that I can produce smaller files when entities are irrelevant.

Priority: P0
Module: msf-cli
Spec reference: §8, §9
Breaking: No

Acceptance Criteria:
- [ ] `convert` accepts `--entities true|false` flag, defaults to true
- [ ] When `--entities false`, output file has feature flag bits 0 and 1 cleared, entity and block entity blocks absent
- [ ] When source has no entities and `--entities true`, output correctly has no entity blocks (no empty blocks written)
- [ ] Feature flags in output header correctly reflect entity block presence

**Story C1.3**: As a builder, I want to override metadata fields during conversion, so that I can set author and name without editing after the fact.

Priority: P1
Module: msf-cli
Spec reference: §5
Breaking: No

Acceptance Criteria:
- [ ] `convert` accepts `--name <str>` and `--author <str>` flags
- [ ] Provided values override source metadata; omitted flags preserve source values
- [ ] Empty string for `--author` is valid (anonymous); empty `--name` produces error per §5.2

**Story C2.1**: As a builder, I want palette statistics in inspect output, so that I can identify inefficient palettes.

Priority: P1
Module: msf-cli
Spec reference: §4
Breaking: No

Acceptance Criteria:
- [ ] `inspect` output includes total palette entry count
- [ ] `inspect` output includes top 10 palette entries by frequency across all regions (with block count and percentage)
- [ ] Single-entry palette (air only) displays correctly

**Story C3.1**: As a developer, I want JSON output from inspect, so that I can integrate MSF inspection into CI pipelines and scripts.

Priority: P1
Module: msf-cli
Spec reference: —
Breaking: No

Acceptance Criteria:
- [ ] `inspect --format json` produces valid JSON to stdout
- [ ] `inspect --format text` produces current human-readable output (default)
- [ ] JSON schema includes: header fields, palette summary, layer list, region list, entity counts, format version, file checksums
- [ ] JSON output is stable — field names do not change within a minor version

**Story C5.1**: As a developer, I want to see parser warnings in CLI output, so that I can diagnose non-conforming files.

Priority: P1
Module: msf-cli
Spec reference: §3.5.1
Breaking: No

Acceptance Criteria:
- [ ] `inspect` and `validate` accept `--warnings` flag
- [ ] When `--warnings` is set, all `MsfWarning` instances are printed to stderr with code, message, and byte offset
- [ ] When `--warnings` is not set, warnings are silently consumed (current behavior)
- [ ] Warnings do not affect exit code (they are advisory)

### Epic 3 — Fabric Command Improvements (msf-fabric)

**Story F1.1**: As a builder, I want to place schematics at explicit coordinates with rotation and mirror options, so that I have precise control over placement.

Priority: P0
Module: msf-fabric
Spec reference: §10.2, §10.3
Breaking: No

Acceptance Criteria:
- [ ] `/msf place <filename> [<x> <y> <z>]` places at specified coordinates; omitting coordinates uses player position
- [ ] `/msf place` accepts `rotation 0|90|180|270` argument, defaults to 0
- [ ] `/msf place` accepts `mirror none|x|z` argument, defaults to none
- [ ] Rotation transforms all direction-dependent blockstate properties per §10.2
- [ ] Mirror transforms all axis-dependent blockstate properties
- [ ] Placement at explicit coordinates works for non-op players with appropriate permission level
- [ ] Invalid rotation value produces clear error message
- [ ] Round-trip test: extract → place with 90° rotation → extract → place with 270° rotation → compare with original

**Story F2.1**: As a builder, I want to filter placement by layer, so that I can place construction phases independently.

Priority: P1
Module: msf-fabric
Spec reference: §6
Breaking: No

Acceptance Criteria:
- [ ] `/msf place` accepts `layer <name>` argument
- [ ] When layer is specified, only regions belonging to that layer are placed
- [ ] Non-existent layer name produces clear error message listing available layers
- [ ] Omitting layer argument places all layers (current behavior)

**Story F3.1**: As a builder, I want extract flags for layer selection, entity inclusion, and name override.

Priority: P1
Module: msf-fabric
Spec reference: §5, §6, §8
Breaking: No

Acceptance Criteria:
- [ ] `/msf extract` accepts `layer <name>` argument to extract a single layer
- [ ] `/msf extract` accepts `entities true|false` argument, defaults to true
- [ ] `/msf extract` accepts `name <str>` argument to override the schematic name and filename
- [ ] Omitting all flags produces current behavior

**Story F4.1**: As a builder, I want to list available schematics in-game with pagination.

Priority: P1
Module: msf-fabric
Spec reference: —
Breaking: No

Acceptance Criteria:
- [ ] `/msf list` displays .msf files in the schematics directory with name, file size, and layer count
- [ ] Output is paginated at 8 entries per page with clickable next/previous navigation
- [ ] Empty directory produces "No schematics found" message
- [ ] Non-.msf files in the directory are ignored

**Story F5.1**: As a builder, I want to inspect a schematic in-game without leaving the game.

Priority: P1
Module: msf-fabric
Spec reference: —
Breaking: No

Acceptance Criteria:
- [ ] `/msf info <filename>` displays: name, author, format version, dimensions, layer names, palette entry count, entity count, compression type, file size
- [ ] Output is formatted for chat readability (no wall of text)
- [ ] Non-existent filename produces clear error message
- [ ] Format version and mod version displayed distinctly per V1.1

### Epic 4 — Spec Round V1_O (editorial + gap fills)

**Story SO.1**: Spec receives editorial round V1_O addressing community feedback.

Priority: P0 (gates Sessions 13–14)
Module: spec
Spec reference: multiple sections
Breaking: No

Acceptance Criteria:
- [ ] S1: Region overlap rule — normative statement added to §7.1 prohibiting overlap within a layer, defining writer/reader obligations
- [ ] S2: 4 GiB note — non-normative forward-compatibility note added after §3.6
- [ ] S4: Lighting hints stub — one-sentence note in §3.3 or new subsection noting bit 3 layout is TBD for future minor version
- [ ] S5: Test vector appendix — hex dump of minimal valid file (header + metadata + palette + 1 layer + 1 region + file checksum) with annotated byte offsets
- [ ] S8: Per-region palette rationale — one sentence in §4 explaining global palette choice
- [ ] S9: Zstd level recommendation — SHOULD note in §7.2
- [ ] S10: Thumbnail dimensions — SHOULD note in §5.2
- [ ] S11: Data version validation — clarification added to §3.4 or §4.3
- [ ] All additions pass Gate Close Checklist (RFC 2119 discipline, no ambiguous antecedents, testable requirements)

### Epic 5 — Documentation & Infrastructure

**Story D1.1**: As a potential adopter, I want a comparison table in the README, so that I can quickly understand why MSF over alternatives.

Priority: P0
Module: docs
Breaking: No

Acceptance Criteria:
- [ ] README contains MSF vs Litematica vs Sponge Schematic comparison table
- [ ] Table covers: tool ownership, forward compatibility, compression options, semantic layers, bit-packed palettes, pure library availability
- [ ] Table is factually accurate and neutral in tone

**Story D2.1**: As a developer, I want Maven coordinates and Gradle snippets in the README.

Priority: P0
Module: docs
Breaking: No

Acceptance Criteria:
- [ ] README contains Maven XML and Gradle KTS dependency snippets for msf-core
- [ ] Coordinates match published artifact (dev.msf:msf-core:1.1.0)
- [ ] Modrinth and CurseForge links included for msf-fabric

**Story D3.1**: As an implementer, I want example .msf files in the repository.

Priority: P1
Module: docs
Breaking: No

Acceptance Criteria:
- [ ] Repository contains at least 3 example .msf files in an examples/ directory
- [ ] Examples include: minimal (single block), small build (multi-layer), and one with entities
- [ ] Each example has a one-line description in the directory README

**Story D4.1**: As a user, I want a populated CHANGELOG reflecting v1.0.0 and v1.1.0.

Priority: P1
Module: docs
Breaking: No

Acceptance Criteria:
- [ ] CHANGELOG.md has a retroactive v1.0.0 section with bullet points for initial release features
- [ ] CHANGELOG.md has a v1.1.0 section covering all changes in this release
- [ ] Format follows Keep a Changelog conventions

**Story D5-D11**: Documentation polish bundle (badges, diagrams, entity docs, CLI help, fabric.mod.json authors, CI workflow, CLI binary releases, Modrinth page).

Priority: P1
Module: docs, infra
Breaking: No

Acceptance Criteria:
- [ ] README has build status, Maven Central, Modrinth, and license badges
- [ ] README includes at least one diagram (layer dependency flow or header layout)
- [ ] README documents entity/block entity support
- [ ] fabric.mod.json has populated authors array
- [ ] GitHub Actions workflow runs ./gradlew build on push/PR
- [ ] GitHub Releases includes CLI shadow JAR as binary asset
- [ ] Modrinth/CurseForge page has updated description reflecting v1.1.0 features

## Session Plan

**Spec round V1_O** — gates Sessions 13 and 14. Conducted in a spec review conversation. Produces FORMAT_GATE.md.

**Session 13**: msf-cli enhancements
- Stories: V1.1 (CLI portion), C1.1, C1.2, C1.3, C2.1, C3.1, C5.1
- Module: msf-cli
- Prerequisite: V1_O spec round closed

**Session 14**: msf-fabric command improvements
- Stories: V1.1 (Fabric portion), F1.1, F2.1, F3.1, F4.1, F5.1
- Module: msf-fabric
- Prerequisite: V1_O spec round closed

**Session 15**: Documentation and infrastructure
- Stories: D1.1, D2.1, D3.1, D4.1, D5-D11
- Module: docs, infra
- May run in parallel with Sessions 13-14

## Format Amendment Brief

Sections affected: §3.3, §3.4, §3.6, §4, §5.2, §7.1, §7.2, new Appendix
Change type: Additive (non-normative notes) + one normative gap fill (region overlap)
Summary: Community feedback identified a missing normative rule for region overlap within layers (S1) and several editorial gaps. V1_O adds a region overlap prohibition, forward-compat advisory notes for the 4 GiB ceiling and lighting hints bit, editorial recommendations for zstd level and thumbnail dimensions, data version validation guidance, per-region palette rationale, and a test vector appendix with annotated hex dump. No changes to the binary wire format. No existing normative requirements are altered.

## SME Skills Required

All required skills installed:
- app-format-spec — for V1_O spec round
- code-mc-implementation — for Sessions 13-14
- app-release — for release coordination
- app-documentation — for Session 15
- app-mc-community — for Modrinth/CurseForge page updates

## Gate Documents This Release Produces

- FORMAT_GATE.md (after V1_O)
- IMPLEMENTATION_GATE.md (after Sessions 13-14)
- Release gate (managed by app-release)
