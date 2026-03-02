# API Guidelines

## Public API Design

This document outlines principles for designing public APIs in MSF modules.

## Principles

### 1. Clarity Over Cleverness
- Use clear, descriptive names
- Prefer explicitness to implicit behavior
- One way to do things, not multiple ways

### 2. Immutability by Default
```java
// Prefer records for data carriers
public record User(String id, String name, String email) {}

// Or immutable classes
public final class UserData {
    private final String id;
    //...
}
```

### 3. Fail-Fast Validation
```java
public class UserService {
    /**
     * Saves a user.
     * @param user the user to save (must not be null)
     * @throws IllegalArgumentException if user is null
     */
    public void saveUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User must not be null");
        }
        // ...
    }
}
```

### 4. Prefer Checked Operations Over Nulls
```java
// Instead of returning null
// public User findUser(String id) { ... }

// Use Optional
public Optional<User> findUser(String id) {
    // ...
}

// Or custom Result type
public Result<User, UserNotFound> findUser(String id) {
    // ...
}
```

### 5. Compose Methods Not Parameters
```java
// Bad: too many parameters
public void createUser(String name, String email, boolean active, 
                       LocalDate createdDate, String source) { }

// Good: use builder or records
public void createUser(UserCreationRequest request) { }

public record UserCreationRequest(
    String name,
    String email,
    boolean active,
    LocalDate createdDate,
    String source
) {}
```

## Documentation Standards

### Javadoc Requirements

All public APIs must have Javadoc:

```java
/**
 * Validates an email address format.
 *
 * @param email the email to validate (must not be null or empty)
 * @return true if valid, false otherwise
 * @throws IllegalArgumentException if email is null or empty
 * 
 * @since 1.0
 */
public boolean isValidEmail(String email) {
    // ...
}
```

### Contract Documentation
- Describe preconditions (what must be true before calling)
- Describe postconditions (what will be true after calling)
- Document side effects
- List exceptions that may be thrown

### Example Javadoc
```java
/**
 * Retrieves a user by their unique identifier.
 *
 * <p>This method queries the underlying data store synchronously.
 * For large datasets, consider using the async variant.
 *
 * @param userId the unique user identifier (must not be null)
 * @return an Optional containing the user if found, empty otherwise
 * @throws IllegalArgumentException if userId is null
 * @throws DataAccessException if database access fails
 *
 * @see #findUserAsync(String)
 * @since 1.0
 */
public Optional<User> findUser(String userId) {
    // ...
}
```

## Interface Design

### Keep Interfaces Cohesive
```java
// Good: Single responsibility
public interface UserService {
    Optional<User> findUser(String id);
    void saveUser(User user);
    void deleteUser(String id);
}

// Bad: Multiple concerns
public interface UserService {
    Optional<User> findUser(String id);
    String generateReport();        // Wrong interface!
    void configureDatabase();       // Wrong interface!
}
```

### Use Default Methods with Caution
```java
public interface UserService {
    Optional<User> findUser(String id);
    
    // Only for backward compatibility or convenience
    default Optional<User> findUserByEmail(String email) {
        // Common implementation
    }
}
```

## Naming Conventions

### Methods
- **Query**: `get*`, `find*`, `is*`, `has*`
- **Command**: `save*`, `delete*`, `create*`, `update*`
- **Batch**: `get*List`, `find*All`

```java
public class UserService {
    // Query
    User getUser(String id);
    Optional<User> findUser(String id);
    List<User> getAllUsers();
    boolean isActive(String userId);
    
    // Command
    void saveUser(User user);
    void deleteUser(String id);
    User createUser(UserCreationRequest request);
}
```

### Constants
```java
public static final int MAX_RETRY_ATTEMPTS = 3;
public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
public static final String CONFIG_KEY_PREFIX = "msf.config.";
```

## Version Compatibility

### Breaking Changes
- Only in major versions
- Document in CHANGELOG
- Provide deprecation period in previous version

### Deprecation
```java
/**
 * @deprecated Use {@link #findUser(String)} instead.
 * This method will be removed in version 2.0.
 */
@Deprecated(since = "1.5", forRemoval = true)
public User getUser(String id) {
    return findUser(id).orElse(null);
}
```

## Error Handling

### Custom Exceptions
```java
// In msf-core
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String userId) {
        super("User not found: " + userId);
    }
}

public class InvalidUserDataException extends RuntimeException {
    public InvalidUserDataException(String message) {
        super(message);
    }
}
```

### Document Expected Exceptions
```java
/**
 * Retrieves a user.
 *
 * @param userId the user ID
 * @return the user
 * @throws UserNotFoundException if user not found
 * @throws InvalidUserIdException if userId format is invalid
 */
public User getUser(String userId) {
    // ...
}
```

## Testing Public APIs

### Contract Tests
Ensure public API contracts are tested:

```java
@Test
void shouldThrowWhenUserIdIsNull() {
    assertThrows(IllegalArgumentException.class, 
        () -> userService.getUser(null));
}

@Test
void shouldReturnEmptyWhenUserNotFound() {
    Optional<User> result = userService.findUser("unknown");
    assertTrue(result.isEmpty());
}
```

## Review Checklist

When reviewing public API additions:
- [ ] Clear, descriptive names
- [ ] Complete Javadoc documentation
- [ ] Preconditions documented and validated
- [ ] Exceptions clearly defined and thrown
- [ ] Immutable where appropriate
- [ ] Tests covering happy path and error cases
- [ ] No breaking changes (unless major version)
- [ ] Follows module-specific conventions

## References
- [CODING_STANDARDS.md](CODING_STANDARDS.md)
- [ARCHITECTURE.md](ARCHITECTURE.md)
