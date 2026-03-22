# Gemini CLI Context - Washer Backend v2

> Project root context for conversational assistance and architectural guidance.

## Project Purpose
Korean dormitory laundry management system: washing machine reservations, user management, notifications, Discord/SmartThings integrations.

## Technology Stack
- **Java 25** (with `--enable-preview`)
- **Spring Boot 4.0.1**, Spring Framework 6.2.1
- **MySQL** + Spring Data JPA + Hibernate + QueryDSL
- **OpenFeign** (Discord, SmartThings APIs)
- **Spotless** (Eclipse formatter), OpenAPI/Swagger
- **Gradle 8.11.1** (Kotlin DSL)

## Architecture: Layered DDD
```
domain/              # Entities extending BaseEntity, business logic
service/             # Interface + Impl pattern, single-method services
repository/          # JPA + QueryDSL custom queries
controller/          # REST endpoints returning CommonApiResDto<T>
dto/                 # Records (no from() methods)
exception/           # ExpectedException subclasses
config/, util/       # Configuration and utilities
```

## Critical Conventions

**Domain DTOs:** Records only, NO `from()` methods, suffix `ReqDto`/`ResDto`, Jakarta validation, manual mapping (CommonApiResDto is a wrapper, not a domain DTO)

**Services:** Single method per interface, `{Action}{Entity}Service` + `{Interface}Impl`, `@Transactional(readOnly=true)` for queries

**Entities:** Extend `BaseEntity`, `@Version` required, `@Builder`, `FetchType.LAZY`, business methods in Korean

**Controllers:** Return `CommonApiResDto<T>`, `@Tag`/`@Operation` (Korean), `@Valid` validation

**Repositories:** Extend `JpaRepository`, custom queries via `{Entity}RepositoryCustom` + QueryDSL impl

## Code Formatting (Spotless)
- **120 chars max**, 4 spaces indent
- Command: `./gradlew spotlessApply`
- Eclipse formatter config: `eclipse-formatter.xml`

## Korean Language Requirement
**ALL documentation, comments, and messages in Korean:**
- Javadoc, inline comments
- Exception and validation messages
- Test `@DisplayName` annotations
- API documentation (`@Operation`, `@Tag`)
- Commit messages

## Git Commits (Korean)
- `add/update/fix/delete/docs/test/merge/init: {설명}`
- Branches: `master`, `develop`, `feat/`, `fix/`, `docs/`

## Testing
- BDD: `@Nested` classes, Korean `@DisplayName`, Given-When-Then

## Summary of Special Rules
1. Domain DTOs: Records only, NO `from()` methods
2. Services: Single method, interface + Impl
3. Entities: `@Version` required
4. Controllers: `CommonApiResDto<T>` wrapper
5. Manual DTO mapping (no MapStruct)
6. All docs/comments in Korean
