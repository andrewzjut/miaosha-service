---
paths:
  - "**/*.java"
  - "**/pom.xml"
---
# Java Security

> This file extends [common/security.md](../common/security.md) with Java/Spring Boot specific content.

## Input Validation

Use Bean Validation (JSR 380) on all request DTOs:

```java
public record CreateUserRequest(
    @NotBlank @Email String email,
    @NotBlank @Size(min = 8, max = 50) String password,
    @NotBlank String name
) {}
```

Controller-level enforcement:

```java
@PostMapping
public ResponseEntity<UserDto> createUser(
        @Valid @RequestBody CreateUserRequest request) {
    // request is guaranteed valid here
}
```

## SQL Injection Prevention

Use parameterized queries with JPA:

```java
// CORRECT
@Query("SELECT u FROM User u WHERE u.email = :email")
Optional<User> findByEmail(@Param("email") String email);

// WRONG - vulnerable to injection
@Query("SELECT u FROM User u WHERE u.email = '" + email + "'")
```

## XSS Prevention

Spring Boot auto-escapes Thymeleaf templates. For custom HTML:

```java
import org.springframework.web.util.HtmlUtils;

String safe = HtmlUtils.htmlEscape(userInput);
```

## CSRF Protection

Enabled by default in Spring Security. For stateless APIs:

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        http.csrf(csrf -> csrf.disable());
        return http.build();
    }
}
```

## Secret Management

```java
@Configuration
public class ApiConfig {

    @Value("${api.secret.key}")
    private String apiKey;

    // Or use @ConfigurationProperties for type-safe binding
}
```

Never commit secrets. Use environment variables:

```properties
# application.properties
api.secret.key=${API_SECRET_KEY}
```

## Spring Security Checklist

- [ ] Authentication configured (JWT, OAuth2, or session-based)
- [ ] Authorization rules on all endpoints
- [ ] Password encoding with BCrypt
- [ ] CORS configured for frontend origins
- [ ] Security headers enabled (HSTS, CSP, X-Frame-Options)
- [ ] Rate limiting on auth endpoints

## Dependency Scanning

```bash
# OWASP Dependency Check
mvn org.owasp:dependency-check-maven:check

# Maven Audit
mvn org.sonatype.ossindex.maven:ossindex-maven-plugin:audit
```

## Reference

See skill: `springboot-security` for comprehensive Spring Security patterns.
