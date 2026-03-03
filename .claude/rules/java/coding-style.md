---
paths:
  - "**/*.java"
  - "**/pom.xml"
  - "**/.mvn/**"
---
# Java Coding Style

> This file extends [common/coding-style.md](../common/coding-style.md) with Java specific content.

## Formatting

Use **google-java-format** or **Spotless** for consistent formatting:

```bash
mvn spotless:apply
```

## Design Principles

- **Prefer immutability**: Use `final` fields, `record` types (Java 17+), and immutable collections
- **Composition over inheritance**: Favor composition unless inheritance provides clear benefits
- **Small classes**: Single responsibility, <500 lines preferred

## Naming Conventions

- Classes: `PascalCase` (e.g., `UserService`, `OrderController`)
- Methods/variables: `camelCase` (e.g., `findUserById`, `userDto`)
- Constants: `UPPER_SNAKE_CASE` (e.g., `MAX_RETRY_COUNT`)
- Test classes: `*Test` suffix (e.g., `UserServiceTest`)

## Error Handling

Always handle exceptions explicitly:

```java
try {
    userService.createUser(request);
} catch (UserAlreadyExistsException e) {
    log.warn("User creation failed: user already exists", e);
    throw new BusinessException("USER_ALREADY_EXISTS", e);
}
```

- Never swallow exceptions silently
- Wrap checked exceptions with context
- Use custom exception types for domain errors

## Optional Usage

- Use `Optional` for return values, not fields or parameters
- Prefer `map()`, `flatMap()`, `orElse()` over `isPresent()` + `get()`
- Use `Optional.ofNullable()` for nullable external values

## Records (Java 17+)

Use `record` for DTOs and value objects:

```java
public record UserDto(Long id, String email, String name) {}
```

## Reference

See skill: `java-coding-standards` for comprehensive Java idioms.
