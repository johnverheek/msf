# ADR-0002: Java 21 with Modern Features

**Status**: Accepted

**Date**: 2026-03-01

**Author(s)**: Project Team

## Context

Project requires choice of Java version. Java 21 was released in Sept 2023 and has LTS (Long-Term Support) status until Sept 2031. It introduces significant language improvements:
- Records (JEP 395, finalized in Java 16)
- Sealed classes (JEP 409, finalized in Java 17)
- Text blocks (finalized in Java 15)
- Pattern matching advancements
- Virtual threads (preview, powerful for async)

Previous versions have limited modern features; waiting for newer versions adds risk.

## Decision

Use Java 21 as the target language version with active use of modern features:
- **Records**: For immutable data classes
- **Sealed classes**: For constrained type hierarchies
- **Text blocks**: For multi-line strings
- **Var keyword**: For obvious type inference
- **Pattern matching**: For enhanced conditionals

Configure both source and target compatibility to Java 21 in `build.gradle`.

## Rationale

### Pros
- **LTS Support**: 5+ years of security patches (until Sept 2031)
- **Modern Syntax**: Records reduce boilerplate significantly
- **Type Safety**: Sealed classes provide compile-time guarantees
- **Developer Experience**: Modern features improve code clarity
- **Performance**: Virtual threads (future use) enable efficient async
- **Industry Adoption**: Java 21 widely adopted by March 2026
- **Future Proof**: Well-positioned for Java 23+ features

### Cons
- **Minimum JDK Version**: Users must have Java 21+ installed
- **Library Compatibility**: Some older libraries may not support Java 21
- **Team Learning**: Team must learn modern Java idioms

### Trade-offs
- Accept Java 21 requirement for significantly cleaner code
- Accept initial learning curve for modern features

## Consequences

### Positive
- Records eliminate getter/constructor boilerplate
- Sealed classes prevent invalid type combinations
- Code is more readable and maintainable
- Aligns with current industry best practices
- Will be LTS version for entire project lifetime

### Negative
- Cannot build with Java 8, 11, 17 (must use 21+)
- Requires developers to learn Java 21 syntax
- Some enterprise environments may have deployment constraints

## Alternatives Considered

### Alternative 1: Java 17 (Previous LTS)
- **Pros**: Broader adoption, more mature ecosystem
- **Cons**: Missing records refinements, no virtual threads, expires 2026
- **Why rejected**: Java 21 is now available with 5 years more support

### Alternative 2: Java 11 (Widely Supported LTS)
- **Pros**: Maximum compatibility, mature libraries
- **Cons**: No modern features, expired support is near (Sept 2023)
- **Why rejected**: Missing years of language improvements

### Alternative 3: Wait for Java 25+ (Future)
- **Pros**: Even more features, longer development time
- **Cons**: Delays project start, Java 21 LTS is sufficient now
- **Why rejected**: No benefit to waiting; Java 21 meets all needs

## Implementation Notes

### Key Modern Features to Use

#### Records for Data Classes
```java
// Replaces ~15 lines of boilerplate
public record User(String id, String name, String email) {}
```

#### Sealed Classes for Constrained Hierarchies
```java
public sealed class Response permits SuccessResponse, ErrorResponse {}
```

#### Text Blocks for Multi-line Strings
```java
String sql = """
    SELECT * FROM users
    WHERE email = ?
    ORDER BY name
    """;
```

#### Var for Type Inference
```java
var users = userService.getAllUsers();  // Clear what type is
var email = user.email();                // Obvious from context
```

### Build Configuration
In `build.gradle`:
```gradle
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
```

## Related ADRs
- ADR-0001: Multi-Module Architecture
- ADR-0003: Gradle Build Tool Selection

## References
- [Java 21 Release Notes](https://www.oracle.com/java/technologies/javase/21all-relnotes.html)
- [Records Specification](https://docs.oracle.com/javase/specs/jls/se21/html/jls-8.html#jls-8.10)
- [Sealed Classes Specification](https://docs.oracle.com/javase/specs/jls/se21/html/jls-8.html#jls-8.1.1.3)
- [Text Blocks Specification](https://openjdk.org/jeps/378)

## Approval
- [x] Approved by Project Lead (2026-03-01)
- [x] Build configured for Java 21: 2026-03-01
