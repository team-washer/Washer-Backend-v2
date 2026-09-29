package team.washer.server.v2.domain.smartthings.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import team.washer.server.v2.domain.smartthings.dto.request.SmartThingsCommandReqDto;
import team.washer.server.v2.domain.smartthings.service.impl.SendDeviceCommandServiceImpl;
import team.washer.server.v2.domain.smartthings.support.SmartThingsTokenProvider;
import team.washer.server.v2.global.common.error.code.ErrorCode;
import team.washer.server.v2.global.common.error.exception.ErrorCodeException;
import team.washer.server.v2.global.thirdparty.smartthings.feign.SmartThingsApiException;
import team.washer.server.v2.global.thirdparty.smartthings.feign.SmartThingsFeignClient;

@ExtendWith(MockitoExtension.class)
@DisplayName("SendDeviceCommandServiceImpl 클래스의")
class SendDeviceCommandServiceTest {

    @InjectMocks
    private SendDeviceCommandServiceImpl sendDeviceCommandService;

    @Mock
    private SmartThingsFeignClient feignClient;

    @Mock
    private SmartThingsTokenProvider tokenProvider;

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Nested
        @DisplayName("유효한 토큰으로 명령을 전송할 때")
        class Context_with_valid_token {

            @Test
            @DisplayName("기기에 명령을 전송해야 한다")
            void it_sends_command_to_device() {
                // Given
                var deviceId = "device-abc";
                var command = SmartThingsCommandReqDto.powerOff();

                given(tokenProvider.getValidAccessToken()).willReturn("valid-access-token");

                // When
                sendDeviceCommandService.execute(deviceId, command);

                // Then
                then(feignClient).should(times(1)).sendDeviceCommand("Bearer valid-access-token", deviceId, command);
            }
        }

        @Nested
        @DisplayName("저장된 토큰이 없을 때")
        class Context_with_no_token {

            @Test
            @DisplayName("ExpectedException이 발생하고 NOT_FOUND 상태를 반환해야 한다")
            void it_throws_not_found_exception() {
                // Given
                given(tokenProvider.getValidAccessToken())
                        .willThrow(new ErrorCodeException(ErrorCode.SMARTTHINGS_TOKEN_UNAVAILABLE));

                // When & Then
                assertThatThrownBy(
                        () -> sendDeviceCommandService.execute("device-abc", SmartThingsCommandReqDto.powerOff()))
                        .isInstanceOf(ErrorCodeException.class)
                        .satisfies(e -> assertThat(((ErrorCodeException) e).getErrorCode())
                                .isEqualTo(ErrorCode.SMARTTHINGS_TOKEN_UNAVAILABLE));

                then(feignClient).shouldHaveNoInteractions();
            }
        }

        @Nested
        @DisplayName("만료된 토큰으로 명령을 전송하려 할 때")
        class Context_with_expired_token {

            @Test
            @DisplayName("ExpectedException이 발생하고 NOT_FOUND 상태를 반환해야 한다")
            void it_throws_not_found_for_expired_token() {
                // Given
                given(tokenProvider.getValidAccessToken())
                        .willThrow(new ErrorCodeException(ErrorCode.SMARTTHINGS_TOKEN_INVALID));

                // When & Then
                assertThatThrownBy(
                        () -> sendDeviceCommandService.execute("device-abc", SmartThingsCommandReqDto.powerOff()))
                        .isInstanceOf(ErrorCodeException.class)
                        .satisfies(e -> assertThat(((ErrorCodeException) e).getErrorCode())
                                .isEqualTo(ErrorCode.SMARTTHINGS_TOKEN_INVALID));

                then(feignClient).shouldHaveNoInteractions();
            }
        }

        @Nested
        @DisplayName("SmartThings에서 권한 오류가 발생할 때")
        class Context_when_permission_denied {

            @Test
            @DisplayName("SmartThings 권한 오류를 내부 오류 코드로 변환해야 한다")
            void it_propagates_permission_exception() {
                // Given
                var deviceId = "device-abc";
                var command = SmartThingsCommandReqDto.powerOff();

                given(tokenProvider.getValidAccessToken()).willReturn("valid-access-token");
                willThrow(new SmartThingsApiException(403)).given(feignClient)
                        .sendDeviceCommand(anyString(), eq(deviceId), eq(command));

                // When & Then
                assertThatThrownBy(() -> sendDeviceCommandService.execute(deviceId, command))
                        .isInstanceOf(ErrorCodeException.class)
                        .satisfies(e -> assertThat(((ErrorCodeException) e).getErrorCode())
                                .isEqualTo(ErrorCode.SMARTTHINGS_PERMISSION_DENIED));
            }
        }

        @Nested
        @DisplayName("기기 명령 전송 중 일반 예외가 발생할 때")
        class Context_when_feign_fails {

            @Test
            @DisplayName("ExpectedException이 발생하고 BAD_GATEWAY 상태를 반환해야 한다")
            void it_throws_bad_gateway_exception() {
                // Given
                var deviceId = "device-abc";
                var command = SmartThingsCommandReqDto.powerOff();

                given(tokenProvider.getValidAccessToken()).willReturn("valid-access-token");
                willThrow(new RuntimeException("네트워크 오류")).given(feignClient)
                        .sendDeviceCommand(anyString(), eq(deviceId), eq(command));

                // When & Then
                assertThatThrownBy(() -> sendDeviceCommandService.execute(deviceId, command))
                        .isInstanceOf(ErrorCodeException.class).hasMessageNotContaining("네트워크 오류")
                        .satisfies(e -> assertThat(((ErrorCodeException) e).getErrorCode())
                                .isEqualTo(ErrorCode.SMARTTHINGS_COMMAND_UNAVAILABLE));
            }
        }
    }
}
