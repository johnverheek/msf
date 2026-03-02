# MSF Architecture

## System Overview

MSF is a multi-module Java 21 application with a layered architecture:

```
┌─────────────────────────────┐
│    msf-fabric               │  Integration & Implementations
│  (Integration Layer)        │
└──────────────┬──────────────┘
               │ depends on
┌──────────────▼──────────────┐
│    msf-core                 │  Core Business Logic
│  (Business Logic Layer)     │
└─────────────────────────────┘
```

## Module Responsibilities

### msf-core
**Purpose**: Pure business logic and domain abstractions

**Characteristics**:
- No external dependencies (Java stdlib only)
- No dependencies on other modules
- Defines interfaces and abstractions
- Contains domain models and core algorithms
- No integration or framework-specific code

**Package Structure** (to be defined):
```
com.example.msf.core
├── model/          # Domain objects, records
├── service/        # Business logic interfaces
├── util/           # Utility functions
└── exception/      # Domain exceptions
```

**Examples**:
- Data validation logic
- Business rule enforcement
- Domain models (Records preferred)
- Core algorithms

### msf-fabric
**Purpose**: Integration layer and concrete implementations

**Characteristics**:
- Depends on msf-core for abstractions
- Framework integrations and external libraries
- Concrete implementations of core interfaces
- May depend on external libraries
- Bridge between core logic and external systems

**Package Structure** (to be defined):
```
com.example.msf.fabric
├── service/        # Implementations of core services
├── integration/    # External system integrations
├── config/         # Configuration management
└── adapter/        # Adapters for external data formats
```

**Examples**:
- Database implementations
- API clients
- Configuration providers
- Framework-specific code

## Dependency Model

```
External Libraries
       ↓
msf-fabric ← can depend on external libraries
       ↑
    (implements)
       ↑
msf-core ← pure logic, no dependencies
```

## Design Patterns & Principles

### 1. Separation of Concerns
- Core provides abstractions; fabric provides implementations
- Each module has single responsibility
- Clear contracts between modules

### 2. Dependency Inversion
- High-level modules (fabric) depend on abstractions (core)
- Concrete implementations are decoupled from clients
- Use interfaces in core, implementations in fabric

### 3. Immutability
- Prefer immutable objects (Java records)
- Reduces bugs and side effects
- Easier to reason about state

### 4. Fail-Fast
- Validate inputs at module boundaries
- Throw exceptions for invalid states
- Clear error messages

## Module Communication

### Recommended Patterns

**1. Interface-based Integration**
```java
// In msf-core
public interface UserService {
    User getUserById(String userId);
    void saveUser(User user);
}

// In msf-fabric
public class UserServiceImpl implements UserService {
    // Implementation details
}
```

**2. Data Transfer Objects (DTOs)**
- Use records for immutable DTOs
- Defined in core
- Used at module boundaries

**3. Exception Handling**
- Custom exceptions in core for domain errors
- Fabric handles infrastructure exceptions
- Wrap external exceptions at boundaries

## Data Flow

```
External System
       ↓ (adapter)
msf-fabric (Adapter layer)
       ↓ (interface call)
msf-core (Business logic)
       ↑ (return domain object)
msf-fabric (Translate to external format)
       ↓
External System
```

## Scalability Considerations

1. **Horizontal Scaling**: Stateless services in fabric
2. **Vertical Specialization**: Separate concerns within modules
3. **Caching**: Implement in fabric with core-defined interfaces
4. **Async Operations**: Facade patterns in fabric for async msf-core calls

## Security Considerations

1. **Input Validation**: msf-core validates
2. **Access Control**: msf-fabric enforces
3. **Sensitive Data**: msf-core defines sensitivity; fabric handles securely
4. **Audit Logging**: msf-fabric logs integration points

## Future Expansion

As the project grows:
- Separate read/write models (CQRS pattern in fabric)
- Event-driven architecture (fabric publishes events from core operations)
- Additional integration modules (msf-database, msf-api, etc.)
- Domain-specific languages (DSL) in core for complex logic

## Testing Architecture

```
msf-core Tests
├── Unit tests (pure logic)
└── Domain model tests

msf-fabric Tests
├── Unit tests (with mock core)
├── Integration tests (with real core)
└── Contract tests (core ↔ fabric boundary)
```

## References
- [Module Guide](MODULE_GUIDE.md)
- [API Guidelines](API_GUIDELINES.md)
- Architecture Decision Records in [ADR/](ADR/)
