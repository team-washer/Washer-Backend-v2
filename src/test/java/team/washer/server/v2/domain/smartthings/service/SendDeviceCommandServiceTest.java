package team.washer.server.v2.domain.smartthings.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.smartthings.dto.request.SmartThingsCommandReqDto;
import team.washer.server.v2.domain.smartthings.entity.SmartThingsToken;
import team.washer.server.v2.domain.smartthings.exception.SmartThingsPermissionException;
import team.washer.server.v2.domain.smartthings.repository.SmartThingsTokenRepository;
import team.washer.server.v2.domain.smartthings.service.impl.SendDeviceCommandServiceImpl;
import team.washer.server.v2.global.thirdparty.smartthings.feign.SmartThingsFeignClient;

@ExtendWith(MockitoExtension.class)
@DisplayName("SendDeviceCommandServiceImpl 클래스의")
class SendDeviceCommandServiceTest {

    @InjectMocks
    private SendDeviceCommandServiceImpl sendDeviceCommandService;

    @Mock
    private SmartThingsFeignClient feignClient;

    @Mock
    private SmartThingsTokenRepository tokenRepository;

    private SmartThingsToken createValidToken() {
        return SmartThingsToken.builder().accessToken("valid-access-token").refreshToken("refresh-token")
                .expiresAt(LocalDateTime.now().plusHours(1)).build();
    }

    private SmartThingsToken createExpiredToken() {
        return SmartThingsToken.builder().accessToken("expired-access-token").refreshToken("refresh-token")
                .expiresAt(LocalDateTime.now().minusMinutes(10)).build();
    }

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
                var token = createValidToken();

                given(tokenRepository.findSingletonToken()).willReturn(Optional.of(token));

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
                given(tokenRepository.findSingletonToken()).willReturn(Optional.empty());

                // When & Then
                assertThatThrownBy(
                        () -> sendDeviceCommandService.execute("device-abc", SmartThingsCommandReqDto.powerOff()))
                        .isInstanceOf(ExpectedException.class).hasMessage("SmartThings 토큰이 존재하지 않습니다")
                        .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                                .isEqualTo(HttpStatus.NOT_FOUND));

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
                var expiredToken = createExpiredToken();
                given(tokenRepository.findSingletonToken()).willReturn(Optional.of(expiredToken));

                // When & Then
                assertThatThrownBy(
                        () -> sendDeviceCommandService.execute("device-abc", SmartThingsCommandReqDto.powerOff()))
                        .isInstanceOf(ExpectedException.class).hasMessage("SmartThings 토큰이 만료되었거나 유효하지 않습니다")
                        .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                                .isEqualTo(HttpStatus.NOT_FOUND));

                then(feignClient).shouldHaveNoInteractions();
            }
        }

        @Nested
        @DisplayName("SmartThings에서 권한 오류가 발생할 때")
        class Context_when_permission_denied {

            @Test
            @DisplayName("SmartThingsPermissionException을 그대로 전파해야 한다")
            void it_propagates_permission_exception() {
                // Given
                var deviceId = "device-abc";
                var command = SmartThingsCommandReqDto.powerOff();
                var token = createValidToken();

                given(tokenRepository.findSingletonToken()).willReturn(Optional.of(token));
                willThrow(new SmartThingsPermissionException("x:devices:* 스코프 없음")).given(feignClient)
                        .sendDeviceCommand(anyString(), eq(deviceId), eq(command));

                // When & Then
                assertThatThrownBy(() -> sendDeviceCommandService.execute(deviceId, command))
                        .isInstanceOf(SmartThingsPermissionException.class).hasMessage("x:devices:* 스코프 없음");
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
                var token = createValidToken();

                given(tokenRepository.findSingletonToken()).willReturn(Optional.of(token));
                willThrow(new RuntimeException("네트워크 오류")).given(feignClient)
                        .sendDeviceCommand(anyString(), eq(deviceId), eq(command));

                // When & Then
                assertThatThrownBy(() -> sendDeviceCommandService.execute(deviceId, command))
                        .isInstanceOf(ExpectedException.class).hasMessageContaining("기기 명령 전송에 실패했습니다")
                        .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                                .isEqualTo(HttpStatus.BAD_GATEWAY));
            }
        }
    }
}
