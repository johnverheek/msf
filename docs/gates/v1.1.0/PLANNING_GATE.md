# Planning Gate — v1.1.0
Track: Minor
Branch: feature/v1.1.0

## Platform Target
Minecraft: 1.21.11
Fabric API: 0.141.3+1.21.11
Fabric Loader: 0.18.4
Artifact suffix: +1.21.11

Note: Minecraft 26.1 port is explicitly deferred to v1.2.0. The trigger condition (Sodium and Litematica stable on 26.1) is not met — 26.1 released 2026-03-14, Sodium latest stable is 0.8.6+1.21.11 (2026-02-23), Litematica latest stable is 0.25.4+1.21.11 (2026-01-12). The platform will remain 1.21.11 until the owner declares readiness to enter a current-version maintenance cycle.

## What This Release Is
v1.1.0 adds the in-game command surface and CLI polish that v1.0.0 deferred. Users gain the ability to list and preview schematics before placing, apply rotation and mirroring to placements, control entity capture behavior during extraction, and extract multi-layer structures. The CLI gains a version header so tooling output is self-describing. Documentation fills the README gaps deferred from v1.0.0.

## Semver Rationale
Minor bump. All changes are additive — new commands, new flags on existing commands, new CLI output. No existing behavior changes. No spec changes. No breaking changes to msf-core or msf-fabric public APIs.

## Breaking Change
Breaking: No

## Scope

In:
- `/msf list` — enumerate `.msf` files in the schematics folder with name, file size, MSF format version, and layer count
- `/msf preview` — display a bounding box wireframe at cursor position before placement (ghost block rendering is the v1.2.0 goal; bounding box is the v1.1.0 deliverable; see design note below)
- `/msf place` rotation and mirror flags — rotate/mirror placement based on explicit flag or player canonical facing; unadorned `/msf place` behavior is unchanged
- Entity capture policy — default behavior excludes living mobs from `/msf extract`; `--living-mobs` flag opts in to capturing them; non-living entities (armor stands, item frames, etc.) are always captured regardless of flag
- Layers support in `/msf extract` — `--layers N` flag subdivides the extraction into N horizontal layers, producing a multi-layer `.msf`
- Mod Menu integration — register msf-fabric with Mod Menu API so it appears correctly in the in-game mod list
- msf-cli output header — all subcommands print app version, MSF format version, and MC data version to stderr before output
- README benchmark table — file size comparison vs `.litematic` and `.nbt` across small/medium/large structures
- README install documentation — `/msf extract` and `/msf place` usage examples, Fabric install path
- PlantUML architecture diagram — module boundaries and data flow in `docs/`

Out:
- `.schem` (MCEdit) and Sponge format conversion — deferred indefinitely by owner
- Minecraft 26.1 port — deferred to v1.2.0 (trigger not met)
- Ghost block preview rendering — v1.2.0 goal; bounding box wireframe is v1.1.0
- NeoForge and Quilt multi-loader support — v1.2.0+

## Design Note — /msf preview

`/msf preview` is a bounding box wireframe overlay showing the placement footprint at the player's cursor before committing a `/msf place`. The implementation approach (server-side particle render vs. client-side mixin) is a decision for Session 17. The session should start by evaluating the simplest approach that is visually usable: server-side particle or block outline packets are preferred if viable to keep the mod server-side only. A client-side component is permitted if necessary but must be flagged in the implementation gate.

## Design Note — Entity Capture Policy

Living mob exclusion applies to entities whose type is a subtype of `LivingEntity` AND is not `ArmorStandEntity`. Item frames, armor stands, and other non-living entities are always included. The `--living-mobs` flag was confirmed functionally possible during v1.0.0 testing but has known oddities; testing must verify and document the known edge cases in the session notes.

## Stories

### Epic 1 — /msf list (msf-fabric)

---

As a builder,
I want to run `/msf list` to see all available schematic files,
so that I can find the right file without leaving the game.

Priority: P0
Module: msf-fabric (command/)
Breaking: No

Acceptance Criteria:
- [ ] `/msf list` enumerates all `.msf` files in the `msf-schematics/` directory
- [ ] Output per file: filename, file size (human-readable), MSF format major version, layer count
- [ ] Output is delivered as chat messages to the command sender
- [ ] If the directory is empty or does not exist, a clear "no schematics found" message is shown
- [ ] If a file fails to parse for metadata, it is listed with an error annotation rather than crashing the command

---

### Epic 2 — /msf preview (msf-fabric)

---

As a builder,
I want to run `/msf preview <filename>` to see a bounding box at my cursor before placement,
so that I can verify alignment and orientation before committing.

Priority: P1
Module: msf-fabric (command/, rendering)
Breaking: No

Acceptance Criteria:
- [ ] `/msf preview <filename>` renders a bounding box wireframe at the player's look-at target position
- [ ] Bounding box dimensions match the schematic's layer dimensions (x, y, z extents)
- [ ] Preview persists until the player runs `/msf preview off` or logs out
- [ ] Tab-completion for `<filename>` lists available `.msf` files
- [ ] If the file cannot be read, the command fails with an error message and no preview is rendered
- [ ] Implementation approach (server-side vs. client-side) is documented in the session notes

---

### Epic 3 — /msf place Rotation and Mirror Flags (msf-fabric)

---

As a builder,
I want to pass a rotation flag to `/msf place` so that I can orient the structure without manually adjusting my facing,
so that I have precise control over placement orientation.

Priority: P1
Module: msf-fabric (command/, RegionPlacer)
Breaking: No

Acceptance Criteria:
- [ ] `/msf place <filename> [--rotate <0|90|180|270>]` applies clockwise rotation to the placement
- [ ] `/msf place <filename> [--mirror <x|z>]` applies mirror transformation along the specified axis
- [ ] Flags are optional; omitting them produces identical behavior to unadorned `/msf place` in v1.0.0
- [ ] `--rotate` and `--mirror` can be combined in a single command
- [ ] Rotation logic uses the existing `CanonicalFacing` rotation delta infrastructure
- [ ] Round-trip test: place with `--rotate 90`, verify block positions match expected rotation

---

### Epic 4 — Entity Capture Policy (msf-fabric)

---

As a builder,
I want `/msf extract` to exclude living mobs by default,
so that wandering mobs don't end up in my schematic unexpectedly.

Priority: P0
Module: msf-fabric (command/, RegionExtractor)
Breaking: No

Acceptance Criteria:
- [ ] By default, `/msf extract` excludes all entities whose type is a LivingEntity subtype, except ArmorStandEntity
- [ ] Armor stands and item frames are always captured regardless of flag
- [ ] `--living-mobs` flag opts in to capturing living mob entities
- [ ] Test: extract a region containing a villager and an armor stand without the flag; verify only the armor stand is captured
- [ ] Test: same extraction with `--living-mobs`; verify both are captured
- [ ] Known edge cases observed during v1.0.0 testing are documented in session notes

---

### Epic 5 — Layers Support in /msf extract (msf-fabric, msf-core)

---

As a builder,
I want to extract a region into multiple horizontal layers,
so that I can place large structures in stages.

Priority: P1
Module: msf-fabric (command/, RegionExtractor), msf-core (MsfFile builder)
Breaking: No

Acceptance Criteria:
- [ ] `/msf extract <filename> [--layers N]` subdivides the vertical extent into N equal horizontal layers (rounding up for the last layer if height is not evenly divisible)
- [ ] Resulting `.msf` file contains N layers in the layer index, each with the correct y-offset and height
- [ ] If `--layers` is omitted, behavior is identical to v1.0.0 (single layer)
- [ ] Each layer is independently compressible — the existing compression path is applied per-region
- [ ] Test: extract a 10-block-tall region with `--layers 2`; verify two layers with y-offsets 0 and 5 and heights 5 and 5
- [ ] Test: extract a 7-block-tall region with `--layers 3`; verify three layers with heights 3, 3, 1

---

### Epic 6 — Mod Menu Integration (msf-fabric)

---

As a player,
I want msf-fabric to appear in the Mod Menu mod list with correct metadata,
so that I can see the mod version and description in-game.

Priority: P2
Module: msf-fabric
Breaking: No

Acceptance Criteria:
- [ ] msf-fabric is registered with the Mod Menu API and appears in the in-game mod list
- [ ] Displayed name, version, description, and author match fabric.mod.json
- [ ] A config screen stub is acceptable for v1.1.0 (no actual config required)

---

### Epic 7 — msf-cli Output Header (msf-cli)

---

As a developer using msf-cli,
I want all subcommands to print a version header to stderr before output,
so that I know which tool version and format version produced the output.

Priority: P2
Module: msf-cli
Breaking: No

Acceptance Criteria:
- [ ] All subcommands (`inspect`, `validate`, `convert`) print to stderr: `msf-cli <app-version> | MSF format <major>.<minor> | MC data <data-version>`
- [ ] Header is printed before any other output
- [ ] Header does not appear in stdout (stdout remains parseable by tooling)
- [ ] Unit test: capture stderr of a subcommand invocation and assert header format matches

---

### Epic 8 — Documentation (app-documentation)

---

As a potential adopter,
I want a file-size benchmark table in the README,
so that I can understand the concrete size advantage before committing.

Priority: P1
Module: README.md
Breaking: No

Acceptance Criteria:
- [ ] Table with at least three test cases: small (<100 blocks), medium (1k–10k blocks), large (100k+ blocks)
- [ ] Columns: structure name, `.nbt` size, `.litematic` size, `.msf (zstd)` size, `.msf (lz4)` size
- [ ] One-sentence methodology note identifies tool versions used

---

As a new user,
I want install instructions and usage examples in the README,
so that I can get started without reading the full spec.

Priority: P1
Module: README.md
Breaking: No

Acceptance Criteria:
- [ ] README includes a Fabric install path (Modrinth link or .jar download)
- [ ] README shows `/msf extract` command syntax with an example
- [ ] README shows `/msf place` command syntax with an example
- [ ] README shows `/msf list` command syntax

---

As a developer or contributor,
I want a PlantUML architecture diagram in docs/,
so that module boundaries and data flow are visually clear.

Priority: P2
Module: docs/ARCHITECTURE.md + docs/architecture.puml
Breaking: No

Acceptance Criteria:
- [ ] PlantUML source file at `docs/architecture.puml` rendering module boundaries: msf-core, msf-fabric, msf-cli
- [ ] Data flow arrows: converter inputs/outputs, MsfReader/MsfWriter, bridge layer
- [ ] Diagram is linked from ARCHITECTURE.md

---

## Session Plan

Session 14 (code-mc-implementation): Epic 1 (/msf list), Epic 6 (Mod Menu integration), Epic 7 (msf-cli output header). Lightweight session — command enumeration, Mod Menu registration, CLI stderr header.

Session 15 (code-mc-implementation): Epic 3 (/msf place rotation and mirror flags). Builds on existing CanonicalFacing infrastructure. Includes round-trip tests.

Session 16 (code-mc-implementation): Epic 4 (entity capture policy) and Epic 5 (layers support in /msf extract). These share RegionExtractor as the primary touch point — combined session reduces context switching.

Session 17 (code-mc-implementation): Epic 2 (/msf preview bounding box). Isolated rendering session. Starts with implementation approach decision (server-side vs. client-side); documents decision in session notes.

app-documentation task: Epic 8 — README benchmark table, install docs, PlantUML diagram. Runs after Session 17.

## Format Amendment Brief
Not required. No MSF binary format changes in v1.1.0. All new behavior is at the command and converter layer.

## SME Skills Required
All required skills installed:
- code-mc-implementation (Sessions 14–17)
- app-documentation (Epic 8)

## Gate Documents This Release Produces
- IMPLEMENTATION_GATE.md — produced by code-mc-implementation after Session 17
- RELEASE_GATE.md — produced by app-release after publishing
