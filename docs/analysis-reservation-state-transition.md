# 예약 상태 전이 로직 정합성 분석

작성일: 2026-08-26
대상 브랜치: `fix/machine-completion-immediate-availability` (PR #102) 기준
관련 PR: #96, #101, #102

## 요약

PR #102 리뷰 중 발견된 문제를 추적하다가, 그 아래에 누적된 구조적 결함을 확인했다.
**"기기 사이클이 완료됐는가"를 판정하는 규칙이 코드 네 곳에 서로 다르게 구현되어 있다.**

PR #96 → #101 → #102로 이어지며 각각 다른 지점에 패치를 얹는 사이 이 편차가 벌어졌고,
그 결과 화면에 보이는 상태와 DB에 저장된 상태가 갈라지는 현상이 발생한다.

PR #102 범위의 항목은 해당 PR에 인라인 코멘트로 남겼다. 이 문서는 **PR #102만 되돌려서는
해결되지 않는 항목**을 정리한다.

## 작업 분할

`develop` 기준으로 브랜치를 나눠 진행한다.

| 브랜치 | 범위 | 상태 |
|---|---|---|
| `fix/reservation-completion-detection-unification` | 3장 1~3단계 (2-1 · 2-2 · 2-3 · 2-4) | PR #103 |
| `fix/reservation-lifecycle-residue` | 2-5 + 2-7 | 진행 중 |
| `fix/force-stop-cancellation-notification` | 2-6 | 예정 |
| `fix/active-reservation-selection` | 2-8 | 예정 |

별건은 코어 3단계와 독립적이므로 분리하되, 규모에 맞춰 묶는다.

- **2-5 + 2-7**: 각각 수 줄 규모이고 둘 다 reservation 도메인이라 하나로 묶는다.
- **2-6**: `NotificationType`을 새로 만들어야 하고 관리자 기능의 동작 자체가 바뀌므로 단독으로 둔다.
- **2-8**: 활성 예약 선택 규칙을 바꾸면 목록 API 표시가 달라져 회귀 확인 지점이 다르므로 단독으로 둔다.

`fix/reservation-lifecycle-residue`는 2-7이 코어 브랜치에서 재작성한 `completeReservation`을
건드리므로 `develop`이 아니라 **코어 브랜치 위에 쌓아 올린다.** 코어가 머지되면 base가
`develop`으로 전환된다.

---

## 1. 근본 원인: 완료 판정 규칙의 분산

| 위치 | 완료 판정 근거 | stale 가드 | 조기완료 가드 | 디바운스 |
|---|---|---|---|---|
| `MachineStateDetectionSupport.isCompleted` | jobState + machineState + completionTime | — | — | — |
| `ReservationLifecycleProcessor` (`isCompleted` 경로) | 위 + 3중 가드 | O | O | X |
| `ReservationLifecycleProcessor.getStoppedNearCompletionTime` | stop + completionTime ±5분 | **X** | **X** | X |
| `QueryAllMachinesStatusServiceImpl.getDisplayReservation` | `isCompleted`만 | **X** | **X** | X |

같은 질문에 네 가지 답이 나온다. 특히 아래 두 경로는 라이프사이클이 공들여 쌓아둔 방어를
전혀 거치지 않는다.

- `getStoppedNearCompletionTime` (PR #101에서 도입)
- `getDisplayReservation` (PR #101에서 도입)

참고로 중단 판정(`isInterrupted`)에는 이미 `INTERRUPTION_CONFIRM_THRESHOLD` 기반의
연속 감지(디바운스)가 있으나, **완료 판정에는 어느 경로에도 디바운스가 없다.**
SmartThings가 단 한 번 순간적으로 보고한 `stop`이 곧바로 확정 처리된다.

---

## 2. 발견 항목

### 2-1. [High] 목록 API와 라이프사이클이 서로 다른 상태를 보고한다

- 위치: `QueryAllMachinesStatusServiceImpl.java:115` (`getDisplayReservation`)
- 도입: PR #101 (`60e2c53`)

`getDisplayReservation`은 `isCompleted().isPresent()`만 보고 RUNNING 예약을 숨긴다.
반면 라이프사이클은 같은 신호를 `stale_completion` / `too_early_completion`으로
**보류**한다 (`ReservationLifecycleProcessor.java:106,115`).

즉 라이프사이클이 완료를 거부하는 동안에도 목록 API는 이미 AVAILABLE을 반환한다.
`getDisplayReservation`이 null을 반환하면 `computeAvailability`가
`reservation == null` + `isOperating == false`(stop)로 판단해 AVAILABLE을 내보내기 때문이다.

**증상 (사용자 관점)**

1. 기기 목록에 "사용 가능 / 남은 시간 없음"으로 표시된다.
2. 예약을 누르면 실패한다.
   - `CreateReservationServiceImpl.java:82` → `해당 기기를 사용할 수 없습니다` (400)
     — DB의 `machine.availability`는 여전히 `IN_USE`이기 때문
   - 또는 `CreateReservationServiceImpl.java:88` → `해당 기기에 이미 진행 중인 예약이 있습니다` (409)

PR #102는 `isCompleted`가 `present`를 반환하는 조건을 넓히므로 이 현상의 발생 빈도를 키운다.
사이클 도중 순간 정지 한 번에 목록이 "사용 가능"으로 깜빡인다. PR #101이 고치려던 증상
(완료 후에도 남은 시간이 계속 표시됨)의 정반대 오류다.

### 2-2. [High] `stopped_near_completion` 경로가 가드를 통째로 우회한다

- 위치: `ReservationLifecycleProcessor.java:234` (`getStoppedNearCompletionTime`), `:205` (`processStoppedNearCompletion`)
- 도입: PR #101 (`60e2c53`)

`getStoppedNearCompletionTime`은 `completionTime.isBefore(startTime)`만 확인한다.
`isCompleted` 경로에 있는 아래 가드가 **하나도 적용되지 않는다.**

- `isStaleCompletion`의 타임스탬프 검사 (`:293-294` — operatingStateTimestamp / jobStateTimestamp가 startTime 이전인지)
- `isTooEarlyCompletion` (`:297`)

그리고 `processStoppedNearCompletion`은 completionTime이 과거이기만 하면 곧바로
`completeReservation`을 호출한다 (`:205-207`).

**시나리오**: 60분 코스 10분 경과 시점에 `stop` + `jobState=wash` + completionTime이
잠깐 과거 값으로 보고되면 (단 startTime 이후) 즉시 완료가 확정된다.

### 2-3. [Medium] 중단 확정이 영구히 불가능해지는 조합

- 위치: `ReservationLifecycleProcessor.java:131` vs `:137`, `:211-214`

`getStoppedNearCompletionTime`(`:131`)이 `isInterrupted`(`:137`)보다 **먼저** 평가되고,
`processStoppedNearCompletion`이 `:211-214`에서 `interruptionCount`를 clear한다.

따라서 사용자가 전원을 끈(`switch=off`) 상태여도 completionTime이 ±5분 안이면
매 폴링(30초)마다 카운터가 0으로 리셋되어 `INTERRUPTION_CONFIRM_THRESHOLD`에
**절대 도달하지 못한다.**

그리고 completionTime이 지나는 순간 중단이 아니라 **정상 완료**로 처리되어,
`sendInterruption`이 아닌 `sendCompletion` 알림이 나간다.

### 2-4. [Medium] 가드가 자기 기준선을 스스로 오염시킨다

- 위치: `ReservationLifecycleProcessor.java:216-220`, `:193-200`

두 지점이 SmartThings의 completionTime을 `expectedCompletionTime`에 계속 덮어쓴다.
그런데 `isTooEarlyCompletion`(`:298`)은 **바로 그 `expectedCompletionTime`을 기준으로**
조기 완료를 판정한다. 정지 중 기기가 보고한 값이 기준선이 되면 가드가 무의미해진다.

또한 `:193-200`의 갱신에는 상한 검증이 없다. 이상치가 그대로 저장되고,
나중에 `hasSuspiciousExpectedCompletionTime`(`:331`, 240분 초과)이라는
**우회로로 그 이상치를 예외 처리**한다. 저장 시점에 상한을 검증하면 이 우회로 자체가 불필요하다.

### 2-5. [Medium] `ReservationTimeoutScheduler`만 운영시간 체크가 없다

- 위치: `ReservationTimeoutScheduler.java:20`

| 스케줄러 | 주기 | `operationTimePolicy` 확인 |
|---|---|---|
| `ReservationLifecycleScheduler` | 30초 | O (`:23`) |
| `IdleMachineShutdownScheduler` | 60초 | O (`:23`) |
| `ReservationTimeoutScheduler` | 60초 | **X** |

운영시간 외에도 60초마다 돌면서 RESERVED 예약을 취소하고
**패널티(쿨다운 · 취소횟수 · 48h 블록)를 부여**한다 (`OverdueReservationProcessor.java:94-100`).

라이프사이클 스케줄러는 멈춰 있어 RESERVED → RUNNING 전환이 일어나지 않는 시간대이므로,
그 시간에 남아 있던 예약은 자동 시작 기회 없이 취소 + 패널티만 받는다.

### 2-6. [Medium] 강제정지가 정상 사이클을 취소할 수 있고, 알림도 가지 않는다

- 위치: `ForceStopMachineServiceImpl.java:119`, `:122`

**(a) `ALREADY_STOPPED`인데 RUNNING이면 취소된다**

```java
if (forceStopResult != ForceStopResult.STOPPED && !reservation.isRunning()) {
    return null;
}
reservation.cancel();
```

뒤집으면 `ALREADY_STOPPED` + RUNNING 조합에서 취소가 실행된다.
`:73`의 stop 판정은 단발 조회라 라이프사이클의 디바운스가 전혀 없어서,
사이클 단계 전환 중 순간 stop일 때 관리자가 버튼을 누르면 정상 진행 중인 예약이 취소된다.

**(b) 취소 알림이 없다**

| 취소 경로 | 알림 |
|---|---|
| 기기 중단 확정 | `sendInterruption` |
| pause 타임아웃 | `sendPauseTimeout` |
| RESERVED 타임아웃 | `sendAutoCancellation` |
| **관리자 강제정지** | **없음** |

진행 중이던 사용자는 목록에서 예약이 사라진 것으로만 알 수 있다.

### 2-7. [Low] `complete()`가 잔여 상태를 정리하지 않는다

- 위치: `ReservationLifecycleProcessor.java:260` (`completeReservation`)

중단 경로(`:143`)와 pause 타임아웃 경로(`:172`)는 `clearInterruptionCount` /
`clearPausedAt`을 호출하는데, `completeReservation`은 `reservation.complete()`만 부른다.
`pausedAt` · `interruptionCount`가 COMPLETED 예약에 남아 관리자 이력에 노출된다.

### 2-8. [Low] "활성 예약 하나 고르기" 규칙이 두 가지다

| 위치 | 선택 규칙 |
|---|---|
| `ForceStopMachineServiceImpl.java:108` | RUNNING 우선, 없으면 RESERVED |
| `ReservationRepository.java:55` (`findActiveReservationByMachineId`) | `createdAt DESC` 첫 번째 |

목록 API는 후자를 쓰므로, 상태 드리프트로 RUNNING과 RESERVED가 공존하면
최신 RESERVED가 RUNNING을 가려 실제로는 IN_USE인 기기가 RESERVED로 표시된다.

---

## 3. 개선 순서 제안

### 1단계 — 완료 판정을 한 곳으로 모은다

가드(stale · 조기완료)까지 포함한 완료 판정을 `ReservationCompletionDecisionSupport`
하나로 모으고, 라이프사이클만 이것을 호출한다.

- 2-2의 `stopped_near_completion` 우회 경로도 이 통합 판정 안으로 들어간다.
- **목록 API는 완료를 예측하지 않는다.** `getDisplayReservation`을 제거하고 DB의 예약
  상태만 그대로 반영한다. 완료 확정은 라이프사이클이 디바운스와 가드를 거쳐 DB에 쓰고,
  목록은 그 결과만 읽으므로 2-1의 화면/DB 불일치가 구조적으로 불가능해진다.
  대가는 완료 반영이 라이프사이클 확정 시점까지 지연된다는 것(최대 30초 × 임계값).

### 2단계 — 완료 판정에도 디바운스를 적용한다

`isInterrupted`가 이미 쓰는 `INTERRUPTION_CONFIRM_THRESHOLD` 방식(연속 감지 확인)을
완료 판정에도 적용한다. 완료는 `COMPLETION_CONFIRM_THRESHOLD`(연속 2회)를 별도로 둔다.

- PR #102가 노린 "완료 직후 즉시 AVAILABLE 전환"은 최대 30초 지연으로 유지된다.
- 사이클 도중 순간 정지에 의한 오탐이 막힌다.
- 2-3의 `interruptionCount` 리셋 문제도 함께 해소된다
  (완료 판정과 중단 판정이 같은 디바운스 체계 위에 놓이므로).

### 3단계 — `expectedCompletionTime` 저장 시 상한을 검증한다

`MAX_REASONABLE_CYCLE_MINUTES`를 저장 시점에 적용하면
`hasSuspiciousExpectedCompletionTime` 우회로를 삭제할 수 있다 (2-4).

### 별건 (후속 브랜치로 각각 분리)

위 1~3단계와 의존 관계가 없으므로 코어 브랜치에 섞지 않고 개별 브랜치·PR로 처리한다.

- 2-5 `ReservationTimeoutScheduler`에 운영시간 체크 추가
- 2-6 강제정지의 `ALREADY_STOPPED` 취소 조건 수정 + 취소 알림 추가
- 2-7 `complete()` 시 잔여 상태 정리
- 2-8 활성 예약 선택 규칙 통일

---

## 4. PR #102 자체 항목 (참고 — 해당 PR에 코멘트 완료)

| 위치 | 내용 |
|---|---|
| `MachineStateDetectionSupport.java:88` | [High] reset-jobState 분기를 미래 completionTime 가드보다 앞으로 옮겨 가드가 무력화됨 |
| `ReservationLifecycleProcessor.java:317` | [High] `hasFreshStoppedCompletionEvidence`의 두 조건이 항상 통과해 `too_early_completion` 보류가 죽음 |
| `MachineStateDetectionSupport.java:95` | [Low] 아래로 밀려난 블록이 동작상 죽은 코드가 됨 |
| `ReservationLifecycleProcessorTest.java:327` | [Medium] `buildDeviceStatus(null)`은 machineState가 null이라 도달 불가능한 상태를 검증 |
| `ReservationLifecycleProcessor.java:358` | [Low] 두 수용 경로의 로그 reason이 합쳐져 구분 불가 |

제거된 가드는 `3ef60b2`(조기 완료 표시 수정)에서 "실제 종료 2~4분 전 완료 오인"을 막으려고
의도적으로 넣은 것이고, `1f5e786`(조기 완료 방어 보강)이 그 위에 쌓은 것이다.
