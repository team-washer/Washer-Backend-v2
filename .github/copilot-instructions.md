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
