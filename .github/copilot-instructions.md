# GitHub Copilot Instructions - Washer Backend v2

## Project Overview
Korean dormitory laundry management system with Java 25 and Spring Boot 4.0.1.

## Technology Stack
- Java 25 (preview features enabled)
- Spring Boot 4.0.1
- MySQL + Spring Data JPA + QueryDSL
- OpenFeign, OpenAPI/Swagger
- JUnit 5, Mockito

## Code Formatting (Spotless)
- **Line length:** 120 chars max
- **Indentation:** 4 spaces (no tabs)
- **Command:** `./gradlew spotlessApply`
- Use `final`, `var`, method references (`User::getName`)

## Architecture: Layered DDD
- **Domain:** Entities extending `BaseEntity`, include `@Version` for optimistic locking
- **Service:** Interface + Impl pattern, single method per service
- **Repository:** JpaRepository + QueryDSL for complex queries
- **Controller:** Return `CommonApiResDto<T>` wrapper
- **Domain DTO:** Records only (no classes, no `from()` methods)

## Naming Conventions
- **Entities:** `Reservation`, `User` - extend `BaseEntity`, use `@Builder`
- **DTOs:** Records with `ReqDto`/`ResDto` suffix - NO `from()` methods
- **Services:** `{Action}{Entity}Service` interface, `{Interface}Impl` implementation
- **Controllers:** `{Entity}Controller` - always return `CommonApiResDto<T>`

## Critical Rules
1. **Domain DTOs:** Records only - NO `from()` methods, manual mapping in service
2. **Services:** Single method, interface + Impl, `@Transactional(readOnly=true)` for queries
3. **Controllers:** Always return `CommonApiResDto<T>`
4. **Entities:** Extend `BaseEntity`, include `@Version`
5. **Validation:** Jakarta annotations on DTOs
6. **Manual mapping:** No MapStruct
7. **QueryDSL:** For complex queries with joins

## Korean Language Requirement
**ALL documentation, comments, and messages MUST be in Korean:**
- Javadoc comments: Korean
- Inline comments: Korean
- Exception messages: Korean
- Validation messages: Korean
- Test `@DisplayName`: Korean

## Git Commit Format (Korean)
- `add: 새로운 기능 추가`
- `update: 기존 기능 수정`
- `fix: 버그 수정`
- `delete: 코드/파일 삭제`
- `docs: 문서 작성/수정`
- `test: 테스트 코드 작성/수정`
- `merge: 브랜치 병합`
- `init: 초기 설정`

## GitHub Issue 및 PR 규칙
- 새 Issue 제목에는 `[P0]`~`[P4]` 우선순위 접두사를 넣지 않고 위험도 라벨을 하나만 적용합니다.
- `버그`, `리팩터링`, `신규 기능`, `문서화`, `삭제` 같은 작업 유형 라벨은 위험도와 다른 축이므로 함께 적용할 수 있습니다.
- 위험도 라벨은 `위험도: 긴급`(인증·권한·비밀정보·데이터 손실·즉시 안전 대응), `위험도: 높음`(핵심 흐름 장애·상태 정합성·동시성), `위험도: 보통`(제한된 기능 오류·API 계약·운영 정책), `위험도: 낮음`(성능·관측성·유지보수), `위험도: 정리`(미사용 코드·설정·중복 제거)로 구분합니다.
- 위험도 라벨은 Issue 전용입니다. PR 작성 도구는 코드 수정 성격에 맞는 PR 라벨만 자동 선택합니다.
