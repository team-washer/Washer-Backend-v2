# 스펙: 사용자 권한(role) 변경 API

## 개요
인원관리 페이지(관리자 웹)에서 사용할 **사용자 권한 변경 API**를 구현한다.
관리자가 특정 사용자의 `UserRole`(USER / DORMITORY_COUNCIL / ADMIN)을 임의의 값으로 변경(승격·강등 통합)할 수 있다.

## 결정 사항 요약 (인터뷰 결과)
| # | 항목 | 결정 |
|---|------|------|
| Q1 | API 범위 | **범용 role 지정** — 요청에 목표 role 하나를 담아 설정. 승격/강등 단일 엔드포인트로 통합 |
| Q2 | 호출자 권한 | **ADMIN 전용** — 서비스에서 호출자 role을 별도 검증, DORMITORY_COUNCIL 호출 시 403 |
| Q3 | 안전 가드 | ① 자기 자신 변경 금지 ② 동일 role 변경 거부 ③ 마지막 ADMIN 보호(방어적 count 검증) |
| Q4 | 엔드포인트 | **`PATCH /api/v2/admin/users/{id}/role`**, 변경된 사용자 정보 담은 새 ResDto 반환 |
| Q5 | 감사 로깅 | **앱 로그**로 기록 (영문 key=value, 별도 테이블 없음) |

## 현행 구조 (참고)
- 역할: `team.washer.server.v2.domain.user.enums.UserRole` — `USER`, `DORMITORY_COUNCIL`, `ADMIN`
- URL 인가: `DomainAuthorizationConfig` — `/api/v2/admin/**` 는 `DORMITORY_COUNCIL` 또는 `ADMIN` 허용
- 호출자 식별: `CurrentUserProvider.getCurrentUserId()` → `Long`
- 사용자 엔티티: `User extends BaseEntity`, `role` 필드는 `@Enumerated(STRING)`, `@Builder.Default USER`
- 기존 관리자 사용자 API: `AdminUserController` (`/api/v2/admin/users`) — 조회/수정(호실·학년·층)/삭제. role은 미포함.

## API 명세
```
PATCH /api/v2/admin/users/{id}/role
```
- **인가(URL)**: 기존 `/api/v2/admin/**` 규칙(DORMITORY_COUNCIL·ADMIN)을 그대로 통과하되,
  서비스 계층에서 호출자가 ADMIN인지 추가 검증한다. (URL 규칙 변경 없음)
- **Path**: `id` — 대상 사용자 ID (Long, `@NotNull`)
- **Request Body**
  ```json
  { "role": "DORMITORY_COUNCIL" }
  ```
  - `role`: `UserRole` enum 이름 (USER / DORMITORY_COUNCIL / ADMIN), `@NotNull`
- **Response 200** (`UserRoleUpdateResDto`)
  ```json
  { "id": 12, "name": "홍길동", "studentId": "2405", "role": "DORMITORY_COUNCIL" }
  ```
  - SDK가 `CommonApiResponse`로 자동 래핑

### 오류 응답 (모두 `ExpectedException` 직접 throw)
| 상황 | HTTP | 메시지(한국어) |
|------|------|----------------|
| 호출자가 ADMIN이 아님 | 403 FORBIDDEN | `권한을 변경할 수 있는 관리자가 아닙니다.` |
| 대상 사용자 없음 | 404 NOT_FOUND | `사용자를 찾을 수 없습니다` |
| 자기 자신의 role 변경 시도 | 400 BAD_REQUEST | `자신의 권한은 변경할 수 없습니다.` |
| 대상이 이미 해당 role | 400 BAD_REQUEST | `이미 해당 권한을 가진 사용자입니다.` |
| 마지막 ADMIN 강등 시도 | 400 BAD_REQUEST | `마지막 관리자의 권한은 변경할 수 없습니다.` |
| `role` 누락/유효하지 않은 enum | 400 | 검증/역직렬화 오류 (GlobalExceptionHandler 처리) |

## 처리 로직 (ChangeUserRoleServiceImpl.execute)
1. `actorId = currentUserProvider.getCurrentUserId()`
2. 호출자 조회 → 없거나 role != ADMIN 이면 **403**
3. `actorId == targetId` 이면 **400** (자기 자신 변경 금지)
4. 대상 사용자 조회 → 없으면 **404**
5. 대상의 현재 role == newRole 이면 **400** (동일 role 거부)
6. **마지막 ADMIN 보호**: `대상 현재 role == ADMIN && newRole != ADMIN` 인 경우
   `userRepository.countByRole(ADMIN) <= 1` 이면 **400**
7. `user.changeRole(newRole)` (엔티티 비즈니스 메서드)
8. 저장
9. 감사 로그: `log.info("user role changed actorId={} targetId={} from={} to={}", actorId, targetId, oldRole, newRole)`
10. `UserRoleUpdateResDto` 매핑 후 반환

> 참고: 3(자기 변경 금지)이 있으면 6(마지막 ADMIN 보호)은 논리적으로 대부분 중복이나,
> 방어적 안전망으로 유지한다. count 쿼리 1회로 비용이 작다.

## 변경/생성 파일

### 신규
1. `domain/user/dto/request/UpdateUserRoleReqDto.java`
   ```java
   public record UpdateUserRoleReqDto(@NotNull(message = "권한은 필수입니다") UserRole role) {}
   ```
2. `domain/user/dto/response/UserRoleUpdateResDto.java`
   ```java
   public record UserRoleUpdateResDto(Long id, String name, String studentId, UserRole role) {}
   ```
   - 프로젝트 규칙: record만, `from()` 정적 팩토리 없음 → 매핑은 서비스에서 수동
3. `domain/user/service/ChangeUserRoleService.java` (인터페이스, 단일 메서드)
   ```java
   UserRoleUpdateResDto execute(Long targetUserId, UserRole newRole);
   ```
4. `domain/user/service/impl/ChangeUserRoleServiceImpl.java`
   - `@Service`, `@RequiredArgsConstructor`, `@Transactional`(쓰기)
   - 의존성: `UserRepository`, `CurrentUserProvider`
   - 위 "처리 로직" 구현

### 수정
5. `domain/user/entity/User.java` — 비즈니스 메서드 추가 (한국어 Javadoc)
   ```java
   /**
    * 사용자 권한을 변경합니다.
    *
    * @param newRole 새 권한
    */
   public void changeRole(final UserRole newRole) {
       this.role = newRole;
   }
   ```
6. `domain/user/repository/UserRepository.java` — 마지막 ADMIN 보호용 카운트
   ```java
   long countByRole(UserRole role);
   ```
7. `domain/user/controller/AdminUserController.java` — 엔드포인트 추가
   ```java
   @PatchMapping("/{id}/role")
   @Operation(summary = "사용자 권한 변경", description = "사용자의 권한(role)을 변경합니다. 관리자(ADMIN)만 호출할 수 있습니다.")
   public UserRoleUpdateResDto changeUserRole(
           @Parameter(description = "사용자 ID") @PathVariable @NotNull Long id,
           @Valid @RequestBody UpdateUserRoleReqDto request) {
       return changeUserRoleService.execute(id, request.role());
   }
   ```
   - `ChangeUserRoleService` 필드 주입 추가

## 테스트
`src/test/.../domain/user/service/ChangeUserRoleServiceTest.java` (BDD, `@Nested`, 한국어 `@DisplayName`, Given-When-Then)

시나리오:
- 성공: ADMIN이 USER를 DORMITORY_COUNCIL로 승격 → role 변경 + ResDto 반환
- 성공: ADMIN이 ADMIN(다른 사람)을 USER로 강등 (ADMIN이 2명 이상일 때)
- 실패: 호출자가 DORMITORY_COUNCIL → 403
- 실패: 호출자가 USER → 403 (URL 인가를 우회한 방어 케이스)
- 실패: 대상 사용자 없음 → 404
- 실패: 자기 자신 변경 → 400
- 실패: 동일 role로 변경 → 400
- 실패: 마지막 ADMIN 강등(ADMIN 1명) → 400
- 감사 로그 관련은 동작 검증만(선택)

## 컨벤션 체크리스트
- [ ] DTO는 record, `from()` 없음, 매핑은 서비스 수동
- [ ] 서비스 인터페이스+Impl, 단일 메서드, 쓰기 `@Transactional`
- [ ] `ExpectedException` 직접 throw (서브클래싱 없음)
- [ ] 예외/검증 메시지 한국어, 로그 메시지 영문 key=value
- [ ] 엔티티 비즈니스 메서드 한국어 Javadoc
- [ ] `@Tag`/`@Operation` 한국어
- [ ] `./gradlew spotlessApply`
- [ ] 커밋 메시지 한국어: `add: 사용자 권한 변경 API 구현`
```
