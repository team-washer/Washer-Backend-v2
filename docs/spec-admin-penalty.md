# 구현 스펙: 관리자 세탁 패널티 부과 기능

## 1. 기능 요약

관리자 또는 기숙사자치위원회가 특정 사용자를 지목해 **48시간 세탁 패널티(호실 예약 차단)** 를 직접 부과하는 기능.

기존 패널티 정책은 두 가지였다.

1. 예약 직접 취소 또는 예약 기간 만료 시 → 해당 기기 유형에 **5분 쿨다운**
2. 48시간 이내 5분 패널티가 5회 누적되면 → 해당 호실에 **48시간 예약 차단**

여기에 세 번째 정책을 추가한다.

3. **관리자·자치위가 패널티를 부과하면 5분 패널티 5회 누적과 동일하게 취급 → 즉시 48시간 호실 차단**

정책 문서상으로는 "5분 5회 누적"으로 설명하지만, 구현은 취소 이력을 조작하지 않고 **48시간 차단을 직접 부여**한다(§7-1 참고).

---

## 2. 확정 요구사항

| 항목 | 결정 |
|------|------|
| 구현 모델 | 직접 부여형 — 취소 이력 ZSet을 건드리지 않고 `applyBlock()` 직접 호출 |
| 차단 단위 | **호실(roomNumber)** 단위 — 기존 48시간 차단과 동일 |
| 부과 권한 | **자치위(DORMITORY_COUNCIL) + 관리자(ADMIN)** — 서비스 내 `isAdmin()` 검사 없음 |
| 해제·연장 권한 | 기존 그대로 **ADMIN만** (비대칭 유지, 변경 없음) |
| 이미 차단 중일 때 | TTL을 **48시간으로 리셋**(덮어쓰기), 거부하지 않음 |
| 부과 사유(reason) | **필수** 입력 |
| 사유 저장 위치 | 별도 엔티티 없음 — 알림 메시지(DB) + 서버 로그 |
| 진행 중 예약 | **건드리지 않음** — 신규 예약 생성만 차단됨 |
| 대상 가드 | **자기 자신 지목 금지** (호실 단위 가드는 두지 않음) |
| 5분 쿨다운 | 함께 적용하지 **않음** |
| 알림 | **신규 타입** `ADMIN_PENALTY_BLOCKED` 추가, 차단 중이어도 **항상 발송** |
| Redis 실패 시 | 부과 후 `isBlocked()`로 **검증**, 실패하면 예외 전파(알림 미발송) |

---

## 3. API 설계

```
POST /api/v2/admin/reservations/users/{userId}/penalty
```

기존 해제 API(`DELETE /api/v2/admin/reservations/users/{userId}/penalty`)와 동일 URI에서 부과/해제가 짝을 이룬다.

### Request

```json
{
  "reason": "세탁물 장기 방치로 기기 점유"
}
```

### Response (성공)

SDK가 자동 래핑 → 바디 없음, `CommonApiResponse.success("세탁 패널티가 부과되었습니다.")` 반환

### Error Cases

| 조건 | 상태 코드 | 메시지 |
|------|-----------|--------|
| 일반 사용자(USER) 접근 | 403 | Spring Security가 차단 (`/api/v2/admin/**`) |
| 자기 자신에게 부과 | 400 | 자신에게는 패널티를 부과할 수 없습니다. |
| 사유 누락·공백 | 400 | 부과 사유는 필수입니다 |
| 사유 200자 초과 | 400 | 부과 사유는 200자를 초과할 수 없습니다 |
| actor userId 없음 | 404 | 사용자를 찾을 수 없습니다. |
| 대상 userId 없음 | 404 | 사용자를 찾을 수 없습니다. |
| 대상 호실 정보 없음 | 404 | 호실 정보를 찾을 수 없습니다. |
| Redis 차단 부여 실패 | 500 | 패널티 부과에 실패했습니다. 잠시 후 다시 시도해 주세요. |

### 권한 처리 방식

`DomainAuthorizationConfig:43`이 이미 `/api/v2/admin/**`를 `DORMITORY_COUNCIL`, `ADMIN`에 허용한다.
따라서 **서비스에 `isAdmin()` 검사를 넣지 않는 것만으로** 자치위 부과가 가능해진다.
`ClearUserPenaltyServiceImpl:32`, `ExtendCancellationBlockServiceImpl:36`의 `isAdmin()` 검사는 **그대로 둔다**(해제·연장은 ADMIN 전용 유지).

---

## 4. 구현 대상 파일 목록

### 신규 생성

| 파일 | 역할 |
|------|------|
| `domain/reservation/dto/request/ApplyUserPenaltyReqDto.java` | 요청 DTO (reason 필드) |
| `domain/reservation/service/ApplyUserPenaltyService.java` | 서비스 인터페이스 |
| `domain/reservation/service/impl/ApplyUserPenaltyServiceImpl.java` | 서비스 구현체 |

### 수정

| 파일 | 변경 내용 |
|------|-----------|
| `domain/notification/enums/NotificationType.java` | `ADMIN_PENALTY_BLOCKED` 상수 추가 |
| `domain/notification/entity/Notification.java` | `createAdminPenaltyNotification()` 정적 팩토리 추가 |
| `domain/notification/support/ReservationNotificationSupport.java` | `sendAdminPenalty()` 추가 |
| `domain/reservation/controller/AdminReservationController.java` | POST 엔드포인트 추가 |

### 변경하지 않는 파일

`PenaltyRedisUtil`은 **수정하지 않는다**. 기존 `applyBlock()` / `isBlocked()` 조합으로 충분하며, 자동 판정 경로가 공유하는 유틸에 예외 전파 로직을 넣으면 스케줄러 동작에 영향을 준다.

---

## 5. 상세 구현

### 5-1. ApplyUserPenaltyReqDto

```java
package team.washer.server.v2.domain.reservation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "세탁 패널티 부과 요청 DTO")
public record ApplyUserPenaltyReqDto(
        @NotBlank(message = "부과 사유는 필수입니다") @Size(max = 200, message = "부과 사유는 200자를 초과할 수 없습니다") @Schema(description = "패널티 부과 사유", example = "세탁물 장기 방치로 기기 점유") String reason) {
}
```

`@Size(max = 200)`은 `CreateMalfunctionReportReqDto:11` 선례를 따른다.
`Notification.message` 컬럼이 500자 제한(`Notification.java:42`)이고 템플릿 고정부가 약 40자이므로 안전하다.

### 5-2. ApplyUserPenaltyService

```java
package team.washer.server.v2.domain.reservation.service;

public interface ApplyUserPenaltyService {

    /**
     * 대상 사용자의 호실에 48시간 세탁 패널티를 부과합니다.
     *
     * @param userId
     *            패널티 부과 대상 사용자 ID
     * @param reason
     *            부과 사유
     */
    void execute(Long userId, String reason);
}
```

### 5-3. ApplyUserPenaltyServiceImpl

```java
package team.washer.server.v2.domain.reservation.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.notification.support.ReservationNotificationSupport;
import team.washer.server.v2.domain.reservation.service.ApplyUserPenaltyService;
import team.washer.server.v2.domain.reservation.util.PenaltyRedisUtil;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.global.security.provider.CurrentUserProvider;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplyUserPenaltyServiceImpl implements ApplyUserPenaltyService {

    private final UserRepository userRepository;
    private final PenaltyRedisUtil penaltyRedisUtil;
    private final ReservationNotificationSupport reservationNotificationSupport;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional
    public void execute(final Long userId, final String reason) {
        final var actorId = currentUserProvider.getCurrentUserId();

        // 권한 검사는 SecurityConfig가 담당한다(DORMITORY_COUNCIL, ADMIN 허용).
        // 자치위·관리자 구분은 감사 로그에만 남기므로 actor는 조회만 한다.
        final User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new ExpectedException("사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));

        if (actorId.equals(userId)) {
            throw new ExpectedException("자신에게는 패널티를 부과할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }

        final User target = userRepository.findById(userId)
                .orElseThrow(() -> new ExpectedException("사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));

        final String roomNumber = target.getRoomNumber();
        if (roomNumber == null) {
            throw new ExpectedException("호실 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }

        // applyBlock은 예외를 삼키므로 부과 성공 여부를 직접 검증한다.
        // 관리자에게 거짓 성공 응답이 나가면 제재가 집행되지 않은 채 종료된다.
        penaltyRedisUtil.applyBlock(roomNumber);
        if (!penaltyRedisUtil.isBlocked(roomNumber)) {
            log.error("failed to apply admin penalty roomNumber={} targetId={} actorId={}",
                    roomNumber,
                    userId,
                    actorId);
            throw new ExpectedException("패널티 부과에 실패했습니다. 잠시 후 다시 시도해 주세요.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }

        log.info("admin penalty applied roomNumber={} targetId={} actorId={} actorRole={} reason={}",
                roomNumber,
                userId,
                actorId,
                actor.getRole(),
                reason);

        reservationNotificationSupport.sendAdminPenalty(target, reason);
    }
}
```

**설계 노트**

- `isAdmin()` 검사를 넣지 않는 것이 자치위 허용의 핵심이다. 실수로 추가하지 말 것.
- `applyBlock()` → `isBlocked()` 순서로 검증한다. 검증 실패 시 알림이 발송되지 않는다.
- Redis는 트랜잭션 롤백 대상이 아니므로, 알림 저장이 실패해도 차단은 남는다(제재가 남는 쪽이 안전한 실패 방향).
- 로그가 부과자 추적의 유일한 수단이므로 `actorId`, `actorRole`, `reason`을 모두 남긴다.

### 5-4. NotificationType 상수 추가

`NotificationType.java`의 `FORCE_STOPPED` 뒤에 추가한다.

```java
ADMIN_PENALTY_BLOCKED("예약 차단 알림", "관리자에 의해 해당 호실의 예약이 48시간 동안 제한됩니다.\n\n사유: {reason}")
```

> **주의**: `formatMessage(String reason)` 오버로드는 **추가할 수 없다.**
> 이미 `formatMessage(String machineName)`이 존재해 시그니처가 충돌한다(`NotificationType.java:39`).
> 대신 팩토리에서 `getMessageTemplate().replace("{reason}", reason)`를 직접 호출한다 —
> `createWarningNotification`(`Notification.java:158`)이 같은 방식을 쓴다.

### 5-5. Notification 정적 팩토리

```java
/**
 * 관리자 패널티 부과 알림을 생성합니다.
 *
 * @param user
 *            알림 수신 사용자
 * @param reason
 *            패널티 부과 사유
 * @return 생성된 관리자 패널티 알림
 */
public static Notification createAdminPenaltyNotification(User user, String reason) {
    String message = NotificationType.ADMIN_PENALTY_BLOCKED.getMessageTemplate().replace("{reason}", reason);

    return Notification.builder().user(user).type(NotificationType.ADMIN_PENALTY_BLOCKED).message(message)
            .isRead(false).build();
}
```

`machine`을 설정하지 않는다. `createBlockExtensionNotification`(`Notification.java:227`)이 동일한 선례다.

### 5-6. ReservationNotificationSupport

```java
/**
 * 관리자 패널티 부과 알림을 전송한다.
 */
@Transactional
public void sendAdminPenalty(User user, String reason) {
    var notification = Notification.createAdminPenaltyNotification(user, reason);
    persistAndSend(user, notification, "예약 차단 알림");
}
```

### 5-7. AdminReservationController

필드 추가:

```java
private final ApplyUserPenaltyService applyUserPenaltyService;
```

엔드포인트 추가 (기존 `clearUserPenalty` 바로 위에 배치):

```java
@PostMapping("/users/{userId}/penalty")
@Operation(summary = "사용자 세탁 패널티 부과", description = "특정 사용자의 호실에 48시간 예약 차단을 부과합니다. 5분 패널티 5회 누적과 동일한 제재이며, 관리자와 기숙사자치위원회가 사용할 수 있습니다. 이미 차단 중인 호실은 차단 기간이 48시간으로 갱신됩니다.")
public CommonApiResponse applyUserPenalty(@Parameter(description = "사용자 ID") @PathVariable @NotNull Long userId,
        @Valid @RequestBody ApplyUserPenaltyReqDto reqDto) {

    applyUserPenaltyService.execute(userId, reqDto.reason());
    return CommonApiResponse.success("세탁 패널티가 부과되었습니다.");
}
```

---

## 6. 기존 기능과의 연동

부과 결과가 기존 조회·해제 경로에 **자동 반영**된다. 추가 작업이 없다.

| 경로 | 동작 |
|------|------|
| `CreateReservationServiceImpl:44` | `isBlocked(roomNumber)` → 신규 예약 차단됨 |
| `QueryReservationAvailabilityServiceImpl:38` | 예약 가능 여부에 반영됨 |
| `PenaltyStatusResDto.isRoomBlocked` / `blockExpiresAt` | 차단 상태·만료 시각 노출됨 |
| `ClearUserPenaltyService` (ADMIN) | `clearAllRestrictions()`가 호실 차단까지 해제 |
| `ExtendCancellationBlockService` (ADMIN) | 부과된 차단을 일 단위로 연장 가능 |

`PenaltyStatusResDto`에는 **"관리자 부과인지 자동 누적인지" 구분이 나타나지 않는다.** Redis 차단 엔티티가 사유를 담지 않기 때문이며(결정 6), 사유는 사용자 알림 목록에서 확인한다.

---

## 7. 검토 후 기각한 대안

### 7-1. 취소 이력 ZSet에 5건 주입 (이력 주입형)

"5분 5회 누적으로 판단"을 문자 그대로 구현하는 방식. **기각.**
48시간 차단이 만료돼도 ZSet 이력 5건이 남아, 사용자가 이후 정상적으로 **한 번만 취소해도 즉시 재차단**되는 숨은 함정이 생긴다. 관리자 재량 제재와 자연 누적 이력이 뒤섞여 추적도 흐려진다.

### 7-2. 유저 단위 신규 차단 엔티티

지목된 사용자만 정확히 차단. **기각.**
"48시간 세탁 패널티"라는 동일한 이름의 서로 다른 두 메커니즘이 생기고, 조회·해제·연장 세 API가 전부 분기를 안게 된다. 기존 차단이 호실 단위인 이유(룸메이트 간 번갈아 예약하는 우회 방지)가 관리자 부과에도 동일하게 적용된다.

### 7-3. 해제 권한도 자치위까지 완화

자치위 오조작을 스스로 수습하게 하는 방식. **기각.**
제재 부과는 넓게, 해제는 좁게 두는 것이 정상적인 권한 설계다. 또한 `ClearUserPenaltyServiceImpl` 완화는 **자연 누적 패널티 해제 권한까지 동시에 여는** 범위 초과 변경이다. 자치위 오조작은 관리자 요청이라는 운영 절차로 흡수한다.

### 7-4. 진행 중 예약 연쇄 취소

**기각.** `RUNNING` 상태를 취소하면 젖은 세탁물이 기기에 갇힌 채 예약만 사라진다. 그런 개입에는 이미 `ForceStopMachineService`와 `AdminCancelReservationService`가 있으므로 관리자가 판단해서 별도로 사용한다.

### 7-5. 자기 호실(roomNumber) 부과 금지

**기각.** 자기 호실에 부과하면 본인도 48시간 세탁을 못 하므로 남용 유인이 없고(자해), 오히려 룸메이트의 실제 위반을 가장 잘 아는 사람이 조치하지 못하게 된다. 자기 자신 지목만 오조작으로 보고 차단한다.

### 7-6. 감사 전용 JPA 엔티티(`AdminPenalty`)

**기각.** `ClearUserPenaltyServiceImpl:38`, `ExtendCancellationBlockServiceImpl:51` 모두 관리자 행위를 로그로만 남기는 기존 관례를 따른다. 사유는 `Notification.message`에 DB로 영구 보존되므로 실질 추적이 확보된다.
**단, 부과자 신원(actorId·actorRole)은 애플리케이션 로그에만 남는다.** 로그 보존·검색 환경이 부실하다면 이 결정을 재검토할 것.

---

## 8. 테스트 계획

`ApplyUserPenaltyServiceTest` 신규 작성. 기존 `ClearUserPenaltyServiceTest` 구조(BDD + `@Nested` + 한국어 `@DisplayName`)를 따른다.

### 성공 케이스

- 관리자가 부과하면 호실 차단이 적용되고 알림이 발송된다
- **자치위원이 부과해도 정상 동작한다** (핵심 회귀 방지 — `isAdmin()` 검사가 실수로 추가되면 여기서 실패)

### 실패 케이스

- 자기 자신에게 부과하면 400
- 존재하지 않는 부과자면 404
- 존재하지 않는 대상 사용자면 404
- 대상의 호실 정보가 없으면 404
- `applyBlock` 후 `isBlocked`가 false면 500이고 **알림이 발송되지 않는다**

> **"이미 차단 중인 호실도 알림이 재발송된다"는 단위 테스트로 작성하지 않았다.**
> 서비스가 사전 차단 여부(`wasBlocked`)를 **조회하지 않으므로** 차단 중이든 아니든 실행 경로가 완전히 동일하다.
> 즉 이 동작은 "알림을 억제하는 분기가 코드에 없다"는 사실 자체로 보장되며, 목으로 구분할 상태가 존재하지 않는다.

### 검증 테스트

`ApplyUserPenaltyReqDtoTest` — `FcmTokenReqDtoTest` 선례를 따라 작성.

- 사유 200자는 허용된다
- 사유 200자 초과 시 검증 실패
- 사유가 공백이면 검증 실패
- 사유가 null이면 검증 실패

---

## 9. 작업 순서

1. `NotificationType.ADMIN_PENALTY_BLOCKED` 추가
2. `Notification.createAdminPenaltyNotification()` 추가
3. `ReservationNotificationSupport.sendAdminPenalty()` 추가
4. `ApplyUserPenaltyReqDto` 생성
5. `ApplyUserPenaltyService` / `ApplyUserPenaltyServiceImpl` 생성
6. `AdminReservationController`에 엔드포인트 추가
7. `ApplyUserPenaltyServiceTest` 작성
8. `./gradlew spotlessApply` 실행 후 테스트

DB 스키마 변경이 없으므로 마이그레이션 작업은 없다.
