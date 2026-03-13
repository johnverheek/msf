Planning Gate — v1.0.0
Track: Major (initial release) Branch: release/v1.0.0
What This Release Is
The first internal release of MSF (Minecraft Structured Format) — a binary schematic file format for Minecraft with a Java reference implementation. v1.0.0 exercises the full release process: CI/CD pipeline, version finalization, build verification, spec publication, and tagging. Artifacts are built and verified but NOT published externally. No Maven repository, no mod distribution platform, no community announcement. External publishing is a separate future gate triggered by the owner.
Semver Rationale
Initial release — v1.0.0 signals a stable, production-ready format and reference implementation. The version is real and permanent even though distribution is deferred.
Breaking Change
Breaking: No — there are no prior releases to break against.
Inventory of What Exists
Specification
•	V1.0 spec through review round V1_N — all critical issues resolved, spec stable
•	Covers: header, global palette, metadata, layer index, region data (bit packing, compression), entity block, block entity block, placement metadata, file checksum, versioning contract
•	Full appendices (A–F): implementer guarantee, block type summary, canonical ordering, xxHash3, unsigned integer handling, deprecation policy
msf-core (Sessions 1–10)
•	MsfHeader: 48-byte header read/write, magic validation, checksum verification
•	MsfException hierarchy: MsfParseException, MsfVersionException, MsfChecksumException, MsfPaletteException, MsfCompressionException
•	Global palette: read/write, AIR invariant, deduplication enforcement
•	Metadata block: all fields including placement metadata, thumbnail, functional volume
•	Layer index: read/write, dependency validation, flag handling
•	Region data: bit packing (YZX ordering), compression (none/zstd/lz4/brotli), biome data
•	Entity and block entity blocks: typed field extraction, UUID stripping
•	File checksum: xxHash3-64 computation and verification
•	Warning mechanism: MsfWarning with codes, consumer-based delivery
•	In-house NBT reader/writer
•	NBT ↔ MSF conversion logic
•	Litematica ↔ MSF conversion logic (with intermediate routing)
msf-fabric (Session 12)
•	BlockStateBridge, EntityBridge, BiomeBridge — registry resolution
•	RegionExtractor — world → MSF extraction
•	RegionPlacer — MSF → world placement with facing rotation
•	/msf extract command — region selection, layer assignment, file output
•	/msf place command — all layers, player facing, file path resolution
•	File paths resolve relative to server run directory (msf-schematics/)
msf-cli (Sessions 9–11)
•	inspect subcommand — header and block summary
•	validate subcommand — structural and checksum verification
•	.nbt ↔ .msf conversion (vanilla structure format)
•	.litematic ↔ .msf conversion (Litematica format)
•	Cross-format routing (e.g., .litematic → .msf → .nbt via intermediate)
Test Suite
•	269+ tests across all three modules
•	Coverage: round-trip, corruption, version rejection, boundary conditions, forward compatibility
Scope
In:
•	CI/CD pipeline (GitHub Actions: build + test on PR and push to develop/main)
•	Version strings finalized in all build files and mod metadata
•	CLI distribution packaging (fat jar via shadowJar or equivalent)
•	README with project overview, module descriptions, build instructions, usage examples
•	CHANGELOG.md for v1.0.0
•	Spec document cleanup (remove DRAFT marker, strip working notes)
•	fabric.mod.json verification (version, dependencies, metadata)
•	Clean build verification (all tests green)
•	End-to-end round-trip validation (extract → save → inspect → validate → place)
•	Conversion path verification (.nbt and .litematic round trips)
•	Git tag v1.0.0 on main
•	GitHub Release with artifacts attached (spec, CLI fat jar, mod jar)
Out:
•	External publishing to any repository or platform (Maven Central, GitHub Packages, Modrinth, CurseForge) — deferred until owner triggers publishing gate
•	Community announcements and mod page content — deferred until publishing
•	NeoForge bridge — deferred to a future version
•	GUI tooling or editor integration
•	Spec website or hosted documentation beyond the repo
•	Performance benchmarking suite
•	Javadoc polish — can be done incrementally, not a release blocker
Stories
Epic 1 — Release Infrastructure
Story 1.1: CI/CD Pipeline
As the project owner, I want automated build and test on every push and PR, so that regressions are caught before they reach the develop branch.
Priority: P0 Module: repo-wide (GitHub Actions) Breaking: No
Acceptance Criteria:
•	[ ] GitHub Actions workflow runs ./gradlew build on push to develop and main
•	[ ] GitHub Actions workflow runs ./gradlew build on PR to develop and main
•	[ ] Workflow targets Java 21
•	[ ] Build failure blocks PR merge
•	[ ] Workflow caches Gradle dependencies
Story 1.2: Version Strings
As the release engineer, I want all version strings set to 1.0.0 from a single source, so that build artifacts carry the correct version with no duplication.
Priority: P0 Module: all modules (build.gradle.kts, fabric.mod.json, libs.versions.toml) Breaking: No
Acceptance Criteria:
•	[ ] msf-core artifact version is 1.0.0
•	[ ] msf-fabric artifact version is 1.0.0+1.21.1
•	[ ] msf-cli artifact version is 1.0.0
•	[ ] fabric.mod.json version field matches 1.0.0
•	[ ] fabric.mod.json depends block declares correct Fabric API and loader versions
•	[ ] Version is sourced from a single location — no duplicated version literals
Story 1.3: CLI Fat Jar
As a user who does not build from source, I want a single runnable jar for msf-cli, so that I can inspect, validate, and convert MSF files directly.
Priority: P0 Module: msf-cli (build configuration) Breaking: No
Acceptance Criteria:
•	[ ] ./gradlew :msf-cli:shadowJar (or equivalent) produces a fat jar with all dependencies
•	[ ] Fat jar is runnable via java -jar msf-cli-1.0.0.jar
•	[ ] --help prints usage for all subcommands
•	[ ] Fat jar does not include msf-fabric or Minecraft dependencies
Epic 2 — Documentation
Story 2.1: README
As a developer discovering the project, I want a README that explains what MSF is, how the repo is structured, and how to build it, so that I can evaluate and build the project.
Priority: P0 Module: repo root Breaking: No
Acceptance Criteria:
•	[ ] README contains: project description, module overview (core/fabric/cli), build instructions, CLI usage examples, link to spec
•	[ ] Build instructions work for a clean checkout with Java 21 and Gradle
•	[ ] No broken links
Story 2.2: Spec Artifact Preparation
As the release engineer, I want the specification document cleaned up for release, so that the published spec is authoritative and free of working notes.
Priority: P0 Module: docs Breaking: No
Acceptance Criteria:
•	[ ] Spec header reads "Version 1.0" (not "DRAFT")
•	[ ] All review round markers and working notes are removed
•	[ ] Spec is attached to the GitHub Release as a downloadable artifact
Story 2.3: Changelog
As the project owner reviewing the release, I want a CHANGELOG.md documenting what v1.0.0 includes, so that the scope of the initial release is clearly recorded.
Priority: P0 Module: repo root Breaking: No
Acceptance Criteria:
•	[ ] CHANGELOG.md exists with a v1.0.0 entry
•	[ ] Entry summarizes: spec version, module capabilities, supported formats, Minecraft version target
•	[ ] Follows Keep a Changelog format
Epic 3 — Final Verification
Story 3.1: Clean Build Verification
As the release engineer, I want a clean-checkout build to pass all tests with zero failures, so that release artifacts are verified correct.
Priority: P0 Module: all Breaking: No
Acceptance Criteria:
•	[ ] ./gradlew clean build passes all 269+ tests
•	[ ] No test is @Disabled without an explanatory comment
•	[ ] No compiler warnings related to deprecation or unchecked casts in production code
Story 3.2: End-to-End Round-Trip Validation
As the project owner, I want documented evidence of a full round trip (build → extract → save → inspect → validate → place), so that all three modules are verified to interoperate.
Priority: P0 Module: all (integration) Breaking: No
Acceptance Criteria:
•	[ ] Round trip completed against Minecraft 1.21.1 with Fabric
•	[ ] CLI inspect output matches expected header and block summary
•	[ ] CLI validate reports no errors
•	[ ] Placed structure matches original (visual comparison + block count match)
•	[ ] Results documented in a verification log
Story 3.3: Conversion Path Verification
As a user with existing schematics, I want .nbt ↔ .msf and .litematic ↔ .msf conversions to produce valid files, so that migration to MSF works correctly.
Priority: P0 Module: msf-cli Breaking: No
Acceptance Criteria:
•	[ ] .nbt → .msf produces a file that passes validate
•	[ ] .litematic → .msf produces a file that passes validate
•	[ ] .msf → .nbt round trip preserves block data
•	[ ] .msf → .litematic round trip preserves block data
•	[ ] Pending tick data from Litematica is silently dropped
Session Plan
Session 13: Release Infrastructure
•	Module: repo-wide
•	Stories: 1.1 (CI/CD), 1.2 (version strings), 1.3 (CLI fat jar)
•	Deliverables: GitHub Actions workflow, version finalization, shadowJar configuration
•	Gated on: nothing — can start immediately
Session 14: Documentation + Clean Build
•	Module: repo-wide
•	Stories: 2.1 (README), 2.2 (spec cleanup), 2.3 (changelog), 3.1 (clean build)
•	Deliverables: README.md, CHANGELOG.md, cleaned spec, build verification log
•	Gated on: Session 13 (version strings must be final)
Owner verification (manual, not a coding session):
•	Stories: 3.2 (round-trip), 3.3 (conversion paths)
•	Requires running Minecraft 1.21.1 with Fabric
•	Results committed to repo as verification log
Release phase (app-release skill):
•	Tag v1.0.0, create GitHub Release, attach artifacts (spec, CLI fat jar, mod jar)
•	No external publishing — artifacts live on the GitHub Release only
Format Amendment Brief
None — spec is at V1_N, closed, stable. The only spec change is removing the "DRAFT" status marker, which is editorial, not normative.
SME Skills Required
Skill	Status	Phase Needed
app-planning	Installed	Planning (now)
code-mc-implementation	Installed	Sessions 13–14
code-release	Installed	CI/CD (Session 13)
app-release	Installed	Release phase
app-documentation	Installed	Session 14
app-format-spec	Installed	Spec cleanup only
All required skills installed.
Gate Documents This Release Produces
Gate	Producer	Status
PLANNING_GATE.md	app-planning	This document
IMPLEMENTATION_GATE.md	code-mc-implementation	After Session 14
Release gate	app-release	After verification
Deferred: Publishing Gate
External publishing is explicitly deferred. When the owner is ready, a separate publishing gate will be produced covering:
•	Maven repository publishing (GitHub Packages or Maven Central)
•	Modrinth and CurseForge mod pages
•	Community announcements
•	Any additional documentation (Javadoc hosting, spec website)
This gate will be triggered by the owner, not by the release process.
