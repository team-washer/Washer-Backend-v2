package team.washer.server.v2.domain.smartthings.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsTokenExchangeResDto;
import team.washer.server.v2.domain.smartthings.entity.SmartThingsToken;
import team.washer.server.v2.domain.smartthings.repository.SmartThingsTokenRepository;
import team.washer.server.v2.domain.smartthings.service.impl.ExchangeSmartThingsTokenServiceImpl;
import team.washer.server.v2.global.thirdparty.smartthings.config.SmartThingsEnvironment;
import team.washer.server.v2.global.thirdparty.smartthings.feign.SmartThingsOAuthClient;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExchangeSmartThingsTokenServiceImpl 클래스의")
class ExchangeSmartThingsTokenServiceTest {

    @InjectMocks
    private ExchangeSmartThingsTokenServiceImpl exchangeSmartThingsTokenService;

    @Mock
    private SmartThingsOAuthClient smartThingsOAuthClient;

    @Mock
    private SmartThingsTokenRepository smartThingsTokenRepository;

    @Mock
    private SmartThingsEnvironment smartThingsEnvironment;

    private SmartThingsTokenExchangeResDto createTokenResponse() {
        return new SmartThingsTokenExchangeResDto("new-access-token", "new-refresh-token", "Bearer", 3600, "r:devices:*");
    }

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Nested
        @DisplayName("토큰이 없는 상태에서 코드를 교환할 때")
        class Context_with_no_existing_token {

            @Test
            @DisplayName("새 토큰을 저장해야 한다")
            void it_saves_new_token() {
                // Given
                given(smartThingsEnvironment.clientId()).willReturn("client-id");
                given(smartThingsEnvironment.clientSecret()).willReturn("client-secret");
                given(smartThingsOAuthClient.exchangeToken(anyString(), anyString())).willReturn(createTokenResponse());
                given(smartThingsTokenRepository.findSingletonToken()).willReturn(Optional.empty());

                // When
                exchangeSmartThingsTokenService.execute("auth-code", "https://redirect.uri");

                // Then
                then(smartThingsTokenRepository).should(times(1)).save(any(SmartThingsToken.class));
            }
        }

        @Nested
        @DisplayName("기존 토큰이 있는 상태에서 코드를 교환할 때")
        class Context_with_existing_token {

            @Test
            @DisplayName("기존 토큰을 업데이트해야 한다")
            void it_updates_existing_token() {
                // Given
                var existingToken = SmartThingsToken.builder()
                        .accessToken("old-access").refreshToken("old-refresh")
                        .expiresAt(LocalDateTime.now().plusHours(1)).build();

                given(smartThingsEnvironment.clientId()).willReturn("client-id");
                given(smartThingsEnvironment.clientSecret()).willReturn("client-secret");
                given(smartThingsOAuthClient.exchangeToken(anyString(), anyString())).willReturn(createTokenResponse());
                given(smartThingsTokenRepository.findSingletonToken()).willReturn(Optional.of(existingToken));

                // When
                exchangeSmartThingsTokenService.execute("auth-code", "https://redirect.uri");

                // Then
                then(smartThingsTokenRepository).should(times(1)).save(existingToken);
                assertThat(existingToken.getAccessToken()).isEqualTo("new-access-token");
                assertThat(existingToken.getRefreshToken()).isEqualTo("new-refresh-token");
            }
        }

        @Nested
        @DisplayName("OAuth 클라이언트 호출이 실패할 때")
        class Context_when_oauth_client_fails {

            @Test
            @DisplayName("ExpectedException이 발생하고 BAD_GATEWAY 상태를 반환해야 한다")
            void it_throws_bad_gateway_exception() {
                // Given
                given(smartThingsEnvironment.clientId()).willReturn("client-id");
                given(smartThingsEnvironment.clientSecret()).willReturn("client-secret");
                given(smartThingsOAuthClient.exchangeToken(anyString(), anyString()))
                        .willThrow(new RuntimeException("연결 오류"));

                // When & Then
                assertThatThrownBy(() -> exchangeSmartThingsTokenService.execute("auth-code", "https://redirect.uri"))
                        .isInstanceOf(ExpectedException.class)
                        .hasMessageContaining("SmartThings 토큰 교환에 실패했습니다")
                        .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                                .isEqualTo(HttpStatus.BAD_GATEWAY));
            }
        }
    }
}
