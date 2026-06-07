# Claude Code Instructions - Washer Backend v2

## Project Context
Korean dormitory laundry management system with Java 25 + Spring Boot 4.0.1. Features: washing machine reservations, user auth, notifications, Discord/SmartThings integrations.

## Tech Stack
- Java 25 (`--enable-preview`), Spring Boot 4.0.1
- MySQL + Spring Data JPA + QueryDSL
- OpenFeign, OpenAPI/Swagger, Lombok, Spotless
- Gradle 8.11.1 (Kotlin DSL)

## Architecture (Layered DDD)
- **domain/**: Entities extending BaseEntity (BaseEntity provides `@Version`)
- **service/**: Interface + Impl, single method per service
- **repository/**: JpaRepository + QueryDSL custom queries
- **controller/**: REST endpoints returning DTOs directly (SDK auto-wraps)
- **dto/**: Records only (NO `from()` methods)
- **exception/**: Code throws SDK `ExpectedException` directly (no subclassing)

## Code Style (Spotless)
- 120 chars max, 4 spaces indent
- Command: `./gradlew spotlessApply` or `/spotless-format`
- Use `final`, `var`, method references

## Critical Patterns

**Domain DTOs (Records Only):**
- Always records, never classes
- NO `from()` static factory methods
- Suffix: `ReqDto`/`ResDto`
- Jakarta validation annotations
- Manual mapping in service (no MapStruct)
- Note: `CommonApiResponse` (`team.themoment.sdk.response`) is the SDK wrapper, not a domain DTO

**Services (Single Responsibility):**
- One method per interface: `{Action}{Entity}Service`
- Implementation: `{Interface}Impl`
- `@Transactional(readOnly = true)` for queries
- `@Transactional` for writes
- Manual DTO mapping in private methods

**Entities:**
- Extend `BaseEntity`, which provides `id`, `createdAt`, `updatedAt`, and `@Version` (optimistic locking) — do NOT redeclare these per entity
- Exception: an entity that cannot extend `BaseEntity` (e.g. `SmartThingsToken`) declares its own `@Version`
- `@Builder`, `@NoArgsConstructor(access = PROTECTED)`, `@AllArgsConstructor` (required so `@Builder` has an all-args constructor to back)
- `FetchType.LAZY` for all relationships
- Business methods in Korean with Javadoc

**Controllers:**
- Response wrapping is handled by the SDK (`sdk.response.enabled: true`)
- Return the response DTO directly — the SDK wraps it automatically
- Return `CommonApiResponse` (`team.themoment.sdk.response`) directly only when there is no response body
- `@Tag`, `@Operation` for OpenAPI (Korean)
- `@Valid` for request validation
- Inject single-method services

**Repositories:**
- Extend `JpaRepository<Entity, ID>`
- Custom: `{Entity}RepositoryCustom` + QueryDSL impl

**Exceptions:**
- Throw the SDK's `ExpectedException` directly: `new ExpectedException("메시지", HttpStatus.X)` — do NOT subclass it
- `GlobalExceptionHandler` maps `ExpectedException` to the response

## Korean Language Requirement
**ALL documentation, comments, and messages in Korean:**
- Javadoc, inline comments
- Exception/validation messages
- Test `@DisplayName`
- API docs (`@Operation`, `@Tag`)
- Commit messages

**Exception: Log messages must be in English** (`log.info/warn/error`)
- Use `key=value` format for structured fields — no colons
- e.g. `log.info("user withdrawn studentId={}", id)` (O) / `log.info("탈퇴: studentId={}", id)` (X)

## Git & Testing
- Branches: `master`, `develop`, `feat/`, `fix/`, `docs/`
- Commits (Korean): `add/update/fix/delete/docs/test/merge/init: {설명}`
- Testing: BDD with `@Nested`, Korean `@DisplayName`, Given-When-Then
- Commands: `/spotless-format`, `/split-commits`
