# Washer Backend v2 — 프로젝트 흐름 가이드

> 처음 이 프로젝트를 파악하는 사람을 위한 문서입니다.
> "요청 하나가 들어와서 어떻게 흘러가는지"를 중심으로 설명합니다.

---

## 1. 이 프로젝트가 하는 일

**기숙사 세탁기/건조기 예약 시스템**입니다. 핵심은 이겁니다:

> 학생이 앱으로 세탁기를 예약 → 실제로 세탁기를 돌리면 **SmartThings(삼성 IoT)** 가 그걸 감지
> → 서버가 자동으로 "진행 중 → 완료" 처리 → 학생한테 푸시 알림

즉 **사람이 "시작했어요 / 끝났어요"를 입력하지 않습니다.**
서버가 실제 기기 상태를 계속 훔쳐보면서 알아서 상태를 바꿉니다. 이게 이 프로젝트의 가장 큰 특징입니다.

### 기술 스택

| 항목 | 내용 |
|------|------|
| 언어 / 프레임워크 | Java 25 (`--enable-preview`), Spring Boot 4.0.1 |
| 영구 저장소 | MySQL + Spring Data JPA + QueryDSL |
| 임시 저장소 | Redis (패널티, 쿨다운, refreshToken) |
| 외부 연동 | SmartThings(OpenFeign), DataGSM OAuth, FCM, Discord, AWS CloudWatch |
| 빌드 | Gradle 8.11.1 (Kotlin DSL) |
| 포맷터 | Spotless (120자, 4 스페이스) |

---

## 2. 전체 구조 (건물로 비유)

```
[모바일 앱]
    ↓ HTTP 요청 (JSON)
┌───────────────────────────────────────────┐
│  Controller  ← 손님 응대 창구 (URL 매핑)      │
│      ↓                                     │
│  Service     ← 실제 일 처리 (규칙/검증)       │
│      ↓                                     │
│  Repository  ← 창고 담당자 (DB 읽기/쓰기)     │
│      ↓                                     │
│  Entity      ← 창고 안의 물건 (DB 테이블)     │
└───────────────────────────────────────────┘
    ↕                    ↕
 [MySQL]              [Redis]         [SmartThings API]
 영구 저장           임시/만료 데이터      실제 세탁기 상태
                    (패널티, 쿨다운)
```

### 폴더 구조

```
src/main/java/team/washer/server/v2/
├── domain/          ← 기능별로 나눔 (여기가 90%)
│   ├── auth/        로그인, 토큰
│   ├── user/        사용자
│   ├── machine/     세탁기/건조기 기기
│   ├── reservation/ 예약 (가장 핵심!)
│   ├── notification/알림 (FCM 푸시)
│   ├── malfunction/ 고장 신고
│   ├── smartthings/ 삼성 IoT 연동
│   ├── admin/       관리자 기능
│   └── health/      헬스체크
└── global/          ← 공통 인프라
    ├── security/    JWT 인증, CORS, 권한 설정
    ├── config/      스케줄러 등 설정
    ├── common/      BaseEntity, 상수, 전역 예외처리
    └── thirdparty/  외부 연동 (Discord, AWS, DataGSM, SmartThings, Feign)
```

각 `domain/xxx/` 안은 항상 같은 모양입니다:

```
controller/  service/(인터페이스)  service/impl/(구현체)
repository/  entity/  dto/request/  dto/response/  enums/  support/
```

---

## 3. 흐름 ① — 로그인

`domain/auth/controller/AuthController.java:31` → `domain/auth/service/impl/SignInServiceImpl.java`

```
1. 앱이 DataGSM(학교 계정 서비스)에서 받은 인증코드를 보냄
      POST /api/v2/auth/login  { authCode, redirectUri }
                ↓
2. 서버가 DataGSM에 그 코드를 주고 "이 학생 누구야?" 물어봄
      → 학번, 이름, 학년, 호실 정보를 받음
                ↓
3. 우리 DB에 그 학번이 있나?
      있음  → 그 User 사용
      없음  → 자동 회원가입 (UserRegistrationSupport)
      단, 탈퇴 후 30일 안 지났으면 거부 (WithdrawnStudentRedisUtil)
                ↓
4. JWT 토큰 2개 발급해서 돌려줌
      accessToken  (1시간)   → 매 요청마다 사용
      refreshToken (30일)    → accessToken 만료 시 재발급용
```

### 인증 API 목록

| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/api/v2/auth/login` | DataGSM OAuth 인증 코드로 로그인 |
| POST | `/api/v2/auth/refresh` | Refresh Token으로 Access Token 재발급 |
| POST | `/api/v2/auth/token/status` | 앱 시작 시 토큰 유효성 점검 (예외 없이 `valid=false` 반환) |

### 이후 모든 요청

헤더에 `Authorization: Bearer {accessToken}` 을 달고 옵니다.
`global/security/jwt/filter/JwtAuthenticationFilter` 가 이걸 가로채서 토큰을 까보고,
"이 요청은 userId=123번 학생" 이라고 표시(SecurityContext)를 붙여줍니다.

그래서 Service 코드에서는 이렇게 현재 사용자를 꺼냅니다:

```java
final var userId = currentUserProvider.getCurrentUserId();  // 자동으로 알아냄
```

> `global/security/provider/CurrentUserProvider.java` — 인증 정보가 없으면 401 `ExpectedException`

---

## 4. 흐름 ② — 예약 생성 (가장 중요)

`domain/reservation/controller/ReservationController.java:53`
→ `domain/reservation/service/impl/CreateReservationServiceImpl.java:44`

```
POST /api/v2/reservations  { machineId: 5 }
```

서버는 **9단계 검증**을 순서대로 통과시킵니다. 하나라도 걸리면 에러:

| # | 검증 | 이유 |
|---|------|------|
| 1 | 층 제한 | 자기 층 기기만 예약 가능 |
| 2 | 호실 세탁 금지 | 관리자가 막아둔 호실인가 (`WashingBan`) |
| 3 | 48시간 차단 | 48시간 내 취소 4회 초과 → 호실 전체 차단 |
| 4 | 시간 제한 | 평일 21:20 이후, 일요일은 학년별 시작 시각 |
| 5 | **기기 락 걸고 조회** | 동시에 두 명이 같은 기기 예약하는 것 방지 |
| 6 | 쿨다운 | 취소 후 5분간 같은 종류 재예약 금지 |
| 7 | 기기 가용성 | 이미 사용 중 / 고장인가 |
| 8 | 1인 1예약 | 개인은 활성 예약 1개만 |
| 9 | 호실 중복 | 같은 방에서 세탁기 2대 예약 금지 (세탁기1 + 건조기1은 OK) |

통과하면:

- `Reservation` 을 `RESERVED` 상태로 저장
- `Machine` 을 "예약됨"(`markAsReserved()`)으로 표시

### 여기서 5번의 비관적 락이 중요합니다

```java
// 동일 기기 동시 예약 직렬화를 위해 비관적 쓰기 락으로 조회
final Machine machine = machineRepository.findByIdForUpdate(reqDto.machineId())
        .orElseThrow(() -> new ExpectedException("기기를 찾을 수 없습니다", HttpStatus.NOT_FOUND));
```

두 사람이 정확히 동시에 같은 세탁기를 누르면, DB가 한 명을 잠깐 기다리게 해서 순서대로 처리합니다.
안 그러면 둘 다 예약에 성공해버립니다.

### 시간 제한 규칙

`global/common/constants/TimeRestrictionConstants.java`

| 상수 | 값 | 의미 |
|------|-----|------|
| `RESTRICTION_START_TIME` | 08:00 | 제한 적용 시작 |
| `WEEKDAY_START_TIME` | 21:20 | 평일 예약 가능 시각 |
| `SUNDAY_GRADE_1_START_TIME` | 20:00 | 일요일 1학년 |
| `SUNDAY_GRADE_2_START_TIME` | 20:20 | 일요일 2학년 |
| `SUNDAY_GRADE_3_START_TIME` | 20:40 | 일요일 3학년 |

> 개발 환경에서는 `reservation.disable-time-restriction: true` 로 이 검증을 끌 수 있습니다.

### 예약 API 목록

| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/api/v2/reservations` | 예약 생성 |
| DELETE | `/api/v2/reservations/{id}` | 예약 취소 |
| GET | `/api/v2/reservations/active` | 내 활성 예약 |
| GET | `/api/v2/reservations/active/room` | 내 호실 활성 예약 목록 |
| GET | `/api/v2/reservations/history` | 예약 히스토리 (필터 + 페이징) |
| GET | `/api/v2/reservations/availability` | 예약 가능 여부 / 패널티 해제 시간 |
| GET | `/api/v2/reservations/{id}` | 예약 상세 |

---

## 5. 흐름 ③ — 예약의 일생 (자동 상태 전환)

예약 상태는 4가지입니다 (`domain/reservation/enums/ReservationStatus.java`):

```
RESERVED (예약됨) ──> RUNNING (실행 중) ──> COMPLETED (완료)
    │                      │
    └──> CANCELLED <───────┘
```

```java
RESERVED("예약됨", 3), RUNNING("실행 중", 0), COMPLETED("완료", 0), CANCELLED("취소", 0);
//                 ↑ 타임아웃 3분
```

### 그런데 누가 상태를 바꿔줄까요? → **스케줄러**

`domain/reservation/scheduler/ReservationLifecycleScheduler` 가 일정 주기로 계속 돌면서:

```
1. RESERVED 상태 예약들을 전부 가져옴
        ↓
2. 각 예약의 기기 ID로 SmartThings API 호출
        "이 세탁기 지금 돌고 있어?"
        ↓
3-A. 돌고 있다 → RUNNING 으로 변경 + 예상 완료시간 저장
                 + 사용자에게 "세탁 시작됐어요" 푸시 알림

4. RUNNING 상태 예약들도 똑같이 확인
        ↓
4-A. 끝났다     → COMPLETED + "수거해 가세요" 알림
4-B. 멈췄다     → 중단 처리 (패널티 없이 취소)
4-C. 일시정지   → 10분 넘으면 자동 취소
```

`ReservationTimeoutScheduler` 는 별도로 돕니다:
**예약해놓고 3분 안에 안 돌리면 자동 취소** (`RESERVED("예약됨", 3)` 의 그 3분).

### 등록된 스케줄러 전체

| 클래스 | 주기 | 하는 일 |
|--------|------|---------|
| `ReservationLifecycleScheduler` | fixedDelay | RESERVED→RUNNING→COMPLETED 전환 |
| `ReservationTimeoutScheduler` | fixedDelay | 3분 미시작 예약 자동 취소 |
| `IdleMachineShutdownScheduler` | fixedDelay | 유휴 기기 전원 차단 |
| `SmartThingsDeviceSyncScheduler` | cron `10 45 8 * * *` | 매일 08:45:10 기기 목록 동기화 |
| `SmartThingsTokenRefreshScheduler` | fixedRate | SmartThings 토큰 갱신 |

### 여기 숨어있는 설계 포인트

`ProcessReservationLifecycleServiceImpl.java` 주석에 답이 있습니다:

> 외부 API 호출은 트랜잭션 **밖**에서, DB 갱신만 짧은 독립 트랜잭션으로.

왜냐면 SmartThings API가 느리게 응답할 때 DB 커넥션을 붙잡고 있으면,
커넥션 풀이 바닥나서 **서버 전체가 멈춥니다**. 그래서:

```
[트랜잭션]      대상 목록 조회      ← 짧게 (readOnly)
[트랜잭션 없음]  외부 API 호출       ← 느려도 안전
[트랜잭션]      예약 1건 갱신       ← REQUIRES_NEW, 하나 실패해도 나머지 진행
```

관련 클래스 2개로 역할이 나뉘어 있습니다:

- `ProcessReservationLifecycleServiceImpl` — 외부 API 호출 + 조율 (트랜잭션 없음)
- `ReservationLifecycleProcessor` — DB 갱신만 담당 (트랜잭션 경계)

### 완료 판정의 방어 로직

`ReservationLifecycleProcessor` 에는 오탐을 막는 장치가 여럿 있습니다:

| 상수 | 값 | 목적 |
|------|-----|------|
| `COMPLETION_EARLY_LOG_TOLERANCE_MINUTES` | 2 | 너무 이른 완료 보고 허용 오차 |
| `COMPLETION_BOUNDARY_GRACE_MINUTES` | 5 | 경계 시각 유예 |
| `MAX_REASONABLE_CYCLE_MINUTES` | 240 | 비정상적으로 긴 사이클 차단 |

또 `Reservation.interruptionCount` 는 **디바운스 카운터**입니다.
세탁기가 사이클 단계 전환 중 순간적으로 "정지"로 보고하는 것을 진짜 중단과 구분하기 위한 장치입니다.

---

## 6. 흐름 ④ — 취소와 패널티 (Redis 활용)

`domain/reservation/service/impl/CancelReservationServiceImpl.java:34`

```
DELETE /api/v2/reservations/{id}
        ↓
내 예약 맞나? 활성 상태인가? 확인
        ↓
RESERVED 상태에서 취소 = "그냥 마음 바뀜" → 패널티
RUNNING  상태에서 취소 = 이미 돌아감      → 패널티 없음
        ↓
패널티 3종 세트 (전부 Redis 에 저장):
  ① 쿨다운   5분간 같은 종류 재예약 금지    (개인)
  ② 취소기록 48시간 카운터 +1              (개인)
  ③ 4회 초과 시 → 호실 전체 48시간 차단     (호실)
```

### 패널티 상수

`global/common/constants/PenaltyConstants.java`

| 상수 | 값 |
|------|-----|
| `PENALTY_DURATION_MINUTES` | 10 |
| `COOLDOWN_DURATION_MINUTES` | 5 |
| `WARNING_DURATION_DAYS` | 7 |
| `MAX_CANCELLATIONS_IN_48H` | 4 |
| `CANCELLATION_WINDOW_HOURS` | 48 |

### 왜 MySQL이 아니라 Redis일까요?

"5분 뒤 자동 소멸", "48시간 뒤 자동 소멸" 같은 데이터는 Redis의 TTL 기능으로 넣어두면 **알아서 사라집니다**.
MySQL이면 만료된 데이터를 지우는 코드를 따로 짜야 합니다.

정리하면:

- **MySQL** = 영구 기록 (사용자, 예약 이력, 기기, 고장신고)
- **Redis** = 시한부 데이터 (패널티, 쿨다운, refreshToken, 탈퇴 학생 기록)

관련 유틸: `domain/reservation/util/PenaltyRedisUtil.java`

---

## 7. 흐름 ⑤ — 알림

```
서버 내부 이벤트 발생
   (세탁 시작 / 완료 / 중단 / 자동취소 / 차단)
        ↓
ReservationNotificationSupport.sendXxx()
        ↓
   ┌────┴────┐
   ↓         ↓
DB에 저장   FCM 푸시 발송
(알림함)    (사용자 폰)
```

### 알림 발송 메서드

`domain/notification/support/ReservationNotificationSupport.java`

| 메서드 | 상황 |
|--------|------|
| `sendStarted` | 세탁/건조 시작 감지 |
| `sendCompletion` | 완료 감지 |
| `sendInterruption` | 예기치 않은 중단 |
| `sendPauseTimeout` | 일시정지 10분 초과 |
| `sendAutoCancellation` | 시간 초과 자동 취소 |
| `sendTimeoutWarning` | 첫 타임아웃 (패널티 없음 안내) |
| `sendCancellationBlock` | 48시간 차단 적용 |
| `sendBlockExtension` | 차단 연장 |

### 메시지 템플릿

`domain/notification/enums/NotificationType.java` 에 enum으로 다 박혀 있습니다:

```java
COMPLETION("완료 알림", "{machineName}의 {action} 완료되었습니다. 빠른 시간 내에 수거해 주시기 바랍니다.")
```

`{machineName}`, `{action}`, `{reason}`, `{completionTime}` 자리에 실제 값을 끼워넣는 방식입니다.
`formatMessage()` 오버로드들이 그 치환을 담당합니다.

---

## 8. 도메인별 요약

### `domain/user`

- `User` 엔티티: 이름, 학번, 호실, 학년, 층, 패널티 횟수, 권한(`UserRole`), FCM 토큰
- 인덱스: 학번(unique), 호실, (층+학년), 권한
- 비즈니스 메서드: `validateFloorRestriction()`, `validateTimeRestriction()`, 패널티 증가 등

### `domain/machine`

- `Machine` 엔티티: 이름, 타입(세탁기/건조기), SmartThings `deviceId`, 층, 위치, 번호
- 상태 2종:
  - `MachineStatus` — 기기 자체 정상/고장 여부
  - `MachineAvailability` — 예약 가능/예약됨/사용중
- 상태 변경: `markAsReserved()`, `markAsInUse()`, `markAsAvailable()`

### `domain/malfunction`

- 학생이 고장 신고 → 관리자가 상태 변경 (`MalfunctionReportStatus`)
- 사용자용 / 관리자용 컨트롤러 분리

### `domain/admin`

- `AdminDashboardController` — 대시보드 통계
- `AdminWashingBanController` — 특정 호실 세탁 금지 (`WashingBan`)
- 각 도메인에도 `Admin*Controller` 가 별도로 존재 (기기, 예약, 사용자, SmartThings)

### `domain/smartthings`

가장 파일이 많은 도메인입니다. 크게 3가지 역할:

1. **OAuth / 토큰 관리** — `ExchangeSmartThingsTokenService`, `RefreshSmartThingsTokenService`, `SmartThingsTokenProvider`
   - `SmartThingsToken` 은 `BaseEntity` 를 상속하지 않아 `@Version` 을 직접 선언합니다
2. **기기 상태 조회 / 명령** — `DeviceStatusQuerySupport`, `SendDeviceCommandService`, `DeviceShutdownSupport`
3. **상태 해석** — `MachineStateDetectionSupport` 의 `isRunning` / `isCompleted` / `isInterrupted` / `isPaused`
   - 세탁기와 건조기의 상태 표현이 달라서 `isWasher` 플래그로 분기합니다

### `global/thirdparty`

| 패키지 | 용도 |
|--------|------|
| `discord` | 에러 발생 시 웹훅 알림 (`DiscordErrorNotificationService`) |
| `aws/cloudwatch` | 로그 appender |
| `datagsm` | 학교 계정 OAuth |
| `smartthings` | Feign 클라이언트 + 에러 디코더 |
| `feign` | 공통 Feign 설정 / 에러 처리 |

---

## 9. 코드 작성 규칙 (이 프로젝트만의 스타일)

이 프로젝트는 규칙이 꽤 빡빡합니다. 남의 코드 볼 때 헷갈리지 않으려면 이것만 알면 됩니다.

### ① 서비스는 메서드 딱 1개

```java
public interface CreateReservationService {
    ReservationResDto execute(CreateReservationReqDto reqDto);   // execute 하나뿐
}
```

그래서 `reservation/service/` 폴더에 파일이 16개나 있습니다.
`CreateReservationService`, `CancelReservationService`, `QueryReservationService`...
**"클래스 이름이 곧 기능 이름"** 이라 찾기가 쉽습니다.

네이밍: `{Action}{Entity}Service` / 구현체는 `{Interface}Impl`

### ② DTO는 무조건 record

```java
public record CreateReservationReqDto(@NotNull Long machineId) {}
```

- `from()` 같은 정적 팩토리 메서드 **금지** → 변환은 Service 안에서 손으로 씁니다
- 접미사는 `ReqDto` / `ResDto`
- Jakarta validation 애노테이션 사용
- MapStruct 안 씁니다

### ③ 모든 Entity는 BaseEntity 상속

```java
public class Reservation extends BaseEntity { ... }
```

`id`, `createdAt`, `updatedAt`, `version`(낙관적 락) 은 자동으로 딸려옵니다. 다시 선언하면 안 됩니다.

엔티티 필수 애노테이션 조합:

```java
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
```

모든 연관관계는 `FetchType.LAZY` 입니다.

### ④ 예외는 그냥 던진다

```java
throw new ExpectedException("기기를 찾을 수 없습니다", HttpStatus.NOT_FOUND);
```

커스텀 예외 클래스를 만들지 않습니다.
메시지 + HTTP 상태코드만 넘기면 `GlobalExceptionHandler` 가 알아서 JSON 응답으로 바꿔줍니다.

### ⑤ 컨트롤러는 DTO를 그대로 반환

```java
public ReservationResDto createReservation(...) {
    return createReservationService.execute(requestDto);  // 감싸지 않음
}
```

SDK(`sdk.response.enabled: true`)가 자동으로 포장해줍니다.
응답 본문이 없을 때만 `CommonApiResponse` 를 직접 반환합니다.

### ⑥ 트랜잭션

- 조회: `@Transactional(readOnly = true)`
- 쓰기: `@Transactional`
- 외부 API가 끼면 트랜잭션 밖으로 빼기 (5장 참고)

### ⑦ 언어 규칙

- 주석 / Javadoc / 에러메시지 / API 문서 / 테스트 `@DisplayName` → **한국어**
- 로그 → **영어**, `key=value` 형식 (콜론 금지)

```java
log.info("Created reservation {} for user {} on machine {}", saved.getId(), userId, machine.getId());
log.warn("48h block applied roomNumber {}", user.getRoomNumber());
```

---

## 10. 처음 코드를 읽는다면 이 순서로

```
1. Reservation.java                    엔티티 - 데이터가 뭔지
2. ReservationController.java          API 목록 - 뭘 할 수 있는지
3. CreateReservationServiceImpl.java   가장 복잡한 비즈니스 규칙
4. ReservationLifecycleProcessor.java  자동 상태 전환의 핵심
5. JwtAuthenticationFilter.java        인증이 어떻게 붙는지
6. MachineStateDetectionSupport.java   외부 기기 상태를 어떻게 해석하는지
```

그리고 서버 띄운 뒤 **Swagger UI**(`/swagger-ui.html`)를 열면
전체 API를 한국어 설명과 함께 볼 수 있습니다.
실제로 버튼 눌러서 테스트도 가능하니 이게 제일 빠른 파악 방법입니다.

> prod 프로파일에서는 `SwaggerPathObfuscator` 가 경로를 난독화하므로 기본 경로로 접근되지 않습니다.

---

## 11. 자주 쓰는 명령어

```bash
./gradlew spotlessApply    # 포맷 정리 (compileJava 전에 자동 실행됨)
./gradlew build            # 빌드
./gradlew test             # 테스트
```

슬래시 커맨드: `/spotless-format`, `/split-commits`

### 커밋 규칙

```
add|update|fix|delete|docs|test|merge|init: {한국어 설명}
```

브랜치: `master`, `develop`, `feat/`, `fix/`, `docs/`
