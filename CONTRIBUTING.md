# Contributing to MSF

## Getting Started

1. **Clone the repository** and open in VS Code with Copilot integration
2. **Build the project**: `./gradlew build`
3. **Run tests**: `./gradlew test`

## Development Workflow

### Branch Naming
- Feature: `feature/description-here`
- Bug fix: `bugfix/issue-description`
- Refactor: `refactor/what-changed`
- Documentation: `docs/topic`

### Commit Messages
- Use present tense: "Add feature" not "Added feature"
- Be descriptive: Explain what and why, not just what
- Reference issues when applicable: "Fix login bug (closes #123)"
- Keep first line under 50 characters, wrap body at 72 characters

### Pull Requests
- Link related issues in description
- Describe the change and motivation
- Update documentation if needed
- Ensure all tests pass: `./gradlew test`

## Code Standards

### Java Coding Standards
- Follow conventions in `.claude_rules`
- Max line length: 120 characters (see `.editorconfig`)
- Use 4-space indentation
- Always add Javadoc to public methods/classes

### Testing Requirements
- Write unit tests for new features
- Test file location mirrors source: `src/main/java/com/example/Foo.java` → `src/test/java/com/example/FooTest.java`
- Aim for >80% code coverage
- Use descriptive test method names: `testUserCreationWithValidEmail()`

### Module Guidelines
- **msf-core**: Pure business logic, no dependencies on msf-fabric
- **msf-fabric**: Can depend on msf-core, integrations and implementations
- Keep modules loosely coupled

## Documentation

Update relevant documentation when:
- Adding new public APIs (Javadoc + API_GUIDELINES.md)
- Making architectural decisions (create ADR in docs/ADR/)
- Changing module responsibilities (update MODULE_GUIDE.md)
- Adding new conventions (update CODING_STANDARDS.md)

## Code Review Checklist

Before submitting a PR:
- [ ] Code follows style guide in `.editorconfig`
- [ ] All public APIs have Javadoc
- [ ] New tests added and all tests pass
- [ ] No decreases in code coverage
- [ ] Documentation updated
- [ ] Commit messages are descriptive
- [ ] No unnecessary dependencies added

## Using AI Assistants

### Claude.ai Desktop App
- Reference `.claude_rules` for coding standards
- Ask Claude to enforce project conventions
- Use for architecture planning and design discussions

### VS Code Copilot
- Should auto-follow `.editorconfig` formatting
- Copilot context is filtered by `.copilotignore`
- Use for code completion and refactoring suggestions

## Questions?

Refer to:
- [Architecture Overview](docs/ARCHITECTURE.md)
- [Module Guide](docs/MODULE_GUIDE.md)
- [API Guidelines](docs/API_GUIDELINES.md)
- [Road Map](docs/ROADMAP.md)
