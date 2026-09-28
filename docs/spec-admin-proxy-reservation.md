# 구현 스펙: 관리자 대리 예약 생성 기능

## 1. 기능 요약

관리자(기숙사자치위원회 포함)가 관리자 웹에서 특정 사용자와 기기를 지정하여 그 사용자 명의로 예약을 생성하는 기능.

기존 예약 생성은 JWT 토큰에서 사용자를 해석한다(`CurrentUserProvider.getCurrentUserId()`). 대리 예약은 토큰의 사용자를 **예약 생성자(created_by)** 로만 기록하고, 예약의 주체(`user_id`)는 요청 바디의 `userId`로 지정한다.

---

## 2. 확정 요구사항

| 항목 | 결정 |
|------|------|
| 대상 지정 방식 | `userId` 직접 지정. 호실은 해당 User에서 파생 |
| 권한 | 기존 `/api/v2/admin/**` 규칙 그대로 (`DORMITORY_COUNCIL`, `ADMIN`) |
| 우회하는 검증 | 시간대 제한, 48h 호실 차단, 5분 쿨다운 |
| 유지하는 검증 | 층 제한, 호실 세탁금지(WashingBan), 기기 가용성, 기기 중복 예약, 1인 1예약, 호실 동일유형 중복 |
| 감사 추적 | `Reservation.createdBy` (`@ManyToOne` → User, nullable) |
| 타임아웃 자동 취소 시 패널티 | **면제** (대리 예약 한정) |
| 사용자 본인의 수동 취소 시 패널티 | **면제** (대리 예약 한정) |
| FCM 알림 | 발송하지 않음 |
| 응답 노출 | `AdminReservationResDto`에만 `createdByAdminName` 추가. 사용자용 `ReservationResDto`는 변경 없음 |
| 코드 구조 | 공통 `ReservationCreationSupport` 추출 후 일반/대리 두 서비스가 공유 |
| DB 마이그레이션 | 불필요 (`ddl-auto: update`, 신규 컬럼 nullable) |
| 테스트 | 신규 서비스 테스트 + 리팩터링 영향 경로 회귀 테스트 |

### 검증 체인 비교

| 검증 | 일반 예약 | 대리 예약 |
|------|:--------:|:--------:|
| 층 제한 `validateFloorRestriction()` | O | O |
| 호실 세탁금지 `WashingBan` | O | O |
| 48h 호실 차단 `isBlocked()` | O | **X** |
| 시간대 제한 `validateTimeRestriction()` | O | **X** |
| 5분 쿨다운 `isInCooldown()` | O | **X** |
| 기기 가용성 `AVAILABLE` | O | O |
| 기기 중복 예약 | O | O |
| 1인 1예약 | O | O |
| 호실 동일유형 중복 | O | O |

---

## 3. API 설계

```
POST /api/v2/admin/reservations
```

### Request

```json
{
  "userId": 12,
  "machineId": 3
}
```

### Response (성공, 201 아님 — 기존 관리자 API와 동일하게 200)

`AdminReservationResDto`를 반환하고 SDK가 자동 래핑한다.

```json
{
  "id": 101,
  "userId": 12,
  "userName": "김철수",
  "userRoomNumber": "301",
  "userStudentId": "2404",
  "machineId": 3,
  "machineName": "W-2F-L1",
  "machineAvailability": "RESERVED",
  "reservedAt": "2026-08-31T21:30:00",
  "startTime": null,
  "expectedCompletionTime": null,
  "actualCompletionTime": null,
  "status": "RESERVED",
  "cancelledAt": null,
  "createdByAdminName": "박관리"
}
```

### Error Cases

| 조건 | 상태 코드 | 메시지 |
|------|-----------|--------|
| 권한 없음 | 403 | (Security 기본 응답) |
| 대상 사용자 없음 | 404 | 사용자를 찾을 수 없습니다 |
| 기기 없음 | 404 | 기기를 찾을 수 없습니다 |
| 호실 정보 없음 | 400 | 호실 정보가 존재하지 않습니다. |
| 층 제한 위반 | (기존 `validateFloorRestriction()` 그대로) | |
| 호실 세탁 금지 | 403 | 해당 호실은 현재 세탁이 금지된 상태입니다. |
| 기기 사용 불가 | 400 | 해당 기기를 사용할 수 없습니다. 기기: {name} |
| 기기 중복 예약 | 409 | 해당 기기에 이미 진행 중인 예약이 있습니다. 기기: {name} |
| 대상 사용자 활성 예약 존재 | 400 | 이미 활성 예약이 존재합니다. 1인 1예약만 가능합니다. |
| 호실 동일유형 중복 | 400 | 해당 호실에 이미 {type} 예약이 존재합니다. ... |

> 조회 API는 신규 불필요. 관리자 웹은 기존 `GET /api/v2/admin/users`(이름·학번 부분 검색 + 페이지네이션)와 `GET /api/v2/admin/machines`를 사용한다.

---

## 4. 구현 대상 파일 목록

### 신규 생성

| 파일 | 역할 |
|------|------|
| `reservation/dto/request/AdminCreateReservationReqDto.java` | `userId`, `machineId` (둘 다 `@NotNull`) |
| `reservation/support/ReservationCreationSupport.java` | 두 경로가 공유하는 불변식 검증 + 기기 락 조회 + 엔티티 생성 |
| `reservation/service/AdminCreateReservationService.java` | 인터페이스 (`execute(AdminCreateReservationReqDto)`) |
| `reservation/service/impl/AdminCreateReservationServiceImpl.java` | 구현 |
| `src/test/.../reservation/service/AdminCreateReservationServiceTest.java` | 신규 테스트 |

### 수정

| 파일 | 변경 내용 |
|------|-----------|
| `reservation/entity/Reservation.java` | `createdBy` 필드 + `isProxyReservation()` 메서드 추가 |
| `reservation/controller/AdminReservationController.java` | `POST` 엔드포인트 + `@Operation` 추가 |
| `reservation/service/impl/CreateReservationServiceImpl.java` | 공통 로직을 Support 호출로 대체 |
| `reservation/service/impl/CancelReservationServiceImpl.java` | 대리 예약이면 패널티 스킵 |
| `reservation/service/impl/OverdueReservationProcessor.java` | 대리 예약이면 `applyTimeoutPenalty()` 스킵 |
| `reservation/dto/response/AdminReservationResDto.java` | `createdByAdminName` 필드 추가 |
| `reservation/service/impl/QueryAllReservationsServiceImpl.java` | 매핑에 `createdByAdminName` 반영 |
| `reservation/repository/custom/impl/ReservationRepositoryCustomImpl.java` | `findAllWithFilters`(112행)에 `leftJoin(reservation.createdBy).fetchJoin()` 추가 |

---

## 5. 상세 설계

### 5.1 Entity — `Reservation`

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "created_by_user_id", foreignKey = @ForeignKey(name = "fk_reservation_created_by"))
private User createdBy;

/**
 * 관리자 대리 생성 예약 여부를 반환합니다.
 * 대리 예약은 본인이 요청하지 않았으므로 취소·타임아웃 시 패널티를 부여하지 않습니다.
 *
 * @return 대리 예약 여부
 */
public boolean isProxyReservation() {
    return this.createdBy != null;
}
```

- nullable — 기존 행은 전부 `null`(본인 예약)로 안전하게 마이그레이션된다.
- `@Builder` 사용 시 일반 예약 경로는 `createdBy`를 세팅하지 않는다.
- 인덱스는 추가하지 않는다 (대리 예약 단독 조회 요구가 아직 없음).

### 5.2 Support — `ReservationCreationSupport`

두 경로가 공유하는 부분만 담는다. **정책 검증(시간대/차단/쿨다운)은 포함하지 않는다** — 각 서비스가 호출 전에 스스로 수행한다.

```
validateRoomConstraints(User user)      // 층 제한, roomNumber null 체크, WashingBan
lockMachine(Long machineId)             // findByIdForUpdate + 404
validateMachineAndReservations(User, Machine)
                                        // 가용성 → 기기 중복 → 1인 1예약 → 호실 동일유형 중복
create(User user, Machine machine, User createdBy)
                                        // 엔티티 생성 + machine.markAsReserved() + save
```

- `create()`의 `createdBy`는 일반 경로에서 `null`을 넘긴다.
- DTO 매핑은 Support에 두지 않는다 — 두 경로의 응답 타입이 다르기 때문(`ReservationResDto` vs `AdminReservationResDto`).
- 순서는 기존 `CreateReservationServiceImpl`과 동일하게 유지한다. 특히 **기기 비관적 락(`findByIdForUpdate`)이 중복 검증보다 먼저** 와야 동시 예약 직렬화가 유지된다.

### 5.3 `AdminCreateReservationServiceImpl`

```
@Transactional
1. targetUser = userRepository.findById(reqDto.userId())  → 404
2. adminUser  = userRepository.findById(currentUserProvider.getCurrentUserId()) → 404
3. support.validateRoomConstraints(targetUser)
   // 시간대 / 48h 차단 / 쿨다운 검증 없음
4. machine = support.lockMachine(reqDto.machineId())
5. support.validateMachineAndReservations(targetUser, machine)
6. saved = support.create(targetUser, machine, adminUser)
7. log.info("admin created proxy reservation reservationId={} targetUserId={} adminId={} machineId={}", ...)
8. AdminReservationResDto 매핑 (createdByAdminName = adminUser.getName())
```

- 관리자가 자기 자신을 대상으로 지정하는 것은 별도로 막지 않는다(불변식은 그대로 적용되므로 무해).
- 대상 사용자의 role은 검사하지 않는다.

### 5.4 `CreateReservationServiceImpl` 리팩터링 후

```
@Transactional
1. user = 토큰에서 해석 → 404
2. support.validateRoomConstraints(user)
3. 48h 차단 검증          ← 이 경로에만 남는다
4. 시간대 제한 검증        ← 이 경로에만 남는다
5. machine = support.lockMachine(...)
6. 쿨다운 검증            ← 이 경로에만 남는다 (machine.getType() 필요하므로 락 이후)
7. support.validateMachineAndReservations(user, machine)
8. saved = support.create(user, machine, null)
9. ReservationResDto 매핑 (기존과 동일)
```

외부 동작·에러 메시지·검증 순서는 리팩터링 전후로 완전히 동일해야 한다.

### 5.5 패널티 면제 지점

**`CancelReservationServiceImpl`** — 수동 취소:

```java
if (reservation.isReserved() && !reservation.isProxyReservation()) {
    // 기존 패널티 블록 (쿨다운 + 취소 기록 + 48h 블록 판정)
}
```

`applyPenalty` 플래그가 `false`로 남으므로 응답 메시지도 자동으로 "예약이 취소되었습니다."가 된다.

**`OverdueReservationProcessor.processOverdue()`** — 타임아웃 자동 취소:

```java
reservation.cancel();
machine.markAsAvailable();
// ... save

if (!reservation.isProxyReservation()) {
    applyTimeoutPenalty(reservation.getUser(), machine);
}
return OverdueResult.CANCELLED;
```

- 취소 자체와 기기 반납(`markAsAvailable`)은 대리 예약도 동일하게 수행된다.
- 자동 시작(`AUTO_STARTED`) 분기는 변경 없음 — 대리 예약도 기기가 돌기 시작하면 정상적으로 `RUNNING`이 되고 완료 알림까지 기존대로 흐른다.

### 5.6 조회 경로

`ReservationRepositoryCustomImpl.findAllWithFilters`에 `leftJoin(reservation.createdBy, ...).fetchJoin()`을 추가한다. QueryDSL `User` Q타입 별칭이 이미 `user`로 쓰이고 있으므로 `createdByUser` 등 별도 별칭을 선언해야 한다.

`QueryAllReservationsServiceImpl:45` 매핑에서:

```java
reservation.getCreatedBy() != null ? reservation.getCreatedBy().getName() : null
```

---

## 6. 테스트 계획

### 신규 — `AdminCreateReservationServiceTest`

| `@Nested` | `@DisplayName` |
|-----------|----------------|
| 성공 | 관리자가 지정한 사용자 명의로 예약이 생성된다 |
| 성공 | 생성된 예약의 createdBy에 관리자가 기록된다 |
| 정책 우회 | 시간 제한 시간대에도 대리 예약이 생성된다 |
| 정책 우회 | 48시간 차단된 호실에도 대리 예약이 생성된다 |
| 정책 우회 | 쿨다운 중인 사용자에게도 대리 예약이 생성된다 |
| 실패 | 존재하지 않는 사용자면 404 |
| 실패 | 존재하지 않는 기기면 404 |
| 실패 | 세탁 금지 호실이면 403 |
| 실패 | 기기가 사용 불가 상태면 400 |
| 실패 | 기기에 이미 활성 예약이 있으면 409 |
| 실패 | 대상 사용자에게 이미 활성 예약이 있으면 400 |
| 실패 | 호실에 동일 유형 활성 예약이 있으면 400 |

### 회귀

| 대상 | 확인 내용 |
|------|-----------|
| `CreateReservationServiceTest` | Support 추출 후에도 기존 검증 순서·에러 메시지 동일 |
| `CancelReservationServiceTest` | 본인 예약 취소 시 패널티 유지 / 대리 예약 취소 시 패널티 미적용 |
| 타임아웃 처리 테스트 | 대리 예약 만료 시 `applyTimeoutPenalty` 미호출, 취소·기기 반납은 수행 |

---

## 7. 알려진 트레이드오프

- **패널티 우회 경로**: 사용자가 관리자에게 대리 예약을 부탁하면 패널티 없는 취소권을 얻는다. 관리자 개입이 필요하고 `created_by_user_id`에 흔적이 남으므로 감수한다.
- **사용자 미인지**: 알림을 보내지 않으므로 대상 사용자는 대리 예약을 모를 수 있다. 타임아웃 시 자동 취소되고 패널티도 없으므로 사용자 피해는 없으나, 그동안 기기가 묶인다. 관리자가 현장에서 구두 전달하는 운영을 전제로 한다.
- **사용자 앱 미노출**: `ReservationResDto`를 바꾸지 않으므로 앱은 대리 예약을 일반 예약과 동일하게 표시한다. 추후 필요해지면 `isProxyReservation` 필드만 추가하면 된다.
