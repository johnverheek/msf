# Studio State — MSF

## Current Phase
Implementation — Session 13 complete, Session 14 ready to start.

## Version Target
v1.0.0 (internal release — no external publishing until owner triggers publishing gate)

## Spec Status
V1_N — closed, stable. No open issues. DRAFT marker still present — removal is a Session 14 deliverable.

## Implementation Status
Session 13 complete. CI/CD pipeline, version strings, CLI fat jar all delivered.
Session 12 test count: 269+. No new tests in Session 13 (infrastructure-only session).

### Session 13 Deliverables
- .github/workflows/ci.yml — build + test on push/PR to develop and main, Java 21, Gradle cache
- Version strings confirmed single-source: allprojects { version = "1.0.0" } in root build.gradle.kts
- fabric.mod.json uses ${version} placeholder, depends tightened to match libs.versions.toml
- msf-cli fat jar: ./gradlew build produces runnable msf-cli-1.0.0.jar

## What's Next
1. Session 14: Documentation + clean build (README, changelog, spec cleanup, build verification)
2. Manual round-trip and conversion verification (owner, with running Minecraft)
3. Release phase — tag + GitHub Release with artifacts, no external publishing

## Watch Items for Session 14
- runGametest is not part of ./gradlew build — note in verification log that gametests require a live server
- msf-fabric/build.gradle.kts uses deprecated project.buildDir — fix during clean build pass

## Gate Documents
| Gate | Path | Status |
|------|------|--------|
| Planning | docs/gates/v1.0.0/PLANNING_GATE.md | Approved |
| Implementation | docs/gates/v1.0.0/IMPLEMENTATION_GATE.md | Produced at end of Session 14 |
| Release | — | After verification |
| Publishing | — | Deferred until owner triggers |

## Branch
Work: develop

## Repo
https://github.com/johnverheek/msf
