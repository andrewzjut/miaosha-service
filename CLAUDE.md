# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Tech Stack

- **Language**: Java 17
- **Framework**: Spring Boot 3.4.3
- **Build Tool**: Maven
- **Package Manager**: Maven (configured in `.claude/package-manager.json`)

## Commands

```bash
# Build
mvn clean compile

# Run tests
mvn test

# Run single test class
mvn test -Dtest=ClassName

# Run with coverage
mvn clean test jacoco:report

# Package
mvn clean package

# Run application
mvn spring-boot:run

# Dependency tree
mvn dependency:tree
```

## Project Structure

Standard Spring Boot layered architecture:
- `src/main/java` - Application source code
- `src/main/resources` - Configuration, static assets, templates
- `src/test/java` - Test source code
- `pom.xml` - Maven build configuration

Entry point: `DemoApplication.java`

## Development Workflow

1. **Plan**: Use `everything-claude-code:plan` for complex features
2. **TDD**: Use `everything-claude-code:tdd` or `everything-claude-code:springboot-tdd` for test-first development
3. **Review**: Use `everything-claude-code:code-reviewer` after writing code
4. **Security**: Use `everything-claude-code:security-reviewer` for auth, input handling, secrets
5. **Verify**: Use `everything-claude-code:springboot-verification` before commit/PR

## Configuration

### Enabled Plugins
- **everything-claude-code**: Full skill and command suite from [affaan-m/everything-claude-code](https://github.com/affaan-m/everything-claude-code)

### Permissions
- Web search enabled
- GitHub web fetch enabled

## Project Rules

Project-specific rules in `.claude/rules/`:

**Common rules** (`.claude/rules/common/`):
- **coding-style.md**: Immutability, file organization, error handling, input validation
- **git-workflow.md**: Conventional commits, PR workflow
- **testing.md**: 80%+ coverage requirement (unit, integration, E2E)
- **security.md**: Mandatory security checks before commit
- **performance.md**: Model selection strategy, context management
- **hooks.md**: Pre/Post tool use hooks
- **agents.md**: Available specialist agents
- **development-workflow.md**: Feature implementation workflow
- **patterns.md**: Repository pattern, API response format

**Java rules** (`.claude/rules/java/`) extend common rules with:
- **coding-style.md**: google-java-format, naming, Optional, records, error handling
- **testing.md**: JUnit 5, Mockito, AssertJ, Testcontainers, JaCoCo
- **patterns.md**: Layered architecture, controller/service/repository patterns
- **hooks.md**: Auto-format with Spotless, Maven compilation hooks
- **security.md**: Bean validation, SQL injection prevention, Spring Security

## Java/Spring Boot Skills

This project uses everything-claude-code Java/Spring Boot skills:
- `everything-claude-code:springboot-patterns` - REST API design, layered services, data access
- `everything-claude-code:springboot-tdd` - TDD with JUnit 5, Mockito, Testcontainers
- `everything-claude-code:springboot-security` - Spring Security, authn/authz, CSRF, secrets
- `everything-claude-code:springboot-verification` - Build, lint, test, security scan before release
- `everything-claude-code:jpa-patterns` - JPA/Hibernate entity design, relationships, optimization
- `everything-claude-code:java-coding-standards` - Naming, Optional, streams, exceptions
- `everything-claude-code:database-migrations` - Schema changes, zero-downtime deployments
- `everything-claude-code:postgres-patterns` - PostgreSQL query optimization, schema design

## Dependencies

Core dependencies in `pom.xml`:
- `spring-boot-starter-web` - REST API, embedded Tomcat
- `spring-boot-starter-validation` - Bean validation (JSR 380)
- `spring-boot-starter-actuator` - Health checks, metrics
- `lombok` - Boilerplate reduction (optional)
- `spring-boot-starter-test` - JUnit 5, Mockito, Spring Test
