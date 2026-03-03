---
paths:
  - "**/*.java"
  - "**/pom.xml"
---
# Java Testing

> This file extends [common/testing.md](../common/testing.md) with Java/JUnit specific content.

## Framework

- **JUnit 5** (`org.junit.jupiter`) for unit tests
- **Mockito** for mocking dependencies
- **AssertJ** for fluent assertions (preferred over Hamcrest)
- **Spring Boot Test** for integration tests

## Test Structure

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void shouldCreateUser_WhenEmailIsValid() {
        // Given
        CreateUserRequest request = new CreateUserRequest("test@example.com");
        given(userRepository.existsByEmail(request.email())).willReturn(false);

        // When
        UserDto result = userService.createUser(request);

        // Then
        assertThat(result.email()).isEqualTo(request.email());
        then(userRepository).should().save(any(User.class));
    }
}
```

## Integration Tests

Use `@SpringBootTest` with `@ActiveProfiles("test")`:

```java
@SpringBootTest
@ActiveProfiles("test")
class UserControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldReturnUser_WhenExists() {
        ResponseEntity<UserDto> response = restTemplate.getForEntity("/api/users/1", UserDto.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
```

## Testcontainers

Use Testcontainers for database integration tests:

```java
@Testcontainers
@SpringBootTest
class UserRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureTest(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
```

## Coverage

```bash
# JaCoCo report
mvn clean test jacoco:report

# Check coverage threshold
mvn test jacoco:check
```

## Reference

See skill: `springboot-tdd` for TDD workflow with Spring Boot.
