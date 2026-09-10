# 구현 스펙: 예약 차단 기간 연장 기능

## 1. 기능 요약

관리자가 48시간 취소 차단 중인 호실의 차단 기간을 일(day) 단위로 추가 연장하는 기능.  
연장 시 사용자에게 새 만료 시각을 포함한 알림을 발송한다.

---

## 2. 확정 요구사항

| 항목 | 결정 |
|------|------|
| 연장 주체 | 관리자(Admin)만 |
| 연장 단위 | 일(day) 단위 (관리자가 숫자 직접 입력) |
| 연장 방식 | 현재 남은 TTL에 누적 추가 |
| 최대 연장 일수 | 제한 없음 |
| 활성 차단 없을 때 | 400 Bad Request |
| API 파라미터 | userId 기준 (해제 API와 동일 패턴) |
| 알림 | 연장 시 사용자에게 새 만료 시각 포함 발송 |
| 알림 타입 | 연장 전용 `CANCELLATION_BLOCK_EXTENDED` 신규 추가 |

---

## 3. API 설계

```
PATCH /api/v2/admin/reservations/users/{userId}/penalty/block
```

### Request

```json
{
  "days": 2
}
```

### Response (성공)

SDK가 자동 래핑 → 바디 없음, `CommonApiResponse.success(...)` 반환

### Error Cases

| 조건 | 상태 코드 | 메시지 |
|------|-----------|--------|
| 관리자 아님 | 403 | 관리자 권한이 필요합니다 |
| userId 없음 | 404 | 사용자를 찾을 수 없습니다 |
| 호실 정보 없음 | 404 | 호실 정보를 찾을 수 없습니다 |
| 활성 차단 없음 | 400 | 활성화된 예약 차단이 없습니다 |

---

## 4. 구현 대상 파일 목록

### 신규 생성

| 파일 | 역할 |
|------|------|
| `dto/request/ExtendBlockReqDto.java` | 요청 DTO (days 필드) |
| `service/ExtendCancellationBlockService.java` | 서비스 인터페이스 |
| `service/impl/ExtendCancellationBlockServiceImpl.java` | 서비스 구현체 |

### 수정

| 파일 | 변경 내용 |
|------|-----------|
| `util/PenaltyRedisUtil.java` | `extendBlock()` 메서드 추가 |
| `entity/Notification.java` | `createBlockExtensionNotification()` 정적 팩토리 메서드 추가 |
| `support/ReservationNotificationSupport.java` | `sendBlockExtension()` 메서드 추가 |
| `controller/AdminReservationController.java` | PATCH 엔드포인트 추가 |

---

## 5. 상세 구현

### 5-1. ExtendBlockReqDto

```java
package team.washer.server.v2.domain.reservation.dto.request;

import jakarta.validation.constraints.Min;

public record ExtendBlockReqDto(
    @Min(value = 1, message = "연장 일수는 1일 이상이어야 합니다")
    int days
) {}
```

### 5-2. ExtendCancellationBlockService

```java
package team.washer.server.v2.domain.reservation.service;

public interface ExtendCancellationBlockService {
    void execute(Long userId, int days);
}
```

### 5-3. ExtendCancellationBlockServiceImpl

흐름:
1. 현재 사용자(admin) 조회 → 관리자 권한 검증
2. 대상 userId로 User 조회 → roomNumber 획득
3. `penaltyRedisUtil.extendBlock(roomNumber, days)` 호출 → 새 만료 시각 반환
   - 활성 차단 없으면 내부에서 `ExpectedException(400)` 발생
4. 대상 User 조회 → `reservationNotificationSupport.sendBlockExtension(user, newExpiryAt)` 호출

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class ExtendCancellationBlockServiceImpl implements ExtendCancellationBlockService {

    private final UserRepository userRepository;
    private final PenaltyRedisUtil penaltyRedisUtil;
    private final ReservationNotificationSupport reservationNotificationSupport;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional(readOnly = true)
    public void execute(final Long userId, final int days) {
        final var adminId = currentUserProvider.getCurrentUserId();
        final User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ExpectedException("사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        if (!admin.getRole().isAdmin()) {
            log.warn("Unauthorized block extend attempt by user {} for user {}", adminId, userId);
            throw new ExpectedException("관리자 권한이 필요합니다.", HttpStatus.FORBIDDEN);
        }

        final User target = userRepository.findById(userId)
                .orElseThrow(() -> new ExpectedException("사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        final String roomNumber = target.getRoomNumber();
        if (roomNumber == null) {
            throw new ExpectedException("호실 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }

        final LocalDateTime newExpiryAt = penaltyRedisUtil.extendBlock(roomNumber, days);
        log.info("Block extended roomNumber={} additionalDays={} newExpiry={} by admin {}", roomNumber, days, newExpiryAt, adminId);

        reservationNotificationSupport.sendBlockExtension(target, newExpiryAt);
    }
}
```

> `@Transactional(readOnly = true)` — DB 쓰기 없음(Redis만 변경), User 조회만 수행

### 5-4. PenaltyRedisUtil.extendBlock()

```java
/**
 * 호실 단위 예약 차단 기간을 지정한 일수만큼 연장합니다.
 *
 * @param roomNumber    연장 대상 호실 번호
 * @param additionalDays 추가 연장 일수 (1 이상)
 * @return 연장 후 만료 시각
 * @throws ExpectedException 활성 차단이 없는 경우
 */
public LocalDateTime extendBlock(final String roomNumber, final long additionalDays) {
    final CancellationBlockEntity existing = cancellationBlockRedisRepository.findById(roomNumber)
            .orElseThrow(() -> new ExpectedException("활성화된 예약 차단이 없습니다.", HttpStatus.BAD_REQUEST));

    final long currentTtl = existing.getTtl() != null ? existing.getTtl() : 0L;
    final long additionalSeconds = additionalDays * 24L * 3600L;
    final long newTtl = currentTtl + additionalSeconds;

    cancellationBlockRedisRepository.save(
            CancellationBlockEntity.builder().roomNumber(roomNumber).ttl(newTtl).build());
    log.info("block extended roomNumber={} additionalDays={} newTtlSeconds={}", roomNumber, additionalDays, newTtl);

    return LocalDateTime.now().plusSeconds(newTtl);
}
```

### 5-5. Notification.createBlockExtensionNotification()

```java
/**
 * 예약 차단 연장 알림을 생성합니다.
 *
 * @param user       알림 수신 사용자
 * @param newExpiryAt 새 차단 만료 시각
 * @return 생성된 차단 연장 알림
 */
public static Notification createBlockExtensionNotification(User user, LocalDateTime newExpiryAt) {
    String message = NotificationType.CANCELLATION_BLOCK_EXTENDED.formatMessage(newExpiryAt);

    return Notification.builder()
            .user(user)
            .type(NotificationType.CANCELLATION_BLOCK_EXTENDED)
            .message(message)
            .isRead(false)
            .build();
}
```

> `machine` 필드 없음 — 연장은 특정 기기와 무관한 호실 단위 차단이므로 null

### 5-6. ReservationNotificationSupport.sendBlockExtension()

```java
/**
 * 예약 차단 연장 알림을 전송한다.
 */
@Transactional
public void sendBlockExtension(User user, LocalDateTime newExpiryAt) {
    var notification = Notification.createBlockExtensionNotification(user, newExpiryAt);
    persistAndSend(user, notification, "예약 차단 알림");
}
```

### 5-7. AdminReservationController 엔드포인트

```java
@PatchMapping("/users/{userId}/penalty/block")
@Operation(summary = "예약 차단 기간 연장", description = "특정 사용자의 예약 차단 기간을 일 단위로 연장합니다. ADMIN 권한이 필요합니다.")
public CommonApiResponse extendBlock(
        @Parameter(description = "사용자 ID") @PathVariable @NotNull Long userId,
        @Valid @RequestBody ExtendBlockReqDto reqDto) {
    extendCancellationBlockService.execute(userId, reqDto.days());
    return CommonApiResponse.success("예약 차단 기간이 연장되었습니다.");
}
```

---

## 6. 구현 순서

```
1. ExtendBlockReqDto 생성
2. PenaltyRedisUtil.extendBlock() 추가
3. Notification.createBlockExtensionNotification() 추가
4. ReservationNotificationSupport.sendBlockExtension() 추가
5. ExtendCancellationBlockService 인터페이스 생성
6. ExtendCancellationBlockServiceImpl 구현체 생성
7. AdminReservationController 엔드포인트 추가
8. 테스트 작성
```

---

## 7. 테스트 시나리오

| 시나리오 | 기대 결과 |
|----------|-----------|
| 활성 차단 있는 호실, 관리자가 2일 연장 | 남은 TTL + 172,800초, 알림 발송 |
| 활성 차단 없는 호실 | 400 Bad Request |
| 관리자가 아닌 사용자 요청 | 403 Forbidden |
| days = 0 또는 음수 | 400 (Bean Validation) |
| userId 없음 | 404 Not Found |
