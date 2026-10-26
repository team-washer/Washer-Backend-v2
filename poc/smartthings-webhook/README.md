# SmartThings webhook 전환 PoC (#252)

[#251](https://github.com/team-washer/Washer-Backend-v2/issues/251)의 Phase 0입니다. 운영 코드와 운영 배포를 바꾸지 않고 다음 두 가지를 확인한 뒤 Go/No-Go를 기록합니다.

1. 현재 SmartThings Console/계정에서 API Access App의 Target URL과 subscription API를 실제로 쓸 수 있는가
2. 실제 세탁기·건조기가 `finish`/`finished`를 포함한 capability event를 보내는가

이 디렉터리는 Gradle 빌드·Spotless 대상이 아니며 JDK 25만으로 실행합니다(`Json.java`를 함께 로드하는 다중 파일 source launcher).

```bash
java SmartThingsWebhookPoc.java            # 사용법 출력
```

## 0. 시작 전 주의

- **운영 OAuth client를 재사용하지 마세요.** 같은 client로 다시 인가하거나 refresh하면 Installed App이나 refresh token이 바뀌어 운영 서버의 `SmartThingsToken`이 무효화될 수 있습니다. PoC 전용 App을 등록해 쓰는 것을 원칙으로 합니다. 운영 client를 꼭 써야 하면 운영 token refresh와 충돌하지 않는 시간대를 정하고 사전에 공유합니다.
- access/refresh token, client secret은 `poc-data/token.json`(gitignore, 소유자 전용 권한으로 생성)과 환경변수에만 둡니다. Issue, PR, 메신저에는 `installed_app_id`, `locationId`, deviceId, 측정값만 기록합니다.
- 공식 문서의 Service Integrations 영역에는 *"some features described in this section are coming soon"* 배너가 있고, [App Setup](https://developer.smartthings.com/docs/service-integrations/app-setup)에는 *"API Access App registration is coming soon and is not yet available."*이라고 적혀 있습니다(2026-10-14 확인). 1단계에서 막히면 그 자체가 No-Go 근거입니다.

## 1. 준비

| 항목 | 내용 |
|---|---|
| JDK | 25 이상 (`java -version`) |
| 공개 HTTPS | `cloudflared tunnel --url http://localhost:8090` 또는 `ngrok http 8090` 등. 서명에 `(request-target)`이 들어가므로 **경로를 바꾸거나 body를 다시 쓰는 프록시는 쓰지 않습니다** |
| 환경변수 | `SMARTTHINGS_CLIENT_ID`, `SMARTTHINGS_CLIENT_SECRET`, `SMARTTHINGS_REDIRECT_URI` |
| 선택 환경변수 | `SMARTTHINGS_OAUTH_URL`, `SMARTTHINGS_AUTHORIZE_URL`(기본값은 운영 `application.yml`과 동일. 공식 문서 표기는 `/v1/oauth/...`), `SMARTTHINGS_KEY_URL`(기본 `https://key.smartthings.com`), `POC_DATA_DIR`(기본 `poc-data`) |
| 동작 옵션 | `POC_AUTO_CONFIRM=true`: `*.smartthings.com` HTTPS confirmationUrl만 자동 GET / `POC_ENFORCE_SIGNATURE=true`: 서명 실패 시 401 / `POC_STATE_CHANGE_ONLY=false`: 같은 값 반복 event도 수신 / `POC_REPORT_INCLUDE_UNVERIFIED=true`: 서명 미검증 event도 리포트에 포함 |

## 2. 절차 (#252 체크리스트 순서)

### 2-1. Target URL·subscription 사용 가능 여부

1. `java SmartThingsWebhookPoc.java serve 8090`과 터널을 띄웁니다.
2. Developer Center Console → Service Integrations → Register New App에서 Target URL을 `https://<터널 host>/`로 등록합니다. 메뉴가 없거나 등록이 막히면 **화면과 문구를 그대로 기록**하고 No-Go 후보로 둡니다.
3. Console의 *Confirm Target URL*(또는 `smartthings apps:register <appId>`)을 누르면 `CONFIRMATION`이 들어옵니다. 콘솔에는 query를 가린 URL만 출력되므로 `poc-data/confirmation-url.txt`(소유자 전용)의 원문을 직접 GET 하거나 `POC_AUTO_CONFIRM=true`로 띄워 둡니다. Console 상태가 PENDING에서 CONFIRMED로 바뀌는지 확인합니다.

### 2-2. installed_app_id와 locationId

```bash
java SmartThingsWebhookPoc.java authorize-url "r:devices:*" "x:devices:*"   # 브라우저로 열어 인가
java SmartThingsWebhookPoc.java exchange <code>     # 응답 필드 목록과 installed_app_id만 출력
java SmartThingsWebhookPoc.java installed-app       # locationId 출력
```

`응답 필드` 목록에 `installed_app_id`가 없으면 운영 OAuth client 유형이 subscription 대상이 아닐 가능성이 크므로 그대로 기록합니다.

### 2-3. subscription 구성 비교

```bash
# DEVICE 단위: 기기 × capability마다 1개
java SmartThingsWebhookPoc.java subscribe-device <washerId> washerOperatingState
java SmartThingsWebhookPoc.java subscribe-device <washerId> switch switch
java SmartThingsWebhookPoc.java subscribe-device <dryerId>  dryerOperatingState

# location CAPABILITY 단위: 기기 수와 무관하게 capability(·attribute)마다 1개
java SmartThingsWebhookPoc.java subscribe-capability <locationId> washerOperatingState
java SmartThingsWebhookPoc.java subscribe-capability <locationId> dryerOperatingState
java SmartThingsWebhookPoc.java subscribe-capability <locationId> switch switch

java SmartThingsWebhookPoc.java list-subscriptions
java SmartThingsWebhookPoc.java delete-subscription <id|all>
```

- 두 방식을 동시에 걸면 같은 변화가 두 번 들어오므로 `subscriptionName`으로 구분하거나 한 방식씩 측정합니다.
- DEVICE body는 `device` 객체 안에 `componentId/capability/attribute/value/stateChangeOnly/subscriptionName`을 넣습니다. 공식 예시는 `subscriptionName`만 최상위에 두고 나머지는 서버가 `*`로 채운다고 설명하므로, 400이 나면 응답 본문을 기록하고 필터 없이 `subscribe-device <id> '*'`로 다시 시도합니다.
- 제한: 설치 앱당 subscription 40개, 생성 40회/15분, 그 외 Subscriptions API 15회/15분, EVENT 실행 20회/분/installed app ([Rate Limits](https://developer.smartthings.com/docs/getting-started/rate-limits)).

### 2-4. 실제 기기 측정

```bash
java SmartThingsWebhookPoc.java poll 30 <washerId> <dryerId>   # 운영과 같은 30초 snapshot을 병행 기록
```

세탁기·건조기를 각각 최소 1회 **정상 완료까지** 돌리고, 가능하면 한 번은 **중간 전원 차단**도 해서 #217의 두 경우를 모두 남깁니다. 시작·일시정지·완료 시각을 손으로도 적어 둡니다.

### 2-5. 리포트

```bash
java SmartThingsWebhookPoc.java report > poc-data/report.md
```

| 리포트 항목 | 확인할 것 |
|---|---|
| 수신 요청 / 서명 검증 결과 | 모든 EVENT가 `VALID`인지, 성공한 공개키 URL·형식(serve 출력 `공개키 조회 성공 url=`) |
| 분당 최대 EVENT 요청 | 전체 기기로 늘렸을 때 20회/분/installed app 한도 안에 드는지 |
| 정상 완료 신호 관측 | `washerJobState=finish`, `dryerJobState=finished`가 webhook으로 왔는지, polling은 놓쳤는지 |
| attribute별 비교 | 중복 eventId, 순서 역전, 지연(`receivedAt - eventTime`), 한쪽에만 보인 값, webhook 누락 의심 변화 |
| 타임라인 | `machineState`·job state·`completionTime`·`switch`의 실제 전이 순서 |

## 3. Go/No-Go 기준

| # | 기준 | 미충족 시 |
|---|---|---|
| G1 | Target URL이 CONFIRMED가 되고 subscription 생성이 성공한다 | **No-Go** |
| G2 | token 응답에 `installed_app_id`가 있고 `locationId`를 조회할 수 있다 | **No-Go** |
| G3 | 정상 완료 cycle에서 `finish`(세탁기)/`finished`(건조기) 또는 동등한 완료 event가 webhook으로 온다 | **No-Go** (#217 해결 불가) |
| G4 | 모든 EVENT의 HTTP Signature·digest 검증이 성공하고 key URL·형식이 확정된다 | 확정 전까지 No-Go |
| G5 | `machineState`·job state·`switch` 변화 중 webhook 누락 의심이 없거나 원인을 설명할 수 있다 | 조건부(Phase 3 reconciliation 필수 근거로 기록) |
| G6 | 선택한 구성이 subscription 40개와 EVENT 20회/분 한도 안에 든다 | 구성 변경 후 재측정 |

## 4. #251 기록 양식

```markdown
## Phase 0 결과 (#252): Go | No-Go

- 측정 일시 / 기기: YYYY-MM-DD, 세탁기 <deviceId 일부>, 건조기 <deviceId 일부>
- Console: Target URL 등록 가능 여부, CONFIRMATION 수신·CONFIRMED 전환 여부
- OAuth: installed_app_id 존재 여부, locationId 조회 여부 (token 미기록)
- subscription: 선택 구성(DEVICE/CAPABILITY), 필요 개수(현재 기기 수 기준) / 40
- event 관측: finish·finished 수신 여부, 지연 p50/p95/max, 중복·순서 역전·누락 의심 건수, 분당 최대 EVENT
- 서명: key URL·형식, VALID 비율
- 판정: G1~G6 각각 O/X
- (No-Go) blocker: …
- (No-Go) polling 보완안: …
```

No-Go일 때 검토할 polling 보완안 후보입니다. 모두 #251 Phase 3의 reconciliation 방향과 겹칩니다.

- `completionTime` 근처에서만 해당 기기의 조회 주기를 짧게 하는 적응형 polling (#226의 bounded status-query와 함께 설계)
- attribute `timestamp`가 직전 snapshot 이후 갱신됐는데 값이 `none`인 경우를 "완료 신호를 놓쳤을 수 있음"으로 표시하는 판정 근거 보강 (#217 정책 결정에 입력)
- SmartThings 기기 이력 조회 API로 polling 사이 event를 보충할 수 있는지 별도 확인 (현재 공식 문서·scope 미확인)
