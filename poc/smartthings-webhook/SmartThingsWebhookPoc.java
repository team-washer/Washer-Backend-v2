import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * SmartThings webhook 전환 가능 여부(#252) 확인용 PoC. 운영 서버와 별개로 JDK만으로 실행한다.
 *
 * <p>
 * 실행: {@code java SmartThingsWebhookPoc.java <command> [args]} (Java 25, 같은 디렉터리의 Json.java를 함께 로드)
 *
 * <p>
 * 토큰·client secret은 환경변수와 gitignore된 {@code poc-data/} 아래 파일로만 다루며 표준 출력·기록 파일에 남기지 않는다.
 */
public class SmartThingsWebhookPoc {

    static final String API_URL = env("SMARTTHINGS_API_URL", "https://api.smartthings.com");
    static final String OAUTH_TOKEN_URL = env("SMARTTHINGS_OAUTH_URL", "https://api.smartthings.com/oauth/token");
    static final String KEY_BASE_URL = env("SMARTTHINGS_KEY_URL", "https://key.smartthings.com");
    static final Path DATA_DIR = Path.of(env("POC_DATA_DIR", "poc-data"));
    static final Path TOKEN_FILE = DATA_DIR.resolve("token.json");
    static final Path EVENTS_FILE = DATA_DIR.resolve("events.jsonl");
    static final Path POLLS_FILE = DATA_DIR.resolve("polls.jsonl");
    static final Path REQUESTS_FILE = DATA_DIR.resolve("requests.jsonl");
    static final Path CONFIRMATION_URL_FILE = DATA_DIR.resolve("confirmation-url.txt");

    /** confirmationUrl 자동 호출을 허용하는 host 접미사. SSRF 방지를 위해 SmartThings 도메인만 허용한다. */
    static final List<String> CONFIRMATION_HOST_SUFFIXES = List.of(".smartthings.com");
    /** 서명 Date 헤더 허용 오차. */
    static final Duration MAX_DATE_SKEW = Duration.ofMinutes(5);
    static final int MAX_BODY_BYTES = 256 * 1024;
    /** 공개키 캐시 TTL. 공식 문서가 key rotation을 이유로 짧은 캐시를 권장한다. */
    static final Duration KEY_CACHE_TTL = Duration.ofHours(3);
    /** false로 두면 값이 같은 반복 event도 받아 중복·재전송 양상을 볼 수 있다. EVENT 실행 한도(분당 20건)에 주의한다. */
    static final boolean STATE_CHANGE_ONLY = Boolean.parseBoolean(env("POC_STATE_CHANGE_ONLY", "true"));
    /** 공개키 형식 미확정 등으로 서명 검증이 실패하는 동안에도 event 양상을 보려면 true로 둔다. */
    static final boolean INCLUDE_UNVERIFIED = Boolean.parseBoolean(env("POC_REPORT_INCLUDE_UNVERIFIED", "false"));

    /** 운영 lifecycle 판정에 쓰는 capability/attribute. polling 기록과 비교 리포트 대상이다. */
    static final Map<String, List<String>> TRACKED = new LinkedHashMap<>();
    static {
        TRACKED.put("washerOperatingState", List.of("machineState", "washerJobState", "completionTime"));
        TRACKED.put("dryerOperatingState", List.of("machineState", "dryerJobState", "completionTime"));
        TRACKED.put("switch", List.of("switch"));
        TRACKED.put("remoteControlStatus", List.of("remoteControlEnabled"));
    }

    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            usage();
            return;
        }
        Files.createDirectories(DATA_DIR);
        var rest = List.of(args).subList(1, args.length);
        switch (args[0]) {
            case "authorize-url" -> authorizeUrl(rest);
            case "exchange" -> exchange(rest);
            case "refresh" -> refresh();
            case "installed-app" -> installedApp(rest);
            case "subscribe-device" -> subscribeDevice(rest);
            case "subscribe-capability" -> subscribeCapability(rest);
            case "list-subscriptions" -> listSubscriptions(rest);
            case "delete-subscription" -> deleteSubscription(rest);
            case "serve" -> serve(rest);
            case "poll" -> poll(rest);
            case "report" -> report();
            default -> usage();
        }
    }

    static void usage() {
        System.out.println("""
                사용법: java SmartThingsWebhookPoc.java <command> [args]

                [OAuth / Installed App]  환경변수: SMARTTHINGS_CLIENT_ID, SMARTTHINGS_CLIENT_SECRET, SMARTTHINGS_REDIRECT_URI
                  authorize-url <scope...>                 인가 URL 출력 (예: r:devices:* x:devices:* r:locations:*)
                  exchange <code>                          code 교환. token은 poc-data/token.json에만 저장, installed_app_id만 출력
                  refresh                                  PoC token 갱신
                  installed-app [installedAppId]           Installed App 조회 후 locationId 출력

                [Subscription]
                  subscribe-device <deviceId> <capability|*> [attribute|*] [name]
                  subscribe-capability <locationId> <capability> [attribute|*] [name]
                  list-subscriptions [installedAppId]
                  delete-subscription <subscriptionId|all> [installedAppId]

                [측정]
                  serve [port]                             webhook 수신 (기본 8090). 공개 HTTPS 터널을 앞에 둔다
                  poll <intervalSec> <deviceId...>         polling snapshot 기록 (운영과 같은 /status 조회)
                  report                                   events.jsonl과 polls.jsonl 비교 리포트 출력
                """);
    }

    // ------------------------------------------------------------------ OAuth / Installed App

    static void authorizeUrl(List<String> scopes) {
        var scope = scopes.isEmpty() ? "r:devices:* x:devices:*" : String.join(" ", scopes);
        System.out.println(env("SMARTTHINGS_AUTHORIZE_URL", "https://api.smartthings.com/oauth/authorize")
                + "?client_id=" + enc(requireEnv("SMARTTHINGS_CLIENT_ID")) + "&response_type=code&redirect_uri="
                + enc(requireEnv("SMARTTHINGS_REDIRECT_URI")) + "&scope=" + enc(scope));
    }

    static void exchange(List<String> args) throws Exception {
        if (args.isEmpty()) {
            throw new IllegalArgumentException("authorization code가 필요합니다.");
        }
        var form = "grant_type=authorization_code&code=" + enc(args.getFirst()) + "&client_id="
                + enc(requireEnv("SMARTTHINGS_CLIENT_ID")) + "&redirect_uri="
                + enc(requireEnv("SMARTTHINGS_REDIRECT_URI"));
        var response = postToken(form);
        saveToken(response, response);
    }

    static void refresh() throws Exception {
        var current = readToken();
        var refreshToken = Json.str(current, "refresh_token");
        var response = postToken("grant_type=refresh_token&client_id=" + enc(requireEnv("SMARTTHINGS_CLIENT_ID"))
                + "&refresh_token=" + enc(refreshToken));
        saveToken(mergeToken(current, response), response);
    }

    /**
     * 갱신 응답은 refresh_token이나 installed_app_id를 생략할 수 있으므로 기존 token 위에 응답에 값이 있는 필드만 덮어쓴다.
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> mergeToken(Object current, Object response) {
        var merged = new LinkedHashMap<>((Map<String, Object>) current);
        ((Map<String, Object>) response).forEach((key, value) -> {
            if (value != null) {
                merged.put(key, value);
            }
        });
        return merged;
    }

    static Object postToken(String form) throws Exception {
        var basic = Base64.getEncoder().encodeToString(
                (requireEnv("SMARTTHINGS_CLIENT_ID") + ":" + requireEnv("SMARTTHINGS_CLIENT_SECRET"))
                        .getBytes(StandardCharsets.UTF_8));
        var request = HttpRequest.newBuilder(URI.create(OAUTH_TOKEN_URL)).timeout(Duration.ofSeconds(15))
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build();
        var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            // 오류 본문에도 token이 포함될 수 있으므로 상태 코드만 출력한다.
            throw new IllegalStateException("token 요청 실패 status=" + response.statusCode());
        }
        return Json.parse(response.body());
    }

    @SuppressWarnings("unchecked")
    static void saveToken(Object token, Object response) throws IOException {
        var map = new LinkedHashMap<>((Map<String, Object>) token);
        map.put("saved_at", Instant.now().toString());
        writeOwnerOnly(TOKEN_FILE, Json.write(map));
        // 기록 가능한 식별자만 출력한다. access/refresh token 원문은 출력하지 않는다.
        var keys = new ArrayList<>(((Map<String, Object>) response).keySet());
        System.out.println("token 저장 완료 file=" + TOKEN_FILE);
        System.out.println("응답 필드 = " + keys);
        System.out.println("installed_app_id = " + map.get("installed_app_id"));
        System.out.println("scope = " + map.get("scope"));
        System.out.println("expires_in = " + map.get("expires_in"));
    }

    static Object readToken() throws IOException {
        if (!Files.exists(TOKEN_FILE)) {
            throw new IllegalStateException("poc-data/token.json이 없습니다. exchange를 먼저 실행하세요.");
        }
        return Json.parse(Files.readString(TOKEN_FILE));
    }

    static String installedAppId(List<String> args, int index) throws IOException {
        if (args.size() > index) {
            return args.get(index);
        }
        var id = Json.str(readToken(), "installed_app_id");
        if (id == null) {
            throw new IllegalStateException("token 응답에 installed_app_id가 없습니다. 인자로 직접 지정하세요.");
        }
        return id;
    }

    static void installedApp(List<String> args) throws Exception {
        var id = installedAppId(args, 0);
        var body = api("GET", "/v1/installedapps/" + id, null);
        System.out.println("installedAppId = " + Json.str(body, "installedAppId"));
        System.out.println("installedAppType = " + Json.str(body, "installedAppType"));
        System.out.println("installedAppStatus = " + Json.str(body, "installedAppStatus"));
        System.out.println("appId = " + Json.str(body, "appId"));
        System.out.println("locationId = " + Json.str(body, "locationId"));
    }

    // ------------------------------------------------------------------ Subscription

    static void subscribeDevice(List<String> args) throws Exception {
        if (args.size() < 2) {
            throw new IllegalArgumentException("subscribe-device <deviceId> <capability|*> [attribute|*] [name]");
        }
        var device = new LinkedHashMap<String, Object>();
        device.put("deviceId", args.get(0));
        device.put("componentId", "main");
        device.put("capability", args.get(1));
        device.put("attribute", args.size() > 2 ? args.get(2) : "*");
        device.put("stateChangeOnly", STATE_CHANGE_ONLY);
        device.put("subscriptionName", args.size() > 3 ? args.get(3) : subscriptionName("dev", args.get(1)));
        device.put("value", "*");
        createSubscription(Map.of("sourceType", "DEVICE", "device", device));
    }

    static void subscribeCapability(List<String> args) throws Exception {
        if (args.size() < 2) {
            throw new IllegalArgumentException(
                    "subscribe-capability <locationId> <capability> [attribute|*] [name]");
        }
        var capability = new LinkedHashMap<String, Object>();
        capability.put("locationId", args.get(0));
        capability.put("capability", args.get(1));
        capability.put("attribute", args.size() > 2 ? args.get(2) : "*");
        capability.put("value", "*");
        capability.put("stateChangeOnly", STATE_CHANGE_ONLY);
        capability.put("subscriptionName", args.size() > 3 ? args.get(3) : subscriptionName("cap", args.get(1)));
        createSubscription(Map.of("sourceType", "CAPABILITY", "capability", capability));
    }

    static String subscriptionName(String prefix, String capability) {
        // subscriptionName은 길이 제한이 있어 짧게 만든다.
        var name = prefix + "_" + capability.replace("*", "all") + "_" + (System.currentTimeMillis() % 100000);
        return name.length() > 36 ? name.substring(0, 36) : name;
    }

    static void createSubscription(Map<String, Object> body) throws Exception {
        var id = installedAppId(List.of(), 0);
        var response = api("POST", "/v1/installedapps/" + id + "/subscriptions", Json.write(body));
        System.out.println(Json.write(response));
    }

    static void listSubscriptions(List<String> args) throws Exception {
        var id = installedAppId(args, 0);
        var response = api("GET", "/v1/installedapps/" + id + "/subscriptions", null);
        var items = Json.list(response, "items");
        System.out.println("subscription 수 = " + items.size() + " (guardrail 40)");
        for (var item : items) {
            System.out.println(Json.write(item));
        }
    }

    static void deleteSubscription(List<String> args) throws Exception {
        if (args.isEmpty()) {
            throw new IllegalArgumentException("delete-subscription <subscriptionId|all> [installedAppId]");
        }
        var id = installedAppId(args, 1);
        var path = "/v1/installedapps/" + id + "/subscriptions"
                + ("all".equals(args.getFirst()) ? "" : "/" + args.getFirst());
        System.out.println(Json.write(api("DELETE", path, null)));
    }

    static Object api(String method, String path, String body) throws Exception {
        var accessToken = Json.str(readToken(), "access_token");
        var builder = HttpRequest.newBuilder(URI.create(API_URL + path)).timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + accessToken).header("Accept", "application/json");
        if (body != null) {
            builder.header("Content-Type", "application/json;charset=utf-8")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        var response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException(
                    "SmartThings API 실패 status=" + response.statusCode() + " path=" + path + " body="
                            + response.body());
        }
        return response.body().isBlank() ? Map.of() : Json.parse(response.body());
    }

    // ------------------------------------------------------------------ Webhook 수신

    record CachedKey(PublicKey key, Instant fetchedAt) {
    }

    static final Map<String, CachedKey> KEY_CACHE = new ConcurrentHashMap<>();

    static void serve(List<String> args) throws IOException {
        var port = args.isEmpty() ? 8090 : Integer.parseInt(args.getFirst());
        var server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", SmartThingsWebhookPoc::handle);
        // 기본 executor는 단일 스레드라 공개키 조회 지연이 뒤 요청의 수신 지연으로 측정되므로 요청마다 virtual thread로 처리한다.
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        System.out.println("webhook 수신 대기 port=" + port + " data=" + DATA_DIR.toAbsolutePath());
    }

    static void handle(HttpExchange exchange) throws IOException {
        var receivedAt = Instant.now();
        try (exchange) {
            if (!"POST".equals(exchange.getRequestMethod())) {
                respond(exchange, 405, "{}");
                return;
            }
            var raw = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
            if (raw.length > MAX_BODY_BYTES) {
                record(REQUESTS_FILE, Map.of("receivedAt", receivedAt.toString(), "result", "BODY_TOO_LARGE"));
                respond(exchange, 413, "{}");
                return;
            }
            var verification = verify(exchange, raw, receivedAt);
            var body = Json.parse(new String(raw, StandardCharsets.UTF_8));
            var messageType = Json.str(body, "messageType");

            var summary = new LinkedHashMap<String, Object>();
            summary.put("receivedAt", receivedAt.toString());
            summary.put("path", exchange.getRequestURI().getRawPath());
            summary.put("messageType", messageType);
            summary.put("signature", verification);
            summary.put("contentLength", raw.length);
            record(REQUESTS_FILE, summary);
            System.out.println("[" + receivedAt + "] " + messageType + " signature=" + verification);

            if (!"VALID".equals(verification) && Boolean.parseBoolean(env("POC_ENFORCE_SIGNATURE", "false"))) {
                respond(exchange, 401, "{}");
                return;
            }
            switch (messageType == null ? "" : messageType) {
                case "CONFIRMATION" -> handleConfirmation(body);
                case "EVENT" -> handleEvent(body, receivedAt, verification);
                default -> System.out.println("  처리하지 않는 messageType, 요약만 기록");
            }
            respond(exchange, 200, "{}");
        } catch (Exception e) {
            System.out.println("[" + receivedAt + "] 처리 실패 " + e);
            record(REQUESTS_FILE, Map.of("receivedAt", receivedAt.toString(), "result", "ERROR",
                    "error", e.getClass().getSimpleName()));
            respond(exchange, 500, "{}");
        }
    }

    static void handleConfirmation(Object body) throws Exception {
        var url = Json.str(body, "confirmationData.confirmationUrl");
        var appId = Json.str(body, "confirmationData.appId");
        if (url == null) {
            System.out.println("  CONFIRMATION appId=" + appId + " confirmationUrl 없음");
            return;
        }
        // confirmationUrl은 호출만으로 Target URL 확인이 끝나는 일회성 권한 URL이므로 query를 가려 출력하고 원문은 소유자 전용 파일에만 둔다.
        var uri = URI.create(url);
        var host = uri.getHost() == null ? "" : uri.getHost();
        System.out.println("  CONFIRMATION appId=" + appId + " confirmationUrl=" + uri.getScheme() + "://" + host
                + uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?***"));
        if (!"https".equals(uri.getScheme()) || CONFIRMATION_HOST_SUFFIXES.stream().noneMatch(host::endsWith)) {
            System.out.println("  허용되지 않은 confirmationUrl host라 호출·저장하지 않음 host=" + host);
            return;
        }
        writeOwnerOnly(CONFIRMATION_URL_FILE, url + "\n");
        System.out.println("  원문 저장 file=" + CONFIRMATION_URL_FILE);
        if (!Boolean.parseBoolean(env("POC_AUTO_CONFIRM", "false"))) {
            System.out.println("  POC_AUTO_CONFIRM=false: 원문 파일의 URL을 직접 GET 하세요");
            return;
        }
        var response = HTTP.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.discarding());
        System.out.println("  confirmationUrl 호출 결과 status=" + response.statusCode());
    }

    static void handleEvent(Object body, Instant receivedAt, String verification) throws IOException {
        for (var event : Json.list(body, "eventData.events")) {
            var eventType = Json.str(event, "eventType");
            if (!"DEVICE_EVENT".equals(eventType)) {
                var lifecycle = String.valueOf(Json.str(event, "installedAppLifecycleEvent.lifecycle"));
                record(EVENTS_FILE, Map.of("receivedAt", receivedAt.toString(), "source", "webhook",
                        "eventType", String.valueOf(eventType), "lifecycle", lifecycle));
                System.out.println("  " + eventType + " lifecycle=" + lifecycle);
                continue;
            }
            var record = new LinkedHashMap<String, Object>();
            record.put("receivedAt", receivedAt.toString());
            record.put("source", "webhook");
            record.put("eventType", eventType);
            record.put("eventTime", Json.str(event, "eventTime"));
            for (var key : List.of("eventId", "subscriptionName", "locationId", "deviceId", "componentId",
                    "capability", "attribute", "value", "valueType", "stateChange")) {
                record.put(key, Json.at(event, "deviceEvent." + key));
            }
            record.put("signature", verification);
            record(EVENTS_FILE, record);
            System.out.println("  " + record.get("deviceId") + " " + record.get("capability") + "."
                    + record.get("attribute") + "=" + record.get("value") + " eventTime=" + record.get("eventTime"));
        }
    }

    // ------------------------------------------------------------------ HTTP Signature 검증

    static final Pattern SIGNATURE_PARAM = Pattern.compile("(\\w+)=\"([^\"]*)\"");

    /** 검증 결과를 문자열 코드로 반환한다. 실패 원인을 비교 기록하기 위해 예외 대신 코드를 쓴다. */
    static String verify(HttpExchange exchange, byte[] raw, Instant receivedAt) {
        try {
            var headers = exchange.getRequestHeaders();
            var authorization = headers.getFirst("Authorization");
            if (authorization == null || !authorization.startsWith("Signature ")) {
                return "MISSING_SIGNATURE";
            }
            var params = new HashMap<String, String>();
            var matcher = SIGNATURE_PARAM.matcher(authorization.substring("Signature ".length()));
            while (matcher.find()) {
                params.put(matcher.group(1), matcher.group(2));
            }
            if (!"rsa-sha256".equalsIgnoreCase(params.getOrDefault("algorithm", "rsa-sha256"))) {
                return "UNSUPPORTED_ALGORITHM";
            }

            // 공식 예시는 "SHA256=", RFC 3230 표기는 "SHA-256="이므로 알고리즘 접두사는 두 표기를 모두 허용한다.
            var digestHeader = headers.getFirst("Digest");
            if (digestHeader == null || digestHeader.indexOf('=') < 0) {
                return "MISSING_DIGEST";
            }
            var digestAlgorithm = digestHeader.substring(0, digestHeader.indexOf('=')).replace("-", "");
            if (!"SHA256".equalsIgnoreCase(digestAlgorithm)) {
                return "UNSUPPORTED_DIGEST_" + digestAlgorithm;
            }
            var expectedDigest = MessageDigest.getInstance("SHA-256").digest(raw);
            var actualDigest = Base64.getDecoder().decode(digestHeader.substring(digestHeader.indexOf('=') + 1));
            if (!MessageDigest.isEqual(expectedDigest, actualDigest)) {
                return "DIGEST_MISMATCH";
            }

            var date = headers.getFirst("Date");
            if (date == null) {
                return "MISSING_DATE";
            }
            // 공식 예시가 "... UTC" 표기를 쓰므로 RFC 1123의 GMT로 바꿔 파싱한다.
            var sent = ZonedDateTime.parse(date.replaceFirst(" UTC$", " GMT"), DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant();
            if (Duration.between(sent, receivedAt).abs().compareTo(MAX_DATE_SKEW) > 0) {
                return "DATE_SKEW";
            }

            var signed = params.getOrDefault("headers", "date").split(" ");
            var signingString = new StringBuilder();
            for (var name : signed) {
                if (!signingString.isEmpty()) {
                    signingString.append('\n');
                }
                if ("(request-target)".equals(name)) {
                    var uri = exchange.getRequestURI();
                    var target = uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
                    signingString.append("(request-target): ").append(exchange.getRequestMethod().toLowerCase())
                            .append(' ').append(target);
                } else {
                    var value = headers.getFirst(name);
                    if (value == null) {
                        return "MISSING_SIGNED_HEADER_" + name;
                    }
                    signingString.append(name.toLowerCase()).append(": ").append(value);
                }
            }
            if (!List.of(signed).contains("digest")) {
                return "DIGEST_NOT_SIGNED";
            }

            var keyId = params.get("keyId");
            if (keyId == null || !keyId.startsWith("/")) {
                // 절대 URL keyId는 임의 host 조회(SSRF)를 유발하므로 허용하지 않는다.
                return "KEY_ID_NOT_ALLOWED";
            }
            var verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey(keyId));
            verifier.update(signingString.toString().getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(params.get("signature")))
                    ? "VALID"
                    : "SIGNATURE_MISMATCH";
        } catch (Exception e) {
            return "VERIFY_ERROR_" + e.getClass().getSimpleName();
        }
    }

    /**
     * keyId로 공개키를 조회한다. API Access App 문서에는 key host·경로·형식이 명시되어 있지 않아 기존 SmartApp 방식({@code host + keyId})과
     * Enterprise 문서 방식({@code host + /key + keyId})을 차례로 시도하고, 어느 쪽이 성공했는지 출력해 PoC 결과로 남긴다.
     */
    static PublicKey publicKey(String keyId) throws Exception {
        var cached = KEY_CACHE.get(keyId);
        if (cached != null && cached.fetchedAt().plus(KEY_CACHE_TTL).isAfter(Instant.now())) {
            return cached.key();
        }
        Exception lastError = null;
        for (var url : List.of(KEY_BASE_URL + keyId, KEY_BASE_URL + "/key" + keyId)) {
            try {
                var response = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                        .GET().build(), HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() != 200) {
                    throw new IllegalStateException("공개키 조회 실패 status=" + response.statusCode() + " url=" + url);
                }
                var key = parsePublicKey(response.body());
                System.out.println("  공개키 조회 성공 url=" + url + " format=" + key.getFormat());
                KEY_CACHE.put(keyId, new CachedKey(key, Instant.now()));
                return key;
            } catch (Exception e) {
                System.out.println("  " + e.getMessage());
                lastError = e;
            }
        }
        throw lastError;
    }

    /** X.509 인증서(PEM/DER)와 PEM 공개키(SubjectPublicKeyInfo)를 모두 받는다. */
    static PublicKey parsePublicKey(byte[] body) throws Exception {
        var text = new String(body, StandardCharsets.US_ASCII);
        if (text.contains("BEGIN PUBLIC KEY")) {
            var base64 = text.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
            return KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
        }
        return CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(body))
                .getPublicKey();
    }

    // ------------------------------------------------------------------ Polling 기록

    static void poll(List<String> args) throws Exception {
        if (args.size() < 2) {
            throw new IllegalArgumentException("poll <intervalSec> <deviceId...>");
        }
        var interval = Duration.ofSeconds(Long.parseLong(args.getFirst()));
        var deviceIds = args.subList(1, args.size());
        System.out.println("polling 시작 interval=" + interval.toSeconds() + "s devices=" + deviceIds);
        while (true) {
            for (var deviceId : deviceIds) {
                var polledAt = Instant.now();
                try {
                    var status = api("GET", "/v1/devices/" + deviceId + "/status", null);
                    var line = new StringBuilder();
                    for (var capability : TRACKED.entrySet()) {
                        for (var attribute : capability.getValue()) {
                            var base = "components.main." + capability.getKey() + "." + attribute;
                            if (Json.at(status, base) == null) {
                                continue;
                            }
                            var record = new LinkedHashMap<String, Object>();
                            record.put("polledAt", polledAt.toString());
                            record.put("source", "poll");
                            record.put("deviceId", deviceId);
                            record.put("componentId", "main");
                            record.put("capability", capability.getKey());
                            record.put("attribute", attribute);
                            record.put("value", Json.at(status, base + ".value"));
                            record.put("timestamp", Json.str(status, base + ".timestamp"));
                            record(POLLS_FILE, record);
                            line.append(' ').append(attribute).append('=').append(record.get("value"));
                        }
                    }
                    System.out.println("[" + polledAt + "] " + deviceId + line);
                } catch (Exception e) {
                    System.out.println("[" + polledAt + "] " + deviceId + " 조회 실패 " + e.getMessage());
                }
            }
            Thread.sleep(interval);
        }
    }

    // ------------------------------------------------------------------ 비교 리포트

    record Obs(Instant at, Instant eventTime, String key, String deviceId, String value, String eventId,
            String sourceTimestamp) {
    }

    static void report() throws IOException {
        var events = readObs(EVENTS_FILE, true);
        var polls = readObs(POLLS_FILE, false);
        var keys = new TreeMap<String, Boolean>();
        events.forEach(o -> keys.put(o.key(), true));
        polls.forEach(o -> keys.put(o.key(), true));

        System.out.println("# SmartThings webhook PoC 리포트");
        System.out.println("webhook DEVICE_EVENT " + events.size() + "건, polling 관측 " + polls.size() + "건");
        System.out.println();
        reportRequests();
        reportFinish(events, polls);

        System.out.println("## attribute별 비교");
        System.out.println("| device.capability.attribute | webhook 수신 | 중복 eventId | 순서 역전 | 지연 p50/p95/max(초) |"
                + " webhook에만 있는 값 | polling에만 있는 값 | webhook 누락 의심 변화 |");
        System.out.println("|---|---|---|---|---|---|---|---|");
        for (var key : keys.keySet()) {
            var ev = events.stream().filter(o -> o.key().equals(key)).toList();
            var po = polls.stream().filter(o -> o.key().equals(key)).toList();
            System.out.println("| " + key + " | " + ev.size() + " | " + duplicates(ev) + " | " + outOfOrder(ev)
                    + " | " + delays(ev) + " | " + onlyIn(ev, po) + " | " + onlyIn(po, ev) + " | "
                    + missedByWebhook(ev, po) + " |");
        }
        System.out.println();
        System.out.println("## 기기별 시간순 타임라인");
        var timeline = new ArrayList<Obs>();
        timeline.addAll(events);
        timeline.addAll(pollChanges(polls));
        timeline.sort(Comparator.comparing(Obs::at));
        for (var o : timeline) {
            var source = o.eventId() != null ? "WEBHOOK" : "POLL   ";
            System.out.println(o.at() + " " + source + " " + o.key() + " = " + o.value()
                    + (o.eventTime() != null ? " (eventTime " + o.eventTime() + ")" : "")
                    + (o.sourceTimestamp() != null ? " (attr ts " + o.sourceTimestamp() + ")" : ""));
        }
    }

    static void reportRequests() throws IOException {
        if (!Files.exists(REQUESTS_FILE)) {
            return;
        }
        var counts = new TreeMap<String, Integer>();
        var eventsPerMinute = new TreeMap<String, Integer>();
        for (var line : Files.readAllLines(REQUESTS_FILE)) {
            var request = Json.parse(line);
            counts.merge(Json.str(request, "messageType") + " / " + Json.str(request, "signature"), 1, Integer::sum);
            if ("EVENT".equals(Json.str(request, "messageType"))) {
                eventsPerMinute.merge(Json.str(request, "receivedAt").substring(0, 16), 1, Integer::sum);
            }
        }
        System.out.println("## 수신 요청 (messageType / 서명 검증 결과)");
        counts.forEach((k, v) -> System.out.println("- " + k + ": " + v + "건"));
        // 공식 rate limit: EVENT 실행은 installed app당 분당 20건이다.
        var peak = eventsPerMinute.entrySet().stream().max(Map.Entry.comparingByValue());
        peak.ifPresent(e -> System.out.println(
                "- 분당 최대 EVENT 요청 " + e.getValue() + "건 (" + e.getKey() + "Z, 한도 20건/분/installed app)"));
        System.out.println();
    }

    static void reportFinish(List<Obs> events, List<Obs> polls) {
        System.out.println("## 정상 완료 신호(finish/finished) 관측");
        for (var attr : List.of("washerOperatingState.washerJobState=finish",
                "dryerOperatingState.dryerJobState=finished")) {
            var suffix = attr.substring(0, attr.indexOf('='));
            var value = attr.substring(attr.indexOf('=') + 1);
            var byWebhook = events.stream().filter(o -> o.key().endsWith(suffix) && value.equals(o.value()))
                    .count();
            var byPoll = polls.stream().filter(o -> o.key().endsWith(suffix) && value.equals(o.value())).count();
            System.out.println("- " + attr + ": webhook " + byWebhook + "건, polling " + byPoll + "회 관측");
        }
        System.out.println();
    }

    static List<Obs> readObs(Path file, boolean webhook) throws IOException {
        if (!Files.exists(file)) {
            return List.of();
        }
        var result = new ArrayList<Obs>();
        for (var line : Files.readAllLines(file)) {
            var o = Json.parse(line);
            if (webhook && !"DEVICE_EVENT".equals(Json.str(o, "eventType"))) {
                continue;
            }
            // 서명 검증에 실패한 요청은 위조 가능성이 있으므로 기본적으로 비교 대상에서 뺀다.
            if (webhook && !INCLUDE_UNVERIFIED && !"VALID".equals(Json.str(o, "signature"))) {
                continue;
            }
            var key = Json.str(o, "deviceId") + "." + Json.str(o, "capability") + "." + Json.str(o, "attribute");
            var eventTime = Json.str(o, "eventTime");
            result.add(new Obs(Instant.parse(Json.str(o, webhook ? "receivedAt" : "polledAt")),
                    eventTime == null ? null : Instant.parse(eventTime), key, Json.str(o, "deviceId"),
                    Json.str(o, "value"), webhook ? Json.str(o, "eventId") : null, Json.str(o, "timestamp")));
        }
        return result;
    }

    static long duplicates(List<Obs> events) {
        return events.size() - events.stream().map(Obs::eventId).distinct().count();
    }

    /** 수신 순서상 이전 event보다 eventTime이 과거인 event 수. */
    static int outOfOrder(List<Obs> events) {
        var count = 0;
        Instant latest = null;
        for (var o : events) {
            if (o.eventTime() == null) {
                continue;
            }
            if (latest != null && o.eventTime().isBefore(latest)) {
                count++;
            } else {
                latest = o.eventTime();
            }
        }
        return count;
    }

    static String delays(List<Obs> events) {
        var seconds = events.stream().filter(o -> o.eventTime() != null)
                .map(o -> Duration.between(o.eventTime(), o.at()).toMillis() / 1000.0).sorted().toList();
        if (seconds.isEmpty()) {
            return "eventTime 없음";
        }
        return pct(seconds, 0.5) + " / " + pct(seconds, 0.95) + " / " + seconds.getLast();
    }

    static double pct(List<Double> sorted, double p) {
        return sorted.get((int) Math.min(sorted.size() - 1, Math.floor(p * sorted.size())));
    }

    static String onlyIn(List<Obs> a, List<Obs> b) {
        Set<String> values = new LinkedHashSet<>();
        a.forEach(o -> values.add(o.value()));
        b.forEach(o -> values.remove(o.value()));
        return values.isEmpty() ? "-" : String.join(", ", values.stream().map(String::valueOf).toList());
    }

    /** polling에서 값이 바뀌었는데 직전 polling 이후 같은 값의 webhook event가 없는 변화 수. */
    static int missedByWebhook(List<Obs> events, List<Obs> polls) {
        var missed = 0;
        for (var change : pollChanges(polls)) {
            var matched = events.stream().anyMatch(e -> String.valueOf(e.value()).equals(change.value())
                    && !e.at().isBefore(change.at().minus(Duration.ofMinutes(10)))
                    && !e.at().isAfter(change.at().plus(Duration.ofMinutes(2))));
            if (!matched) {
                missed++;
            }
        }
        return missed;
    }

    /** 연속 polling 사이에 값이 바뀐 지점만 남긴다. 첫 관측은 변화로 보지 않는다. */
    static List<Obs> pollChanges(List<Obs> polls) {
        var last = new HashMap<String, String>();
        var changes = new ArrayList<Obs>();
        for (var o : polls) {
            var previous = last.put(o.key(), String.valueOf(o.value()));
            if (previous != null && !previous.equals(String.valueOf(o.value()))) {
                changes.add(o);
            }
        }
        return changes;
    }

    // ------------------------------------------------------------------ 공통

    /**
     * 자격 증명 파일을 소유자만 읽고 쓸 수 있게 만든 뒤 내용을 쓴다. umask와 무관하게 내용이 쓰이기 전에 권한을 제한하고, 기존 파일을 덮어쓸 때도
     * 권한을 다시 적용한다.
     */
    static void writeOwnerOnly(Path file, String content) throws IOException {
        restrictToOwner(DATA_DIR, true);
        if (!Files.exists(file)) {
            Files.createFile(file);
        }
        restrictToOwner(file, false);
        Files.writeString(file, content, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    /** POSIX는 700/600으로, Windows는 소유자 ACL 항목 하나만 남겨 다른 사용자의 접근을 막는다. */
    static void restrictToOwner(Path path, boolean directory) throws IOException {
        var posix = Files.getFileAttributeView(path, PosixFileAttributeView.class);
        if (posix != null) {
            posix.setPermissions(PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"));
            return;
        }
        var acl = Files.getFileAttributeView(path, AclFileAttributeView.class);
        if (acl == null) {
            throw new IOException("파일 권한을 소유자 전용으로 제한할 수 없는 파일 시스템입니다. path=" + path);
        }
        var flags = directory
                ? EnumSet.of(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT)
                : EnumSet.noneOf(AclEntryFlag.class);
        acl.setAcl(List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(Files.getOwner(path))
                .setPermissions(EnumSet.allOf(AclEntryPermission.class)).setFlags(flags).build()));
    }

    static synchronized void record(Path file, Map<String, ?> record) throws IOException {
        Files.writeString(file, Json.write(record) + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    static void respond(HttpExchange exchange, int status, String body) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    static String env(String key, String fallback) {
        var value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    static String requireEnv(String key) {
        var value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("환경변수 " + key + "가 필요합니다.");
        }
        return value;
    }

    static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
