package team.washer.server.v2.global.thirdparty.smartthings.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SmartThings webhook 수신 설정
 *
 * @param enabled
 *            webhook 수신 활성화 여부. 비활성화 상태에서는 수신 경로가 404를 반환하고 구독도 만들지 않는다
 * @param appId
 *            CONFIRMATION을 허용할 SmartThings 앱 ID
 * @param keyUrl
 *            HTTP Signature 공개키를 조회할 기준 URL
 * @param maxBodyBytes
 *            수신 요청 본문 최대 크기
 * @param maxDateSkewSeconds
 *            {@code Date} 헤더와 수신 시각의 허용 오차
 * @param keyCacheTtlMinutes
 *            공개키 캐시 유지 시간. 공식 문서 권고에 따라 24시간을 넘기지 않는다
 * @param eventRetentionDays
 *            수신 event 보존 기간
 */
@ConfigurationProperties(prefix = "third-party.smartthings.webhook")
public record SmartThingsWebhookEnvironment(boolean enabled, String appId, String keyUrl, int maxBodyBytes,
        long maxDateSkewSeconds, long keyCacheTtlMinutes, int eventRetentionDays) {

    private static final String DEFAULT_KEY_URL = "https://key.smartthings.com";
    private static final int DEFAULT_MAX_BODY_BYTES = 256 * 1024;
    private static final long DEFAULT_MAX_DATE_SKEW_SECONDS = 300;
    private static final long DEFAULT_KEY_CACHE_TTL_MINUTES = 180;
    private static final long MAX_KEY_CACHE_TTL_MINUTES = 24 * 60;
    private static final int DEFAULT_EVENT_RETENTION_DAYS = 7;

    public SmartThingsWebhookEnvironment {
        if (keyUrl == null || keyUrl.isBlank()) {
            keyUrl = DEFAULT_KEY_URL;
        }
        if (maxBodyBytes <= 0) {
            maxBodyBytes = DEFAULT_MAX_BODY_BYTES;
        }
        if (maxDateSkewSeconds <= 0) {
            maxDateSkewSeconds = DEFAULT_MAX_DATE_SKEW_SECONDS;
        }
        if (keyCacheTtlMinutes <= 0 || keyCacheTtlMinutes > MAX_KEY_CACHE_TTL_MINUTES) {
            keyCacheTtlMinutes = DEFAULT_KEY_CACHE_TTL_MINUTES;
        }
        if (eventRetentionDays <= 0) {
            eventRetentionDays = DEFAULT_EVENT_RETENTION_DAYS;
        }
    }
}
