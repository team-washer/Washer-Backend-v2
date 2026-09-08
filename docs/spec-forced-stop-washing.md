# 세탁 강제 금지 기능 구현 스펙

브랜치: `feat/forced-stop-washing`

---

## 1. 기능 개요

관리자가 특정 호실의 세탁기/건조기 예약을 무기한 차단하는 기능.  
기존 활성 예약(RESERVED/RUNNING)은 유지하며, **신규 예약 생성만 차단**한다.

---

## 2. 설계 결정 요약

| 항목 | 결정 |
|------|------|
| 발동 주체 | 관리자(Admin) |
| 금지 단위 | 호실(`roomNumber`, String 3-4자리) |
| 지속 기간 | 무기한 (관리자 명시적 해제 필요) |
| 기존 예약 처리 | 유지 (신규 예약만 차단) |
| 저장 방식 | 새 엔티티 `WashingBan` (`domain/admin/` 하위) |
| 중복 금지 요청 | 에러 반환 ("이미 금지된 호실입니다") |
| 사유 필드 | 없음 |
| 사용자 노출 | 예약 시도 시 에러 + availability API에 `isBanned` 추가 |
| 푸시 알림 | 없음 |

---

## 3. 신규 파일 목록

### 3-1. 엔티티

**`domain/admin/entity/WashingBan.java`**

```java
@Entity
@Table(name = "washing_bans",
    uniqueConstraints = @UniqueConstraint(name = "uk_room_number", columnNames = "room_number"))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class WashingBan extends BaseEntity {

    @NotBlank
    @Column(name = "room_number", nullable = false, length = 4)
    private String roomNumber;
}
```

- `BaseEntity` 상속 → `id`, `createdAt`, `updatedAt`, `@Version` 자동 포함
- `roomNumber`에 unique 제약 → 중복 금지 DB 레벨 보장

---

### 3-2. 레포지토리

**`domain/admin/repository/WashingBanRepository.java`**

```java
public interface WashingBanRepository extends JpaRepository<WashingBan, Long> {
    boolean existsByRoomNumber(String roomNumber);
    Optional<WashingBan> findByRoomNumber(String roomNumber);
}
```

---

### 3-3. DTO

**`domain/admin/dto/request/CreateWashingBanReqDto.java`**

```java
public record CreateWashingBanReqDto(
    @NotBlank(message = "호실은 필수입니다")
    @Pattern(regexp = "^\\d{3,4}$", message = "호실은 3-4자리 숫자여야 합니다")
    @Schema(description = "금지할 호실 번호", example = "301")
    String roomNumber
) {}
```

**`domain/admin/dto/response/WashingBanResDto.java`**

```java
public record WashingBanResDto(
    @Schema(description = "금지 ID") Long id,
    @Schema(description = "호실 번호", example = "301") String roomNumber,
    @Schema(description = "금지 등록 시각") LocalDateTime bannedAt
) {}
```

---

### 3-4. 서비스

**`domain/admin/service/CreateWashingBanService.java`**
```java
public interface CreateWashingBanService {
    void createWashingBan(CreateWashingBanReqDto reqDto);
}
```

**`domain/admin/service/impl/CreateWashingBanServiceImpl.java`**
- `WashingBanRepository.existsByRoomNumber()` 로 중복 확인
- 중복이면 `new ExpectedException("이미 금지된 호실입니다.", HttpStatus.CONFLICT)`
- `WashingBan` 저장

---

**`domain/admin/service/DeleteWashingBanService.java`**
```java
public interface DeleteWashingBanService {
    void deleteWashingBan(String roomNumber);
}
```

**`domain/admin/service/impl/DeleteWashingBanServiceImpl.java`**
- `WashingBanRepository.findByRoomNumber()` 조회
- 없으면 `new ExpectedException("금지된 호실이 아닙니다.", HttpStatus.NOT_FOUND)`
- 삭제

---

**`domain/admin/service/QueryAllWashingBansService.java`**
```java
public interface QueryAllWashingBansService {
    List<WashingBanResDto> queryAllWashingBans();
}
```

**`domain/admin/service/impl/QueryAllWashingBansServiceImpl.java`**
- `WashingBanRepository.findAll()` 후 `WashingBanResDto`로 매핑

---

### 3-5. 컨트롤러

**`domain/admin/controller/AdminWashingBanController.java`**

```
POST   /api/v2/admin/washing-bans              - 호실 금지 등록
DELETE /api/v2/admin/washing-bans/{roomNumber} - 호실 금지 해제
GET    /api/v2/admin/washing-bans              - 금지 호실 목록 조회
```

```java
@Tag(name = "관리자 세탁 금지 API")
@RestController
@RequestMapping("/api/v2/admin/washing-bans")
@RequiredArgsConstructor
public class AdminWashingBanController {

    private final CreateWashingBanService createWashingBanService;
    private final DeleteWashingBanService deleteWashingBanService;
    private final QueryAllWashingBansService queryAllWashingBansService;

    @Operation(summary = "호실 세탁 금지 등록")
    @PostMapping
    public CommonApiResponse createWashingBan(@RequestBody @Valid CreateWashingBanReqDto reqDto) {
        createWashingBanService.createWashingBan(reqDto);
        return CommonApiResponse.success();
    }

    @Operation(summary = "호실 세탁 금지 해제")
    @DeleteMapping("/{roomNumber}")
    public CommonApiResponse deleteWashingBan(@PathVariable String roomNumber) {
        deleteWashingBanService.deleteWashingBan(roomNumber);
        return CommonApiResponse.success();
    }

    @Operation(summary = "금지 호실 목록 조회")
    @GetMapping
    public List<WashingBanResDto> queryAllWashingBans() {
        return queryAllWashingBansService.queryAllWashingBans();
    }
}
```

---

## 4. 수정 파일 목록

### 4-1. `ReservationAvailabilityResDto`

`isBanned` 필드 추가:

```java
public record ReservationAvailabilityResDto(
    @Schema(description = "예약 가능 여부") boolean canReserve,
    @Schema(description = "패널티 만료 시간 (패널티 없을 경우 null)") LocalDateTime penaltyExpiresAt,
    @Schema(description = "호실 세탁 금지 여부") boolean isBanned
) {}
```

---

### 4-2. `QueryReservationAvailabilityServiceImpl`

`WashingBanRepository` 주입 후 현재 사용자의 `roomNumber`로 금지 여부 확인:

```java
boolean isBanned = washingBanRepository.existsByRoomNumber(user.getRoomNumber());
return new ReservationAvailabilityResDto(canReserve && !isBanned, penaltyExpiresAt, isBanned);
```

---

### 4-3. `CreateReservationServiceImpl`

예약 생성 로직 최상단에 금지 호실 검증 추가:

```java
if (washingBanRepository.existsByRoomNumber(user.getRoomNumber())) {
    throw new ExpectedException("해당 호실은 현재 세탁이 금지된 상태입니다.", HttpStatus.FORBIDDEN);
}
```

---

## 5. DB 마이그레이션

```sql
CREATE TABLE washing_bans (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    room_number VARCHAR(4)   NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_washing_bans PRIMARY KEY (id),
    CONSTRAINT uk_room_number UNIQUE (room_number)
);
```

---

## 6. 구현 순서

1. `WashingBan` 엔티티 + `WashingBanRepository`
2. `CreateWashingBanService` + Impl
3. `DeleteWashingBanService` + Impl
4. `QueryAllWashingBansService` + Impl + `WashingBanResDto`
5. `AdminWashingBanController` + `CreateWashingBanReqDto`
6. `CreateReservationServiceImpl` 금지 검증 추가
7. `ReservationAvailabilityResDto` + `QueryReservationAvailabilityServiceImpl` 수정
8. DB 마이그레이션 스크립트
