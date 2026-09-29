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

## GitHub Issue and PR Rules
- Do not use `[P0]` through `[P4]` priority prefixes in new Issue titles. Represent priority with a GitHub risk label instead.
- Apply exactly one risk label to each Issue. Work-type labels such as `버그`, `리팩터링`, `신규 기능`, `문서화`, and `삭제` are a separate axis and may be kept alongside the risk label.
- Issue authors who cannot manage GitHub labels must state one requested risk label in the template. A maintainer must apply exactly one risk label during triage; an unlabeled Issue is allowed only before triage is complete.
- Use these risk criteria:
  - `위험도: 긴급`: Authentication or authorization bypasses, token or secret exposure, data loss, or problems requiring immediate safety response. Example: an unauthenticated user can trigger an admin device sync, or an auth token is written to logs.
  - `위험도: 높음`: Core-flow outages, incorrect state transitions, data-integrity issues, or concurrency problems affecting reservations or device control and requiring prompt action. Example: a new reservation can power off a machine that is already running, or duplicate reservations are accepted for one user.
  - `위험도: 보통`: Limited-scope functional errors, API contract issues, user guidance problems, or operational policy improvements. Example: a specific input error does not identify the field the user must fix.
  - `위험도: 낮음`: Performance, observability, or maintainability improvements where the feature remains functional. Example: batching a query to reduce N+1 behavior or adding trace context to asynchronous logs.
  - `위험도: 정리`: Low-impact removal of unused code, configuration, dependencies, or duplication. Example: deleting unused Repository methods.
- PR label selection must use only PR labels appropriate for the code change. Risk labels such as `위험도: 긴급` are Issue-only and must never be added to PRs automatically.
