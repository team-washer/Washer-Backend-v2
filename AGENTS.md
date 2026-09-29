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

## GitHub Issue 및 PR 규칙
- 새 Issue 제목에는 `[P0]`~`[P4]` 우선순위 접두사를 넣지 않고 GitHub 위험도 라벨로 표시합니다.
- 한 Issue에는 위험도 라벨을 정확히 하나만 적용합니다. `버그`, `리팩터링`, `신규 기능`, `문서화`, `삭제` 같은 작업 유형 라벨은 위험도와 다른 축이므로 함께 유지할 수 있습니다.
- 위험도 기준은 다음과 같습니다.
  - `위험도: 긴급`: 인증·권한 우회, 토큰·비밀정보 노출, 데이터 손실 또는 즉시 안전 대응이 필요한 문제입니다. 예: 비인증 사용자가 관리자 기기 동기화를 실행하거나 인증 토큰이 로그에 기록되는 문제입니다.
  - `위험도: 높음`: 예약·기기 제어 같은 핵심 흐름의 장애, 잘못된 상태 전이, 데이터 정합성·동시성 문제로 빠른 대응이 필요한 문제입니다. 예: 신규 예약이 진행 중인 기기의 전원을 끄거나 동일 사용자의 중복 예약을 허용하는 문제입니다.
  - `위험도: 보통`: 영향 범위가 제한된 기능 오류, API 계약·사용자 안내 문제 또는 운영 정책 개선입니다. 예: 특정 입력 오류에서 수정할 필드를 안내하지 않는 문제입니다.
  - `위험도: 낮음`: 기능은 유지되지만 성능, 관측성 또는 유지보수성을 개선하는 작업입니다. 예: 조회를 일괄 처리해 N+1을 줄이거나 비동기 로그의 추적 정보를 보강하는 작업입니다.
  - `위험도: 정리`: 동작 영향이 낮은 미사용 코드·설정·의존성·중복을 제거하는 작업입니다. 예: 호출되지 않는 Repository 메서드를 삭제하는 작업입니다.
- PR 작성 도구의 라벨 선택은 코드 수정 성격에 맞는 PR 라벨만 대상으로 합니다. `위험도: 긴급` 등 위험도 라벨은 Issue 전용이므로 PR에 자동으로 붙이지 않습니다.

## Special Rules Summary
1. Domain DTOs: records only, NO `from()` methods
2. Services: single method, interface + Impl
3. Entities: extend `BaseEntity` (provides `@Version`); no per-entity `@Version` unless `BaseEntity` is not extended
4. Controllers: return response DTOs directly (SDK auto-wraps); `CommonApiResponse` only when there is no body
5. Exceptions: throw SDK `ExpectedException` directly, do not subclass
6. Manual DTO mapping (no MapStruct)
7. All docs/comments in Korean (logs in English)
