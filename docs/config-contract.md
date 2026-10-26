# Spring Config Contract 실험 도입 (#261)

애플리케이션이 요구하는 설정 키와 배포 입력이 제공하는 키를 릴리스 전에 비교하기 위해 `io.github.inryeok-office.config-contract` 0.1.0을 **수동 실행 전용**으로 적용한다.

## 구성

| 항목 | 값 |
|---|---|
| 플러그인 | `io.github.inryeok-office.config-contract` 0.1.0 (Gradle Plugin Portal) |
| 분석 대상 설정 | `src/main/resources/application.yml` (기본 profile만, `activeProfiles` 미지정) |
| 대표 배포 입력 | `config-contract/.env.example` — `application.yml` placeholder 키 이름만, 값 없음 |
| Compose 입력 | 사용하지 않음 (`compose.yaml`은 MySQL·Redis 인프라 변수만 담고 있음) |
| lifecycle 연결 | 없음 (`check`, `build`, `test`, `bootJar`, CI/CD 모두 미연결) |

## 실행 방법

플러그인이 컴파일된 애플리케이션 클래스(Java 25, class file major 69)를 Gradle daemon 안에서 로딩하므로, **Gradle을 JDK 25 이상으로 실행해야 한다.** daemon이 JDK 21 이하이면 `UnsupportedClassVersionError`로 실패한다.

```bash
JAVA_HOME=<JDK 25 경로> ./gradlew --version   # Daemon JVM이 25인지 확인
JAVA_HOME=<JDK 25 경로> ./gradlew configContractCheck
```

`compileJava`가 `spotlessApply`에 의존하므로 실행 전후로 `git status`를 비교해 의도하지 않은 포맷 변경이 없는지 확인한다.

## 검증 결과 (2026-10-26, Windows 11, Gradle 9.4.0, daemon Corretto 25.0.3)

| 시나리오 | 결과 |
|---|---|
| 대표 입력 그대로 | 통과 — `Configuration contract check passed: no findings` |
| `JWT_SECRET` 제거 + 가짜 키 추가 | 실패 — `MISSING JWT_SECRET (application.yml:38)`, `UNUSED WASHER_BOGUS_KEY` |
| `CW_LOG_GROUP`, `MYSQL_PASSWORD` 추가 | 실패 — 두 키 모두 `UNUSED` (아래 오탐 참고) |
| `check`/`build`/`test`/`bootJar` dry-run | `configContractCheck` 미포함 |
| `spotlessCheck test` | Spotless 통과, 테스트 952건 중 949건 통과. 실패 3건은 로컬 Docker 미실행으로 인한 Testcontainers `initializationError`로 플러그인과 무관 |
| 실행 전후 `git status` | 변경 없음 |

통과 시 출력되는 진단(빌드를 실패시키지 않음):

```
UNSUPPORTED ...global.config.AsyncConfig: Class cannot be inspected (NoClassDefFoundError: org/springframework/core/task/AsyncListenableTaskExecutor)
UNSUPPORTED ...global.security.data.CorsEnvironment#allowedOriginPatterns: Property type java.util.List is not a supported single value ...
UNSUPPORTED ...global.security.data.CorsEnvironment#allowedOrigins: Property type java.util.List is not a supported single value ...
```

## 결과 분류

| 결과 | 분류 | 근거 |
|---|---|---|
| `MISSING JWT_SECRET` (음성 대조) | 실제 결함 탐지 | `jwt.secret: ${JWT_SECRET}`는 기본값 없는 유일한 필수 placeholder |
| `UNUSED CW_*` | 오탐 (도구 제한) | `logback-spring.xml`의 placeholder는 발견 대상이 아님 → 대표 입력에서 제외 |
| `UNUSED MYSQL_*` | 오탐 (입력 부적합) | Compose의 모든 서비스 `environment`를 애플리케이션 제공 키로 간주 → `compose.yaml`을 입력으로 쓰지 않음 |
| `UNSUPPORTED AsyncConfig` | 도구 제한 (Spring Boot 4) | Spring Framework 7에서 제거된 타입을 참조하는 클래스를 검사하지 못함. `AsyncConfig`에는 설정 바인딩이 없어 누락 키 없음 |
| `UNSUPPORTED CorsEnvironment#allowed*` | 도구 제한 | collection 바인딩 미지원. `spring.security.cors.*`는 `application.yml`에 값이 있어 실영향 없음 |

## 알려진 한계

- **실제 배포 방식과 다름**: stage/prod는 환경 변수를 주입하지 않고, CD가 GitHub Secret으로 생성한 `application-{stage,prod}.yml`을 jar에 포함한다. 대표 입력은 "환경 변수로 덮어쓸 수 있는 키 목록"이며 실제 배포 입력을 검증하지 않는다. 실제 secret 파일은 분석 입력으로 쓰지 않는다.
- **profile 범위**: 기본 profile만 검사한다. stage/prod 설정 파일은 저장소에 없으므로 검사하지 않는다.
- **Java record 필수성**: Java `@ConfigurationProperties`의 모든 프로퍼티는 선택 항목으로 취급된다. record 생성자나 Bean Validation의 런타임 필수성은 반영되지 않는다.
- **중첩/collection/map**: `List` 등은 `UNSUPPORTED`로 건너뛴다.
- **logback 및 외부 라이브러리 설정**: `CW_*`, `SPRING_PROFILES_ACTIVE`처럼 Spring 바인딩 밖에서 소비되는 키는 발견하지 못하며, 입력에 넣으면 `UNUSED`로 빌드가 실패한다. 0.1.0에는 allowlist가 없다.
- **공식 지원 범위**: 업스트림이 공식 지원하는 조합은 Spring Boot 3.5.16 + JDK 21뿐이다. Spring Boot 4 + JDK 25 결과는 업스트림 spike(inryeok-office/spring-config-contract#84)와 이 문서의 수동 검증에 근거한다.

## Go/No-Go

- **수동 실행 도구로 유지: Go** — Plugin Portal 해석, Java 25 class loading(daemon 25), Spring Boot 4.0.1 placeholder 발견, `MISSING`/`UNUSED` 판정 모두 기대대로 동작했고 기존 빌드에 영향이 없다.
- **CI/CD 강제 연결: No-Go (보류)** — 실제 배포가 환경 변수 기반이 아니어서 검사 신호가 사실상 `JWT_SECRET` 하나로 한정되고, allowlist 부재와 record 필수성 미반영 때문에 오탐·누락 위험이 크다. CI 연결은 배포 입력 표현 방식이 정해진 뒤 별도 이슈와 승인으로 진행한다.
