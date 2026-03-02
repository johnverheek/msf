# Coding Standards

## Code Formatting

All formatting is governed by `.editorconfig`. Key settings:

- **Indentation**: 4 spaces
- **Line length**: 120 characters
- **Line endings**: LF
- **Charset**: UTF-8
- **Tab usage**: Spaces only, never tabs

## Language Features

### Java 21 Idioms

#### Records (Preferred for Immutable Data)
```java
// Good: Use records for data carriers
public record User(String id, String name, String email) {}

// Access via simple getters
User user = new User("123", "John", "john@example.com");
String name = user.name();
```

#### Sealed Classes
```java
// Good: Constrain hierarchy with sealed classes
public sealed class Response
    permits SuccessResponse, ErrorResponse {}

public final class SuccessResponse extends Response { }
public final class ErrorResponse extends Response { }
```

#### Text Blocks
```java
// Good: Use text blocks for multi-line strings
String sql = """
    SELECT * FROM users
    WHERE email = ?
    AND active = true
    """;
```

#### Var Keyword
```java
// Good: Use var when type is obvious
var users = userService.getAllUsers();
var email = user.email();

// Avoid: When type is unclear
var x = complexCalculation();  // What is x?
```

## Naming Conventions

### Classes & Interfaces
```java
// Classes: PascalCase, concrete nouns
public class UserManager { }
public class DataProcessor { }

// Interfaces: PascalCase, often adjectives or nouns
public interface UserService { }
public interface Serializable { }
public interface Comparable<T> { }
```

### Methods
```java
// Queries: get*, find*, is*, has*
public User getUser(String id);
public Optional<User> findUser(String id);
public boolean isActive();
public boolean hasPermission(String perm);

// Commands: save*, delete*, create*, update*
public void saveUser(User user);
public void deleteUser(String id);
public User createUser(UserCreationRequest req);
public void updateUser(User user);

// Static methods
public static Optional<User> fromJson(String json);
public static File createTemporaryFile();
```

### Variables & Constants
```java
// Local variables and parameters: camelCase
public void process(String userName, int maxRetries) {
    String processedData = transform(userName);
    int attempts = 0;
}

// Constants: UPPER_SNAKE_CASE
public static final int MAX_RETRY_ATTEMPTS = 3;
public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
private static final Logger LOGGER = LoggerFactory.getLogger(MyClass.class);

// Enum constants: UPPER_SNAKE_CASE
public enum Status {
    ACTIVE, INACTIVE, PENDING;
}
```

### Packages
```java
// Lowercase, no underscores, hierarchical
com.example.msf.core
com.example.msf.core.model
com.example.msf.core.service
com.example.msf.fabric
com.example.msf.fabric.integration
```

## Code Organization

### Class Member Order
```java
public class MyClass {
    // 1. Static fields
    public static final SomeType CONSTANT = ...;
    private static final Logger LOGGER = ...;
    private static SomeType staticField;
    
    // 2. Static initializers
    static {
        // initialization
    }
    
    // 3. Instance fields
    private String field1;
    private int field2;
    
    // 4. Constructors
    public MyClass() { }
    public MyClass(String field1) { }
    
    // 5. Public methods
    public void publicMethod() { }
    
    // 6. Package-private methods
    void packagePrivateMethod() { }
    
    // 7. Protected methods
    protected void protectedMethod() { }
    
    // 8. Private methods
    private void privateMethod() { }
    
    // 9. Inner classes
    private static class InnerClass { }
}
```

### Method Organization Within Class
- Related methods grouped together
- High-level public methods before detailed private methods
- Getters/setters grouped together

## Comments & Documentation

### Javadoc Requirements
All public elements must have Javadoc:

```java
/**
 * Brief description of the method (one line).
 *
 * <p>Longer description with more details if needed.
 * Can span multiple lines and paragraphs.
 *
 * @param paramName description of parameter
 * @return description of return value
 * @throws ExceptionType description of when thrown
 * @since 1.0
 * @see RelatedMethod
 */
public Object publicMethod(String paramName) throws ExceptionType {
    // ...
}
```

### Inline Comments
Use sparingly. When needed:

```java
// Use for non-obvious algorithm decisions
if (count > 100) {
    // Performance optimization: batch process to avoid
    // excessive database queries (see ADR-005)
    processBatch(items);
}

// Avoid: Obvious comments
int x = 5; // Set x to 5
```

## Defensive Programming

### Null Checks
```java
// Good: Check preconditions
public void saveUser(User user) {
    if (user == null) {
        throw new IllegalArgumentException("User must not be null");
    }
    // ...
}

// Better: Use Optional where appropriate
public Optional<User> findUser(String id) {
    // ...
}

// Use: Non-null assertions in code
if (user == null) {
    throw new IllegalStateException("User should have been initialized");
}
```

### Immutability
```java
// Good: Declare fields as final
private final String id;
private final List<String> tags;

// Good: Return unmodifiable collections
public List<String> getTags() {
    return Collections.unmodifiableList(tags);
}

// Better: Use records
public record User(String id, String name) { }
```

## Error Handling

### Exception Handling
```java
// Good: Catch specific exceptions
try {
    processFile(file);
} catch (FileNotFoundException e) {
    LOGGER.warn("File not found: {}", file, e);
} catch (IOException e) {
    throw new DataProcessingException("Failed to process file", e);
}

// Avoid: Catching Exception or Throwable
try {
    processFile(file);
} catch (Exception e) {  // Too broad!
    // ...
}

// Avoid: Silent failures
try {
    processFile(file);
} catch (IOException ignored) {  // Dangerous!
}
```

## Testing Standards

### Test Naming
```java
public class UserServiceTest {
    // Pattern: test<MethodName><Condition>
    @Test
    void testFindUserWhenUserExists() { }
    
    @Test
    void testFindUserWhenUserNotFound() { }
    
    @Test
    void testSaveUserThrowsWhenUserIsNull() { }
    
    @Test
    void testCreateUserWithValidData() { }
}
```

### Assertions
```java
// Use JUnit 4 assertions
import static junit.framework.Assert.*;

@Test
void testUserCreation() {
    User user = new User("123", "John", "john@example.com");
    
    assertEquals("123", user.id());
    assertEquals("John", user.name());
    assertTrue(user.email().contains("@"));
}
```

## Imports

### Ordering
1. Java standard library
2. Third-party libraries
3. Internal project imports

```java
import java.util.Optional;
import java.util.List;

import junit.framework.TestCase;

import com.example.msf.core.model.User;
import com.example.msf.core.service.UserService;
```

### Guidelines
- Use specific imports, never wildcard `import com.example.*`
- Remove unused imports
- Organize with IDE auto-import feature

## Line Length & Readability

Keep lines under 120 characters. Break long lines:

```java
// Good: Break after opening paren
User user = userService.findUserByEmailWithPermission(
    email,
    "ADMIN"
);

// Good: Break chained method calls
List<User> activeUsers = users.stream()
    .filter(User::isActive)
    .map(this::enrichUser)
    .collect(Collectors.toList());
```

## Code Review Checklist

- [ ] Follows naming conventions
- [ ] Proper indentation (4 spaces)
- [ ] No lines exceed 120 characters
- [ ] All public APIs have Javadoc
- [ ] No unused imports
- [ ] Proper exception handling
- [ ] Immutable where appropriate
- [ ] Unit tests present and meaningful
- [ ] No commented-out code (except temporary debugging)
- [ ] No System.out.println (use logging)

## References
- [API Guidelines](API_GUIDELINES.md)
- [.editorconfig](.editorconfig)
- [.claude_rules](.claude_rules)
