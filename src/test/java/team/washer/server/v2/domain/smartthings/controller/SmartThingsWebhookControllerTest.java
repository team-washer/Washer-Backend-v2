package team.washer.server.v2.domain.smartthings.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.smartthings.enums.SmartThingsWebhookVerificationResult;
import team.washer.server.v2.domain.smartthings.service.HandleSmartThingsWebhookService;
import team.washer.server.v2.domain.smartthings.support.SmartThingsWebhookSignatureVerifier;
import team.washer.server.v2.global.thirdparty.smartthings.config.SmartThingsWebhookEnvironment;

@DisplayName("SmartThingsWebhookController 클래스는")
class SmartThingsWebhookControllerTest {

    private static final byte[] BODY = "{\"messageType\":\"EVENT\"}".getBytes(StandardCharsets.UTF_8);

    private SmartThingsWebhookSignatureVerifier signatureVerifier;
    private HandleSmartThingsWebhookService handleService;

    @BeforeEach
    void setUp() {
        signatureVerifier = mock(SmartThingsWebhookSignatureVerifier.class);
        handleService = mock(HandleSmartThingsWebhookService.class);
    }

    private SmartThingsWebhookController controller(final boolean enabled, final int maxBodyBytes) {
        return new SmartThingsWebhookController(
                new SmartThingsWebhookEnvironment(enabled, "app-1", null, maxBodyBytes, 0, 0, 0),
                signatureVerifier,
                handleService);
    }

    private static MockHttpServletRequest request(final byte[] body) {
        final var request = new MockHttpServletRequest("POST", "/api/v2/events/smartthings");
        request.setContent(body);
        return request;
    }

    private static void assertStatus(final Throwable thrown, final HttpStatus status) {
        assertThat(thrown).isInstanceOfSatisfying(ExpectedException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(status));
    }

    @Nested
    @DisplayName("receiveWebhook 메서드는")
    class Describe_receive_webhook {

        @Test
        @DisplayName("서명이 유효하면 원본 본문 처리를 위임하고 200을 반환한다")
        void it_delegates_when_signature_is_valid() throws Exception {
            // Given
            final var request = request(BODY);
            given(signatureVerifier.verify(any(), any(), any())).willReturn(SmartThingsWebhookVerificationResult.VALID);

            // When
            final var response = controller(true, 0).receiveWebhook(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            then(handleService).should().execute(BODY);
        }

        @Test
        @DisplayName("비활성화 상태면 서명 검증 없이 404 예외를 발생시킨다")
        void it_returns_not_found_when_disabled() {
            // When & Then
            assertThatThrownBy(() -> controller(false, 0).receiveWebhook(request(BODY)))
                    .satisfies(thrown -> assertStatus(thrown, HttpStatus.NOT_FOUND));
            then(signatureVerifier).shouldHaveNoInteractions();
            then(handleService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("서명이 유효하지 않으면 처리하지 않고 401 예외를 발생시킨다")
        void it_rejects_invalid_signature() {
            // Given
            given(signatureVerifier.verify(any(), any(), any()))
                    .willReturn(SmartThingsWebhookVerificationResult.DIGEST_MISMATCH);

            // When & Then
            assertThatThrownBy(() -> controller(true, 0).receiveWebhook(request(BODY)))
                    .satisfies(thrown -> assertStatus(thrown, HttpStatus.UNAUTHORIZED));
            then(handleService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("본문이 최대 크기를 넘으면 서명 검증 없이 413 예외를 발생시킨다")
        void it_rejects_too_large_body() {
            // When & Then
            assertThatThrownBy(() -> controller(true, 8).receiveWebhook(request(BODY)))
                    .satisfies(thrown -> assertStatus(thrown, HttpStatus.CONTENT_TOO_LARGE));
            then(signatureVerifier).shouldHaveNoInteractions();
            then(handleService).shouldHaveNoInteractions();
        }
    }
}
