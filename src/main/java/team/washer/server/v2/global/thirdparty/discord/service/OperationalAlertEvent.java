package team.washer.server.v2.global.thirdparty.discord.service;

import java.time.Instant;
import java.util.Map;

/**
 * 운영자가 조치할 수 있는 오류를 Discord로 전달하기 위한 안전한 요약 이벤트입니다.
 *
 * <p>
 * 예외 원문, 요청 본문, 외부 응답은 CloudWatch의 상관관계 ID로만 추적하고 이 이벤트에는 넣지 않습니다.
 * </p>
 */
record OperationalAlertEvent(String eventType, String severity, Instant occurredAt, String deploymentCommit,
        String correlationId, String operation, String errorCode, String exceptionType, Map<String, String> metadata) {
}
