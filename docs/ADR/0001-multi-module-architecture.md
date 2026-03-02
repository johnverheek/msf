# ADR-0001: Multi-Module Architecture

**Status**: Accepted

**Date**: 2026-03-01

**Author(s)**: Project Team

## Context

MSF needs a scalable, maintainable architecture from the start. The project will contain distinct concerns:
- Core business logic that should remain independent and reusable
- Integration and implementation layers that depend on core logic

A single monolithic module would mix concerns and reduce reusability. A distributed system would add unnecessary complexity too early.

## Decision

Organize the project as a Gradle multi-module build with two primary modules:

1. **msf-core**: Pure business logic, no external dependencies
2. **msf-fabric**: Integrations and implementations of core interfaces

Each module has its own:
- Build configuration
- Source and test directories
- Dependency declarations
- May grow into submodules as project scales

## Rationale

### Pros
- **Clear Separation**: Core logic is isolated from integration concerns
- **Reusability**: Other projects can depend on msf-core without msf-fabric
- **Scalability**: Easy to add new modules (msf-database, msf-api) without monolithic growth
- **Testability**: Core can be tested independently
- **Team Scaling**: Multiple teams can work on different modules without conflicts
- **Dependency Direction**: Clear dependency flow prevents circular dependencies
- **Build Efficiency**: Can build/test modules independently

### Cons
- **Build Complexity**: Multiple module coordination adds slight complexity
- **Initial Setup Overhead**: More configuration files initially
- **Gradle Learning**: Team must understand multi-module Gradle builds

### Trade-offs
- Accept slightly more complex build configuration for significantly better architecture
- Accept longer setup time initially for long-term maintainability gains

## Consequences

### Positive
- Core module remains clean and testable
- Easy to add new integration modules
- Clear architectural boundaries prevent technical debt
- Enables independent versioning/releases
- Supports incremental refactoring across modules

### Negative
- Build configuration is more complex than single-module
- New developers need to understand module relationships
- Inter-module debugging requires understanding dependency flow

## Alternatives Considered

### Alternative 1: Monolithic Single Module
- **Pros**: Simpler initial setup, single build configuration
- **Cons**: Core and fabric concerns mixed, harder to reuse, architecture degrades over time
- **Why rejected**: Would compromise long-term maintainability and growth

### Alternative 2: Separate Repositories
- **Pros**: Complete independence, clear module boundaries
- **Cons**: Distributed development complexity, versioning nightmare, coordination overhead
- **Why rejected**: Too much complexity for current project size, premature distribution

### Alternative 3: Maven Multi-Module
- **Pros**: Industry standard, well-known
- **Cons**: Verbose XML configuration, slower builds, less flexible than Gradle
- **Why rejected**: Gradle provides better developer experience and build performance

## Implementation Notes

### Module Structure
```
msf/
├── msf-core/
│   ├── build.gradle
│   └── src/
├── msf-fabric/
│   ├── build.gradle
│   └── src/
├── build.gradle (root)
└── settings.gradle
```

### Dependency Declaration
- Root `build.gradle`: Common configurations, repositories
- Module `build.gradle`: Module-specific dependencies

### Future Scaling
When new modules are needed:
```
├── msf-core/           # Core
├── msf-fabric/         # Primary integration
├── msf-database/       # Database-specific (future)
├── msf-api/            # API layer (future)
└── msf-security/       # Security (future)
```

## Related ADRs
- ADR-0002: Java 21 with Modern Features  
- ADR-0003: Gradle Build Tool Selection

## References
- [Gradle Multi-project Build Documentation](https://docs.gradle.org/current/userguide/multi_project_builds.html)
- [Maven Multi-Module Projects](https://maven.apache.org/guides/mini/guide-multiple-modules.html)

## Approval
- [x] Approved by Project Lead (2026-03-01)
- [x] Architecture defined and implemented: 2026-03-01
