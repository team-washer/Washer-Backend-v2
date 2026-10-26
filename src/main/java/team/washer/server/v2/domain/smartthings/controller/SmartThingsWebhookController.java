package team.washer.server.v2.domain.smartthings.controller;

import java.io.IOException;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.smartthings.service.HandleSmartThingsWebhookService;
import team.washer.server.v2.domain.smartthings.support.SmartThingsWebhookSignatureVerifier;
import team.washer.server.v2.global.config.swagger.CommonErrorResponses;
import team.washer.server.v2.global.thirdparty.smartthings.config.SmartThingsWebhookEnvironment;

/**
 * SmartThings webhook 수신 엔드포인트
 *
 * <p>
 * JWT 대신 SmartThings HTTP Signature로 인증한다. SDK 응답 래핑과 body 로깅에서 제외해 SmartThings
 * 응답 계약과 민감정보 정책을 지킨다.
 * </p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/events/smartthings")
@Tag(name = "SmartThings Event", description = "SmartThings webhook 수신 API")
@CommonErrorResponses
public class SmartThingsWebhookController {

    private final SmartThingsWebhookEnvironment environment;
    private final SmartThingsWebhookSignatureVerifier signatureVerifier;
    private final HandleSmartThingsWebhookService handleSmartThingsWebhookService;

    @PostMapping
    @Operation(summary = "SmartThings webhook 수신", description = "SmartThings가 보내는 등록 확인과 기기 이벤트를 HTTP Signature로 검증한 뒤 수신함에 저장합니다. 비활성화 상태에서는 404를 반환합니다.")
    public ResponseEntity<Void> receiveWebhook(final HttpServletRequest request) throws IOException {
        if (!environment.enabled()) {
            throw new ExpectedException("SmartThings webhook 수신이 비활성화되어 있습니다.", HttpStatus.NOT_FOUND);
        }

        final var receivedAt = Instant.now();
        final var rawBody = readBody(request);
        final var verification = signatureVerifier.verify(request, rawBody, receivedAt);
        if (!verification.isValid()) {
            log.warn("SmartThings webhook rejected reason={}", verification);
            throw new ExpectedException("SmartThings webhook 서명이 유효하지 않습니다.", HttpStatus.UNAUTHORIZED);
        }

        handleSmartThingsWebhookService.execute(rawBody);
        return ResponseEntity.ok().build();
    }

    /** 본문을 최대 크기 + 1 바이트까지만 읽어 큰 요청이 메모리를 점유하지 못하게 한다. */
    private byte[] readBody(final HttpServletRequest request) throws IOException {
        final var maxBodyBytes = environment.maxBodyBytes();
        if (request.getContentLengthLong() > maxBodyBytes) {
            throw new ExpectedException("SmartThings webhook 요청 본문이 너무 큽니다.", HttpStatus.CONTENT_TOO_LARGE);
        }
        final var rawBody = request.getInputStream().readNBytes(maxBodyBytes + 1);
        if (rawBody.length > maxBodyBytes) {
            throw new ExpectedException("SmartThings webhook 요청 본문이 너무 큽니다.", HttpStatus.CONTENT_TOO_LARGE);
        }
        return rawBody;
    }
}
