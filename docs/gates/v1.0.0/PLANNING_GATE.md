# Planning Gate — v1.0.0
Track: Minor
Branch: feature/v1.0.0-entity-bridge

## What This Release Is
v1.0.0 is the first public release of MSF. It completes the implementation gap between the V1_N specification and what the reference implementation actually delivers: entity and block entity support in msf-fabric. Without this, builders who extract structures containing armor stands, item frames, or containers lose that content silently — unacceptable for a v1.0.0 claim given that Sections 8 and 9 of the spec are fully written and stable. This release also ships the publishing infrastructure that makes the project discoverable (Maven Central for msf-core, Modrinth and CurseForge for msf-fabric), canonical test vectors for third-party implementors, and a README benchmark table that demonstrates the format's concrete file-size advantage.

## Semver Rationale
Minor bump because all changes are additive — new capability in msf-fabric with no changes to existing APIs, binary format, or existing behavior.

## Breaking Change
Breaking: No

## Scope
In:
- Entity extraction and placement in msf-fabric (RegionExtractor, RegionPlacer, EntityBridge, BlockEntityBridge)
- Feature flag bits 0 and 1 wired end-to-end through extract and place commands
- Canonical test vectors in test/resources (one per compression type + one with entities and block entities)
- Maven Central publication for msf-core (dev.msf:msf-core:1.0.0)
- Modrinth and CurseForge publication for msf-fabric
- GitHub Release with msf-cli fat-jar attached
- README benchmark table (file size vs .litematic and .nbt)
- README install section with /msf extract usage

Out:
- /msf list and /msf preview in-game commands (v1.1.0)
- Layers support in /msf extract — multi-layer extraction (v1.1.0)
- .schem (MCEdit) and Sponge format conversion in msf-cli (v1.1.0)
- Rotation and mirror flags in /msf place (v1.1.0)
- NeoForge and Quilt multi-loader support (v1.2.0+)
- Mod Menu integration (v1.1.0)
- PlantUML architecture diagram (v1.1.0 docs pass)

## Stories

### Epic 1 — Entity & BlockEntity Fabric Bridge (msf-fabric)

---

As a builder,
I want the /msf extract command to capture entities within the extraction bounds,
so that armor stands, item frames, and other entities are preserved in the schematic.

Priority: P0
Module: msf-fabric
Spec reference: Section 8
Breaking: No

Acceptance Criteria:
- [ ] /msf extract captures all entities whose block position falls within the selection bounds
- [ ] Captured entities have UUIDs stripped from NBT payload before storage per Section 8.2
- [ ] MsfFile produced has feature flag bit 0 set and entity block offset non-zero
- [ ] Entity type, position (f64 x/y/z), yaw, and pitch are stored in typed fields, not in NBT
- [ ] /msf extract on a region containing no entities produces a file with feature flag bit 0 clear and entity block offset 0

---

As a builder,
I want the /msf place command to place entities from the schematic into the world,
so that entities are restored at the correct position with new UUIDs assigned.

Priority: P0
Module: msf-fabric
Spec reference: Section 8
Breaking: No

Acceptance Criteria:
- [ ] /msf place spawns all entities from the entity block at correct world positions (anchor-relative)
- [ ] Each placed entity receives a freshly generated UUID — the UUID was stripped on write and MUST NOT be reused
- [ ] /msf place on a file with feature flag bit 0 clear does not attempt entity spawning and does not throw
- [ ] Entities are spawned after block regions are placed in all layers

---

As a builder,
I want the /msf extract command to capture block entities within the extraction bounds,
so that chest contents, sign text, and other block entity data are preserved.

Priority: P0
Module: msf-fabric
Spec reference: Section 9
Breaking: No

Acceptance Criteria:
- [ ] /msf extract captures all block entities whose position falls within the selection bounds
- [ ] Block entity position is stored as i32 offsets relative to the schematic anchor per Section 9.1
- [ ] UUIDs are stripped from block entity NBT payloads per Section 9.2 before storage
- [ ] MsfFile produced has feature flag bit 1 set and block entity block offset non-zero when block entities are present
- [ ] /msf extract on a region with no block entities produces a file with feature flag bit 1 clear and block entity block offset 0

---

As a builder,
I want the /msf place command to restore block entities from the schematic,
so that chests, signs, and other tile entities are fully functional after placement.

Priority: P0
Module: msf-fabric
Spec reference: Section 9
Breaking: No

Acceptance Criteria:
- [ ] /msf place restores all block entities at the correct anchor-relative positions in the world
- [ ] /msf place on a file with feature flag bit 1 clear does not attempt block entity restoration and does not throw
- [ ] Block entity NBT data is applied after the corresponding block has been placed

---

As a developer,
I want EntityBridge and BlockEntityBridge to implement bidirectional conversion between Minecraft types and MSF model types,
so that all typed fields round-trip correctly and UUID stripping is enforced at the bridge layer.

Priority: P1
Module: msf-fabric (bridge/ package)
Spec reference: Sections 8.2, 9.2
Breaking: No

Acceptance Criteria:
- [ ] EntityBridge.toMsf(Entity) produces an MsfEntity with UUID absent from the NBT payload
- [ ] EntityBridge.toMinecraft(MsfEntity, world, offset) spawns an entity with a new UUID and correct position
- [ ] BlockEntityBridge.toMsf(BlockEntity, anchorOffset) produces an MsfBlockEntity with correct anchor-relative position and UUID absent
- [ ] BlockEntityBridge.toMinecraft(MsfBlockEntity, world, placementOffset) applies block entity NBT at the correct world position

---

As a developer,
I want round-trip tests for entities and block entities in msf-fabric,
so that extract → save → place is verified to preserve entity state correctly.

Priority: P1
Module: msf-fabric (tests)
Spec reference: Sections 8, 9
Breaking: No

Acceptance Criteria:
- [ ] Test: extract region containing one armor stand, save to .msf, place from .msf; verify armor stand spawned at correct position
- [ ] Test: extract region containing one chest with at least one item stack, save to .msf, place from .msf; verify chest contents match original
- [ ] Test: extract region with no entities; verify produced file has feature flag bit 0 clear
- [ ] Test: place a file with no entity block; verify no exception is thrown

---

### Epic 2 — Test Vectors (test/resources in msf-core or msf-fabric)

---

As a spec implementor,
I want canonical .msf test files covering all major structural variants,
so that I can validate my implementation against the reference format without writing my own test data.

Priority: P1
Module: test/resources
Spec reference: Sections 7.2, 8, 9
Breaking: No

Acceptance Criteria:
- [ ] minimal.msf: single layer, single region, no entities, no block entities, compression type 0x00 (none)
- [ ] zstd.msf: same structure as minimal.msf, compression type 0x01 (zstd)
- [ ] lz4.msf: same structure as minimal.msf, compression type 0x02 (lz4)
- [ ] brotli.msf: same structure as minimal.msf, compression type 0x03 (brotli)
- [ ] entities.msf: single layer, single region, feature flag bits 0 and 1 set, one entity, one block entity
- [ ] A test class validates each golden file by parsing it via MsfReader and asserting no exception is thrown and field values match documented expectations

---

### Epic 3 — Publishing Infrastructure (code-release)

---

As a developer,
I want msf-core published to Maven Central on release tag push,
so that library consumers can declare a Gradle or Maven dependency on it.

Priority: P0
Module: CI / build.gradle.kts
Spec reference: —
Breaking: No

Acceptance Criteria:
- [ ] GitHub Actions workflow triggers on push of tag matching v[0-9]*.[0-9]*.[0-9]*
- [ ] msf-core artifact published with coordinates dev.msf:msf-core:1.0.0
- [ ] Javadoc JAR and sources JAR included in the Maven Central publication
- [ ] Workflow fails and does not publish if any module test suite fails

---

As a player,
I want msf-fabric published to Modrinth and CurseForge on release tag push,
so that I can install it through a mod launcher.

Priority: P0
Module: CI
Spec reference: —
Breaking: No

Acceptance Criteria:
- [ ] GitHub Actions workflow publishes msf-fabric to Modrinth on tag push
- [ ] GitHub Actions workflow publishes msf-fabric to CurseForge on tag push
- [ ] GitHub Release is created with msf-cli fat-jar attached as a release asset
- [ ] Modrinth and CurseForge listings include the changelog text produced for this release

---

As a developer,
I want README badges for Maven Central, Modrinth, and CurseForge,
so that discoverability and version status are immediately visible from the repository.

Priority: P1
Module: README.md
Spec reference: —
Breaking: No

Acceptance Criteria:
- [ ] README displays a Maven Central version badge linked to the artifact page
- [ ] README displays a Modrinth downloads badge
- [ ] README displays a CurseForge downloads badge

---

### Epic 4 — Documentation (app-documentation)

---

As a potential adopter,
I want a file-size benchmark table in the README comparing .msf against .litematic and .nbt,
so that I can understand the concrete file-size advantage before committing to the format.

Priority: P1
Module: README.md / docs/
Spec reference: —
Breaking: No

Acceptance Criteria:
- [ ] README contains a table with at least three test cases spanning small, medium, and large structures
- [ ] Table columns: structure, .nbt size, .litematic size, .msf (zstd) size, .msf (lz4) size
- [ ] A one-sentence methodology note identifies the tool versions used to produce the measurements

---

As a new user,
I want install instructions in the README with usage examples for /msf extract and /msf place,
so that I can get started without reading the full spec.

Priority: P2
Module: README.md
Spec reference: —
Breaking: No

Acceptance Criteria:
- [ ] README contains a Fabric install path (Modrinth link or .jar download)
- [ ] README shows the /msf extract command syntax with an example selection
- [ ] README shows the /msf place command syntax with an example file path

---

## Session Plan

Session 13 (code-mc-implementation): Epic 1 — EntityBridge, BlockEntityBridge, RegionExtractor and RegionPlacer extensions for entities and block entities, feature flag bit wiring in /msf extract and /msf place, round-trip tests. Epic 2 — canonical test vectors generated and validated in the same session.

code-release task: Epic 3 — GitHub Actions workflow for Maven Central, Modrinth, CurseForge, and GitHub Release. README badges. This is a separate task for the code-release skill after Session 13 produces a passing build.

app-documentation task: Epic 4 — README benchmark table and install documentation. This runs in parallel with or after code-release.

## Format Amendment Brief
Not required. Sections 8 and 9 of spec revision V1_N are fully written, complete, and stable. No normative changes are needed for this release.

## SME Skills Required
All required skills installed:
- code-mc-implementation (Session 13)
- code-release (publishing infrastructure)
- app-documentation (README and docs)

## Gate Documents This Release Produces
- IMPLEMENTATION_GATE.md — produced by code-mc-implementation after Session 13
- RELEASE_GATE.md — produced by app-release after publishing
