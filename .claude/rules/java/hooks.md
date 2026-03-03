---
paths:
  - "**/*.java"
  - "**/pom.xml"
---
# Java Hooks

> This file extends [common/hooks.md](../common/hooks.md) with Java specific content.

## PostToolUse Hooks

Configure in `~/.claude/settings.json` or project `.claude/hooks.json`:

### Auto-format on Java file edit

```json
{
  "PostToolUse": [
    {
      "if": {
        "pathMatches": "**/*.java"
      },
      "run": "mvn spotless:apply -q"
    }
  ]
}
```

### Run compilation after Java edits

```json
{
  "PostToolUse": [
    {
      "if": {
        "pathMatches": "**/*.java",
        "tool": "Write|Edit"
      },
      "run": "mvn compile -q -DskipTests"
    }
  ]
}
```

### Run tests after test file edits

```json
{
  "PostToolUse": [
    {
      "if": {
        "pathMatches": "**/src/test/java/**/*.java"
      },
      "run": "mvn test -Dtest=${fileBasenameNoExtension} -q"
    }
  ]
}
```

## Maven Plugins

Recommended plugins in `pom.xml`:

```xml
<plugin>
    <groupId>com.diffplug.spotless</groupId>
    <artifactId>spotless-maven-plugin</artifactId>
    <version>2.43.0</version>
    <configuration>
        <java>
            <googleJavaFormat/>
        </java>
    </configuration>
</plugin>

<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-checkstyle-plugin</artifactId>
    <version>3.4.0</version>
</plugin>
```
