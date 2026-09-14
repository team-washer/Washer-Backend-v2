# SmartThings 상태 조회 서버 대행 전환 스펙

이슈: [#134](https://github.com/team-washer/Washer-Backend-v2/issues/134)
브랜치: `fix/134-smartthings-token-proxy`

---

## 1. 배경

앱은 `GET /api/v2/smartthings/token`으로 SmartThings 액세스 토큰 원문을 받아 메모리에 보관하고,
기기마다 `GET https://api.smartthings.com/v1/devices/{deviceId}/status`를 직접 호출해 상태를 덮어썼다.
토큰 원문은 외부 기기 제어 권한과 연결되므로 일반 사용자에게 전달하지 않도록 서버 대행 API로 전환한다.

앱 사용처(Washer-App-v2) 확인 결과:

| 항목 | 내용 |
|------|------|
| 직접 호출 API | `GET /v1/devices/{deviceId}/status` (조회만, 제어 명령 없음) |
| 사용 필드 | `machineState`, `jobState`, `switch`, `completionTime` |
| 사용 목적 | 서버 `/machines/status` 결과 위에 작동 상태·완료 시각 카운트다운을 덮어씀 |
| 호출 방식 | 기기별 1회씩 병렬 호출, `machineStatusProvider` 갱신 시점마다 실행 |

---

## 2. 서버 대행 API

`GET /api/v2/machines/{id}/device-status` (인증 사용자, 5층 기숙사생 제외)

```json
{
  "machineId": 1,
  "operatingState": "RUN",
  "jobState": "wash",
  "switchStatus": "on",
  "expectedCompletionTime": "2026-01-26T15:30:00",
  "remainingMinutes": 30
}
```

| 앱이 읽던 SmartThings 필드 | 대행 API 필드 |
|------|------|
| `*OperatingState.machineState.value` | `operatingState` |
| `*OperatingState.*JobState.value` | `jobState` |
| `switch.switch.value` | `switchStatus` |
| `*OperatingState.completionTime.value` | `expectedCompletionTime` (KST), `remainingMinutes` |

- SmartThings `deviceId` 대신 서버 기기 ID로 조회하므로 등록되지 않은 임의 기기는 조회할 수 없다.
- 세탁기/건조기 capability 분기는 서버가 기기 타입 기준으로 처리한다.
- 토큰 갱신은 서버가 담당하며, 외부 호출 실패 시 502를 반환한다. 앱의 토큰 갱신·401 재시도 로직은 제거한다.
- 응답·로그·오류 메시지에 토큰 원문을 포함하지 않는다.

---

## 3. 단계별 전환 절차

| 단계 | 서버 | 앱 |
|------|------|------|
| 1 | 대행 API 배포 | 구버전: 토큰 endpoint 사용 |
| 2 | 유지 | 신버전: 토큰 조회·저장·SmartThings 직접 호출 제거, 대행 API 사용 |
| 3 | 최소 지원 버전을 신버전으로 상향 (`/api/v2/app-versions/status`) | 구버전 강제 업데이트 안내 |
| 4 | 토큰 endpoint를 관리자(`DORMITORY_COUNCIL`, `ADMIN`) 전용으로 유지 | - |

- **호환 기간**: 신버전 앱 스토어 배포 후 최소 지원 버전 상향 전까지.
- **최소 지원 버전**: 대행 API를 사용하는 첫 앱 버전 (앱 배포 시 확정).

> 참고: develop에는 `fff4a55`에서 토큰 endpoint가 이미 관리자급 전용으로 제한되어 있다.
> 따라서 구버전 앱의 일반 사용자는 현재 토큰 조회가 403으로 실패한다. 대행 API 배포와 앱 전환을 우선 진행한다.

---

## 4. 롤백

- 앱 전환에 문제가 있으면 앱을 이전 버전으로 되돌리고 최소 지원 버전 상향을 보류한다.
- 대행 API는 조회 전용이며 기존 API에 영향을 주지 않으므로 서버 롤백 없이 유지할 수 있다.
- 구버전 앱의 SmartThings 오버레이를 긴급 복구해야 하는 경우에만 `DomainAuthorizationConfig`의 토큰 endpoint 제한을
  한시적으로 해제하고, 앱 전환 완료 후 다시 제한한다.
