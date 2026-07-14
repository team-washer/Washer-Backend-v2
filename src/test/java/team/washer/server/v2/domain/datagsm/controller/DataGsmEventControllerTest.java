package team.washer.server.v2.domain.datagsm.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.datagsm.service.HandleDataGsmEventService;
import team.washer.server.v2.domain.datagsm.support.DataGsmEventSignatureVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("DataGsmEventController 클래스는")
class DataGsmEventControllerTest {

    @InjectMocks
    private DataGsmEventController dataGsmEventController;

    @Mock
    private DataGsmEventSignatureVerifier signatureVerifier;

    @Mock
    private HandleDataGsmEventService handleDataGsmEventService;

    @Nested
    @DisplayName("receiveEvent 메서드는")
    class Describe_receive_event {

        @Test
        @DisplayName("서명이 유효하면 이벤트 처리를 위임한다")
        void it_delegates_event_handling_when_signature_is_valid() {
            // Given
            final byte[] rawBody = "{\"id\":\"evt_1\"}".getBytes(StandardCharsets.UTF_8);
            given(signatureVerifier.verify("sha256=valid", rawBody)).willReturn(true);

            // When
            dataGsmEventController.receiveEvent("sha256=valid", rawBody);

            // Then
            then(handleDataGsmEventService).should(times(1)).execute(rawBody);
        }

        @Test
        @DisplayName("서명이 유효하지 않으면 인증 예외를 발생시킨다")
        void it_throws_exception_when_signature_is_invalid() {
            // Given
            final byte[] rawBody = "{\"id\":\"evt_1\"}".getBytes(StandardCharsets.UTF_8);
            given(signatureVerifier.verify("sha256=invalid", rawBody)).willReturn(false);

            // When & Then
            assertThatThrownBy(() -> dataGsmEventController.receiveEvent("sha256=invalid", rawBody))
                    .isInstanceOfSatisfying(ExpectedException.class,
                            exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
            then(handleDataGsmEventService).shouldHaveNoInteractions();
        }
    }
}
