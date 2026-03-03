---
paths:
  - "**/*.java"
  - "**/pom.xml"
---
# Java Patterns

> This file extends [common/patterns.md](../common/patterns.md) with Java/Spring Boot specific content.

## Layered Architecture

```
com.example.demo
├── controller/     # HTTP endpoints, request/response mapping
├── service/        # Business logic, transactions
├── repository/     # Data access, JPA repositories
├── domain/         # Core business entities
├── dto/            # Data transfer objects
└── config/         # Configuration classes
```

## Controller Pattern

```java
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<UserDto> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserDto user = userService.createUser(request);
        return ResponseEntity
                .created(URI.create("/api/users/" + user.id()))
                .body(user);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUser(id));
    }

    @ExceptionHandler(UserNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<ErrorResponse> handleNotFound(UserNotFoundException e) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("USER_NOT_FOUND", e.getMessage()));
    }
}
```

## Service Pattern

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserDto createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new UserAlreadyExistsException(request.email());
        }

        User user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .build();

        User saved = userRepository.save(user);
        return UserDto.from(saved);
    }
}
```

## Repository Pattern

```java
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findByCreatedAtBefore(LocalDateTime date);
}
```

## Builder Pattern

Use Lombok `@Builder` for complex objects:

```java
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class User {
    private Long id;
    private String email;
    private String password;
    private LocalDateTime createdAt;
}
```

## Dependency Injection

Constructor injection (preferred):

```java
@Service
@RequiredArgsConstructor  // Lombok generates constructor for final fields
public class UserService {
    private final UserRepository userRepository;
    private final EmailService emailService;
}
```

## Reference

See skills:
- `springboot-patterns` - Spring Boot architecture patterns
- `jpa-patterns` - JPA/Hibernate entity and repository patterns
