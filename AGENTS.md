# AGENTS.md - Washer Backend v2

> Project context and conventions for AI coding agents.

## Project Overview
Korean dormitory laundry management system. Features: washing machine reservations, user auth/management, notifications, Discord/SmartThings integrations.

## Tech Stack
- **Java 25** (`--enable-preview`)
- **Spring Boot 4.0.1**, Spring Framework 6.2.1
- **MySQL** + Spring Data JPA + Hibernate + QueryDSL
- **OpenFeign** (Discord, SmartThings APIs)
- **Spotless** (Eclipse formatter), OpenAPI/Swagger, Lombok
- **Gradle 8.11.1** (Kotlin DSL)
- **the-moment SDK** (`team.themoment.sdk`): response wrapping, `ExpectedException`, logging

## Architecture (Layered DDD)
```
domain/              # Entities extending BaseEntity, business logic
service/             # Interface + Impl pattern, single-method services
repository/          # JpaRepository + QueryDSL custom queries
controller/          # REST endpoints (return DTOs directly; SDK auto-wraps)
dto/                 # Records only (no from() methods)
exception/           # Code throws SDK ExpectedException directly (no subclassing)
config/, util/       # Configuration and utilities
```

## Core Conventions

**Domain DTOs (Records only):**
- Always records, never classes
- NO `from()` static factory methods
- Suffix: `ReqDto` / `ResDto`
- Jakarta validation annotations
- Manual mapping in service (no MapStruct)

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
- Response wrapping is handled by the SDK (`sdk.response.enabled: true` in `application.yml`)
- Return the response DTO directly (e.g. `TokenResDto`, `MachineListResDto`) — the SDK wraps it automatically
- Return `CommonApiResponse` (`team.themoment.sdk.response`) directly only when there is no response body (e.g. delete/withdraw endpoints)
- `@Tag`, `@Operation` for OpenAPI (Korean)
- `@Valid` for request validation
- Inject single-method services

**Repositories:**
- Extend `JpaRepository<Entity, ID>`
- Custom: `{Entity}RepositoryCustom` + QueryDSL impl

**Exceptions:**
- Throw the SDK's `ExpectedException` directly: `new ExpectedException("메시지", HttpStatus.X)` — do NOT subclass it
- `GlobalExceptionHandler` maps `ExpectedException` to the response

## Code Style (Spotless)
- 120 chars max, 4 spaces indent
- Use `final`, `var`, method references
- Command: `./gradlew spotlessApply` or `/spotless-format`
- Eclipse formatter config: `eclipse-formatter.xml`

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

## Special Rules Summary
1. Domain DTOs: records only, NO `from()` methods
2. Services: single method, interface + Impl
3. Entities: extend `BaseEntity` (provides `@Version`); no per-entity `@Version` unless `BaseEntity` is not extended
4. Controllers: return response DTOs directly (SDK auto-wraps); `CommonApiResponse` only when there is no body
5. Exceptions: throw SDK `ExpectedException` directly, do not subclass
6. Manual DTO mapping (no MapStruct)
7. All docs/comments in Korean (logs in English)