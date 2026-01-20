# Claude Code Instructions - Washer Backend v2

## Project Context
Korean dormitory laundry management system with Java 25 + Spring Boot 4.0.1. Features: washing machine reservations, user auth, notifications, Discord/SmartThings integrations.

## Tech Stack
- Java 25 (`--enable-preview`), Spring Boot 4.0.1
- MySQL + Spring Data JPA + QueryDSL
- OpenFeign, OpenAPI/Swagger, Lombok, Spotless
- Gradle 8.11.1 (Kotlin DSL)

## Architecture (Layered DDD)
- **domain/**: Entities extending BaseEntity, include `@Version`
- **service/**: Interface + Impl, single method per service
- **repository/**: JpaRepository + QueryDSL custom queries
- **controller/**: REST endpoints returning `CommonApiResDto<T>`
- **dto/**: Records only (NO `from()` methods)
- **exception/**: ExpectedException subclasses

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
- Note: `CommonApiResDto` is an exception (wrapper class, not a domain DTO)

**Services (Single Responsibility):**
- One method per interface: `{Action}{Entity}Service`
- Implementation: `{Interface}Impl`
- `@Transactional(readOnly = true)` for queries
- `@Transactional` for writes
- Manual DTO mapping in private methods

**Entities:**
- Extend `BaseEntity` (createdAt, updatedAt)
- `@Version` required (optimistic locking)
- `@Builder`, `@NoArgsConstructor(access = PROTECTED)`
- `FetchType.LAZY` for all relationships
- Business methods in Korean with Javadoc

**Controllers:**
- Always return `CommonApiResDto<T>`
- `@Tag`, `@Operation` for OpenAPI (Korean)
- `@Valid` for request validation
- Inject single-method services

**Repositories:**
- Extend `JpaRepository<Entity, ID>`
- Custom: `{Entity}RepositoryCustom` + QueryDSL impl

## Korean Language Requirement
**ALL documentation, comments, and messages in Korean:**
- Javadoc, inline comments
- Exception/validation messages
- Test `@DisplayName`
- API docs (`@Operation`, `@Tag`)
- Commit messages

## Git & Testing
- Branches: `master`, `develop`, `feat/`, `fix/`, `docs/`
- Commits (Korean): `add/update/fix/delete/docs/test/merge/init: {설명}`
- Testing: BDD with `@Nested`, Korean `@DisplayName`, Given-When-Then
- Commands: `/spotless-format`, `/split-commits`
