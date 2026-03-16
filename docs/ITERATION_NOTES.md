# Iteration Notes — v1.0.0

## Publishing Status
Decision: Staged
Reason: Mod pages not yet created on Modrinth and CurseForge. Maven Central signing credentials not yet configured. GitHub Release artifact published. Timeline for public publishing is open-ended — owner-triggered when ready.

## What Worked Well
- Gate document structure — planning gate, format gate, release gate, and implementation gate all carried the right information to the right skill at the right time
- Skill handoffs and kickoff prompts — receiving skills started work immediately without asking clarifying questions in most cases
- Spec review process — the amendment round (V1_O) was fast and precise; the corrective issue was correctly classified and resolved without scope creep
- In-game RTM surfaced real bugs — two genuine defects (JiJ packaging, entity position coordinate space) would not have been caught by automated tests alone; RTM is load-bearing
- Triage and scope decisions — the v1.0.0 vs v1.1.0 split was correct; entity bridge in v1.0.0 was the right call; nothing slipped in that shouldn't have

## Friction Points
- Multiple RTM crashes before root cause found — three separate crash reports (1.21.11 version mismatch, Fabric API version mismatch, zstd NoClassDefFoundError) before the first successful extract. Each was a distinct environment or packaging issue, not a code defect, but the cumulative effect was a long RTM loop.
- Gametest API migration missed in Session 13b — the 1.21.1 → 1.21.11 port fixed bridge and command classes but did not audit testmod code. 116 compiler errors surfaced in Session 13f. Should have been caught in 13b.

## Skill Gaps or Improvements

**app-planning — platform target must be decided at planning, not discovered at RTM**
The planning gate specified Minecraft 1.21.1 as the target. The version was updated to 1.21.11 mid-cycle after an RTM crash revealed the mismatch. Platform target is a planning-level decision that should be explicitly researched and stated at gate production time, not inherited from prior sessions without verification. app-planning should query current Fabric ecosystem status when producing a planning gate for a Fabric mod and state the target version explicitly with rationale.

**code-mc-implementation — session kickoff prompts must include JiJ packaging requirements**
The entity bridge kickoff did not mention Jar-in-Jar packaging. The msf-fabric module depends on msf-core and three compression libraries at runtime. A Fabric mod that omits JiJ for its non-Minecraft dependencies will crash at runtime — this is a Fabric-specific requirement that any implementation session touching msf-fabric must address. Kickoff prompts for msf-fabric sessions should explicitly state: "all runtime dependencies not provided by Minecraft or Fabric API must be declared with the include configuration in build.gradle.kts."

**code-mc-implementation — coordinate space must be stated explicitly in entity bridge kickoffs**
The original entity bridge kickoff said "store entity position" without specifying the coordinate space. The implementation stored absolute world coordinates. The kickoff should have said "store entity position as anchor-relative offset (entity world position minus anchor world position)" with the inverse on read. Whenever a kickoff involves spatial data that has a reference frame, the reference frame must be stated normatively in the kickoff, not left to the implementor to infer from the spec.

**code-mc-implementation — port sessions must audit all subprojects including testmod**
Session 13b ported msf-fabric source but not msf-fabric testmod. The gametest API migration (FabricGameTest removal, @GameTest annotation relocation, templateName rename) was a Fabric API breaking change that affected all test code. Port session kickoffs must explicitly scope "audit and update all subprojects including testmod and integration test code" not just production source.

**app-release — release gate checklist should include JiJ verification**
The release gate checklist had build verification but no explicit check for JiJ packaging completeness. For Fabric mod projects, the checklist should include: "verify META-INF/jars/ contains all required runtime dependencies" as a named checklist item, runnable via jar tf before RTM begins. This would have caught the zstd crash before the first in-game test.

## Workflow Improvements
- Platform target research belongs in app-planning, stated in the planning gate, not deferred to discovery at RTM
- JiJ verification should be a named pre-RTM checklist item in app-release for all Fabric mod projects
- Port sessions should explicitly list all subprojects in scope, including testmod

## Carry Forward
- v1.1.0 backlog is fully defined in STUDIO_STATE.md
- 26.1 port is ecosystem-gated — trigger when Sodium and Litematica publish stable 26.1 builds
- Public publishing gate is owner-triggered — requires Modrinth and CurseForge mod pages plus secrets configuration

## Kickoff for Skill Updates
> Paste this into the studio consultant account:

I'm closing out iteration v1.0.0 of the MSF project. Here are my iteration notes:

The overall workflow rated "Solid — minor improvements only" for a first full cycle. Five specific skill improvements are identified:

1. app-planning: Platform target for Fabric mods must be explicitly researched and stated in the planning gate with current ecosystem rationale. Do not inherit from prior sessions without verification.

2. code-mc-implementation: Kickoff prompts for msf-fabric sessions must include an explicit JiJ packaging requirement: all runtime dependencies not provided by Minecraft or Fabric API must be declared with the include configuration.

3. code-mc-implementation: Kickoff prompts involving spatial data must state the coordinate reference frame normatively — "anchor-relative offset" not just "position".

4. code-mc-implementation: Port session kickoffs must explicitly scope all subprojects including testmod and integration test code, not just production source.

5. app-release: The Fabric mod release checklist must include a named JiJ verification step — confirm META-INF/jars/ contains all required runtime dependencies before RTM begins.

Please review and recommend skill file updates based on this feedback.
