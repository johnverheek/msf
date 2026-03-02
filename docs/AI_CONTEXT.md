# AI Context & Development Guide

## Project Summary
**MSF** is a multi-module Java 21 project designed with AI-assisted development in mind. It uses Gradle for builds and is structured for clarity and maintainability.

## Technology Stack
- **Language**: Java 21
- **Build Tool**: Gradle 8+
- **Testing**: JUnit 4
- **IDE**: VS Code with Copilot integration + Claude.ai desktop app
- **Code Quality**: Checkstyle, Editor Config

## Project Structure

```
msf/
├── msf-core/              # Core business logic layer
│   ├── src/main/java/
│   └── src/test/java/
├── msf-fabric/            # Integration & fabric layer
│   ├── src/main/java/
│   └── src/test/java/
├── docs/                  # Documentation
│   ├── ARCHITECTURE.md
│   ├── API_GUIDELINES.md
│   ├── CODING_STANDARDS.md
│   ├── MODULE_GUIDE.md
│   ├── ROADMAP.md
│   ├── ADR/              # Architecture Decision Records
│   └── AI_CONTEXT.md     # This file
├── .claude_rules         # Claude.ai coding standards
├── .copilotignore        # Copilot context exclusions
├── .editorconfig         # Cross-editor formatting
├── checkstyle.xml        # Code style rules
├── CONTRIBUTING.md       # Contribution guidelines
├── MAINTAINERS.md        # Project ownership
└── build.gradle          # Multi-module build config
```

## Key Architectural Principles

1. **Modular Design**: Each module has clear responsibilities
   - msf-core: Pure business logic, no external integrations
   - msf-fabric: Integration layer, depends on msf-core

2. **Java 21 Features**: Modern Java idioms encouraged
   - Records for immutable data classes
   - Sealed classes for constrained type hierarchies
   - Text blocks for multi-line strings
   - var keyword for type inference

3. **Documentation-First**: All public APIs documented
   - Javadoc required for public classes/methods
   - Architecture decisions recorded in ADRs
   - Complex logic has inline comments

4. **Testability**: Design for unit testing
   - Test coverage >80% target
   - Tests mirror source structure
   - Clear test naming and assertions

## When Working with AI

### Using Claude.ai
1. Reference `.claude_rules` for this project's conventions
2. Ask Claude to "enforce MSF project standards"
3. Discuss architectural decisions before implementation
4. Request documentation review for public APIs

### Using VS Code Copilot
1. Files listed in `.copilotignore` are filtered from context
2. Follows `.editorconfig` formatting automatically
3. Use for code completion, refactoring suggestions
4. Ask to generate tests alongside implementations

## Development Workflow

1. **Read docs**: Understand module responsibilities
2. **Check ADRs**: See architectural decision history
3. **Follow standards**: Use `.claude_rules` and `.editorconfig`
4. **Test thoroughly**: Add unit tests for new code
5. **Document**: Javadoc + inline comments
6. **Request review**: Reference standards in PR

## Building & Testing

```bash
# Build all modules
./gradlew build

# Run all tests
./gradlew test

# Build specific module
./gradlew :msf-core:build

# Run tests with output
./gradlew test --info
```

## Common Tasks

### Adding a New Class
1. Create in appropriate module (core vs fabric)
2. Add Javadoc to public members
3. Write unit tests in mirrored path
4. Ensure Checkstyle passes
5. Update API_GUIDELINES.md if public API

### Making Architectural Change
1. Create ADR in docs/ADR/
2. Update ARCHITECTURE.md
3. Reference ADR in commit messages
4. Update relevant module documentation

### Updating Standards
1. Update applicable file (.claude_rules, CODING_STANDARDS.md, etc.)
2. Communicate change in PR description
3. Apply to all future contributions

## References

- [ARCHITECTURE.md](ARCHITECTURE.md) - System design and module relationships
- [.claude_rules](.claude_rules) - Coding standards for AI assistants
- [CONTRIBUTING.md](CONTRIBUTING.md) - Contribution workflow
- [ADR Template](ADR/0000-template.md) - Decision recording format
