# MSF Module Guide

Detailed guide to developing within each module.

## Overview

```
msf-core ←── Pure business logic, no dependencies
    ↓
msf-fabric ←── Implementations, integrations
```

## msf-core Module

### Purpose
Core business logic and domain abstractions. This module contains zero external dependencies beyond Java stdlib.

### Key Characteristics
- ✅ Pure business logic
- ✅ Domain models
- ✅ Business rules and algorithms
- ✅ Abstract interfaces
- ❌ No external library dependencies
- ❌ No references to msf-fabric

### Package Structure (Planned)
```
com.example.msf.core
├── model/              # Domain objects
│   ├── User.java
│   ├── UserCreationRequest.java
│   └── Status.java
├── service/            # Service interfaces
│   ├── UserService.java
│   ├── PermissionService.java
│   └── ValidationService.java
├── util/               # Utilities
│   ├── StringUtils.java
│   └── DateUtils.java
├── exception/          # Domain exceptions
│   ├── UserNotFoundException.java
│   ├── InvalidDataException.java
│   └── DomainException.java
└── validator/          # Validation logic
    ├── EmailValidator.java
    ├── PasswordValidator.java
    └── DataValidator.java
```

### Development Guidelines

#### Adding a New Domain Model
1. Create record or immutable class in `model/`
2. Include all validation in constructor
3. Add Javadoc
4. Write comprehensive tests

```java
package com.example.msf.core.model;

/**
 * Represents a user in the system.
 */
public record User(
    String id,
    String name,
    String email
) {
    public User {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("ID must not be empty");
        }
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Name must not be empty");
        }
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException("Email must be valid");
        }
    }
}
```

#### Adding a Service Interface
1. Create interface in `service/`
2. Define clear contract with Javadoc
3. Use standard method naming (get*, find*, save*, delete*)
4. Use Optional for optional results

```java
package com.example.msf.core.service;

import java.util.Optional;
import com.example.msf.core.model.User;

/**
 * Service for managing users.
 */
public interface UserService {
    /**
     * Retrieves a user by ID.
     *
     * @param userId the user ID (must not be null)
     * @return Optional containing the user if found
     * @throws IllegalArgumentException if userId is null
     */
    Optional<User> findUser(String userId);

    /**
     * Saves a user.
     *
     * @param user the user to save (must not be null)
     * @throws IllegalArgumentException if user is null
     */
    void saveUser(User user);
}
```

#### Adding Validation Logic
1. Create validator in `validator/` package
2. Keep validation pure (no side effects)
3. Return clear results or throw exceptions

```java
package com.example.msf.core.validator;

/**
 * Validates email addresses.
 */
public class EmailValidator {
    private static final String EMAIL_PATTERN = 
        "^[A-Za-z0-9+_.-]+@(.+)$";

    /**
     * Checks if email format is valid.
     *
     * @param email the email to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValid(String email) {
        if (email == null || email.isEmpty()) {
            return false;
        }
        return email.matches(EMAIL_PATTERN);
    }
}
```

### High-Level Workflows

#### Create a New Feature in Core
1. Define domain model in `model/`
2. Create service interface in `service/`
3. Add validation logic to model constructor
4. Create comprehensive unit tests
5. Document with Javadoc
6. Reference ADR if architectural decisions needed

### Testing msf-core
- Unit tests only (no external dependencies)
- Test pure logic paths
- Test exception cases
- Test immutability contracts
- Target: >85% coverage

---

## msf-fabric Module

### Purpose
Integration layer, implementations of core interfaces, and connections to external systems.

### Key Characteristics
- ✅ Implementations of core service interfaces
- ✅ External integrations (databases, APIs, etc.)
- ✅ Configuration management
- ✅ Adapters for external data formats
- ✅ May depend on external libraries
- ✅ Depends on msf-core
- ❌ Should not obscure core abstractions

### Package Structure (Planned)
```
com.example.msf.fabric
├── service/            # Service implementations
│   └── DefaultUserService.java
├── integration/        # External system integration
│   ├── database/
│   │   └── UserRepository.java
│   ├── api/
│   │   └── ExternalApiClient.java
│   └── messaging/
│       └── EventPublisher.java
├── config/             # Configuration
│   ├── DatabaseConfig.java
│   └── ServiceConfig.java
├── adapter/            # Data format adapters
│   ├── UserJsonAdapter.java
│   └── RequestDtoAdapter.java
└── exception/          # Infrastructure exceptions
    ├── DatabaseException.java
    └── ExternalServiceException.java
```

### Development Guidelines

#### Implementing a Core Service
1. Create class in `service/` implementing core interface
2. Inject dependencies (database, external services)
3. Handle infrastructure concerns (error handling, logging)
4. Add Javadoc
5. Write integration tests

```java
package com.example.msf.fabric.service;

import java.util.Optional;
import com.example.msf.core.model.User;
import com.example.msf.core.service.UserService;
import com.example.msf.fabric.integration.database.UserRepository;

/**
 * Default implementation of UserService using database persistence.
 */
public class DefaultUserService implements UserService {
    private final UserRepository userRepository;

    public DefaultUserService(UserRepository userRepository) {
        if (userRepository == null) {
            throw new IllegalArgumentException("Repository must not be null");
        }
        this.userRepository = userRepository;
    }

    @Override
    public Optional<User> findUser(String userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID must not be null");
        }
        
        try {
            return userRepository.findById(userId);
        } catch (Exception e) {
            throw new RuntimeException("Database error finding user", e);
        }
    }

    @Override
    public void saveUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User must not be null");
        }
        
        try {
            userRepository.save(user);
        } catch (Exception e) {
            throw new RuntimeException("Database error saving user", e);
        }
    }
}
```

#### Adding External Integrations
1. Create interface/class in `integration/`
2. Use adapters to convert between core and external formats
3. Handle external errors gracefully
4. Log integration points

```java
package com.example.msf.fabric.integration.database;

import java.util.Optional;
import com.example.msf.core.model.User;

/**
 * Database repository for User persistence.
 */
public interface UserRepository {
    /**
     * Finds user by ID.
     *
     * @param id the user ID
     * @return Optional with user if found
     * @throws DatabaseException if database access fails
     */
    Optional<User> findById(String id);

    /**
     * Persists a user.
     *
     * @param user the user to save
     * @throws DatabaseException if save fails
     */
    void save(User user);
}
```

#### Adding Adapters
```java
package com.example.msf.fabric.adapter;

import com.example.msf.core.model.User;

/**
 * Adapts between User domain model and JSON representation.
 */
public class UserJsonAdapter {
    /**
     * Converts User to JSON string.
     *
     * @param user the user to convert
     * @return JSON representation
     */
    public static String toJson(User user) {
        // Implementation
        return "{}";
    }

    /**
     * Converts JSON string to User.
     *
     * @param json the JSON string
     * @return parsed User
     * @throws IllegalArgumentException if JSON is invalid
     */
    public static User fromJson(String json) {
        // Implementation
        return null;
    }
}
```

### Testing msf-fabric
- Unit tests with mocked core dependencies
- Integration tests with real core + mocked external systems
- Contract tests verifying core interface implementation
- External integration tests (optional, may use test containers)
- Target: >75% coverage (less critical for integration code)

---

## Cross-Module Communication

### Good Example: Service Implementation
```
msf-fabric/service/DefaultUserService
    ↓ implements
msf-core/service/UserService
    ↓ uses types from
msf-core/model/User
```

### Dependency Injection Pattern
```java
// In fabric: Constructor injection
public DefaultUserService(UserRepository repo, Logger logger) {
    this.repo = repo;
    this.logger = logger;
}

// Clear dependencies, testable, flexible
```

### Error Handling Pattern
```
External System Exception
    ↓ caught in fabric
Wrapped/Translated Exception
    ↓ thrown to caller
Core Business Exception or RuntimeException
```

## Adding New Features

### Step-by-Step: Add User Email Validation Feature

1. **In msf-core:**
   - Add email validation method to UserService interface
   - Create EmailValidator with validation logic
   - Add EmailValidationException
   - Test thoroughly in core tests

2. **In msf-fabric:**
   - Implement email validation in DefaultUserService
   - Integrate with external email service if needed
   - Handle external API errors
   - Write integration tests

3. **Documentation:**
   - Update API_GUIDELINES.md if changing public interface
   - Add Javadoc to new public methods
   - Create ADR if architectural decision needed

## Development Workflow

### For msf-core Changes
```
1. Create domain model/interface
2. Write tests
3. Implement business logic
4. Add Javadoc
5. Update documentation if needed
```

### For msf-fabric Changes
```
1. Implement core interface
2. Add integration setup
3. Write tests (unit + integration)
4. Handle errors gracefully
5. Add logging at integration points
6. Update documentation
```

## References
- [ARCHITECTURE.md](ARCHITECTURE.md)
- [API_GUIDELINES.md](API_GUIDELINES.md)
- [CODING_STANDARDS.md](CODING_STANDARDS.md)
