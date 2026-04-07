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
import team.washer.server.v2.domain.smartthings.service.impl.RefreshSmartThingsTokenServiceImpl;
import team.washer.server.v2.global.thirdparty.smartthings.config.SmartThingsEnvironment;
import team.washer.server.v2.global.thirdparty.smartthings.feign.SmartThingsOAuthClient;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefreshSmartThingsTokenServiceImpl 클래스의")
class RefreshSmartThingsTokenServiceTest {

    @InjectMocks
    private RefreshSmartThingsTokenServiceImpl refreshSmartThingsTokenService;

    @Mock
    private SmartThingsOAuthClient smartThingsOAuthClient;

    @Mock
    private SmartThingsTokenRepository smartThingsTokenRepository;

    @Mock
    private SmartThingsEnvironment smartThingsEnvironment;

    private SmartThingsToken createExpiredToken() {
        return SmartThingsToken.builder()
                .accessToken("old-access").refreshToken("old-refresh")
                .expiresAt(LocalDateTime.now().minusMinutes(1)).build();
    }

    private SmartThingsToken createValidToken() {
        return SmartThingsToken.builder()
                .accessToken("valid-access").refreshToken("valid-refresh")
                .expiresAt(LocalDateTime.now().plusHours(2)).build();
    }

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Nested
        @DisplayName("만료 임박한 토큰이 있을 때")
        class Context_with_expiring_token {

            @Test
            @DisplayName("토큰을 갱신해야 한다")
            void it_refreshes_token() {
                // Given
                var expiredToken = createExpiredToken();
                var refreshResponse = new SmartThingsTokenExchangeResDto(
                        "new-access", "new-refresh", "Bearer", 3600, "r:devices:*");

                given(smartThingsTokenRepository.findSingletonTokenWithLock()).willReturn(Optional.of(expiredToken));
                given(smartThingsEnvironment.clientId()).willReturn("client-id");
                given(smartThingsEnvironment.clientSecret()).willReturn("client-secret");
                given(smartThingsOAuthClient.refreshToken(anyString(), anyString())).willReturn(refreshResponse);

                // When
                refreshSmartThingsTokenService.execute();

                // Then
                then(smartThingsTokenRepository).should(times(1)).save(expiredToken);
                assertThat(expiredToken.getAccessToken()).isEqualTo("new-access");
                assertThat(expiredToken.getRefreshToken()).isEqualTo("new-refresh");
            }
        }

        @Nested
        @DisplayName("아직 유효한 토큰이 있을 때")
        class Context_with_valid_token {

            @Test
            @DisplayName("갱신 없이 스킵해야 한다")
            void it_skips_refresh() {
                // Given
                var validToken = createValidToken();
                given(smartThingsTokenRepository.findSingletonTokenWithLock()).willReturn(Optional.of(validToken));

                // When
                refreshSmartThingsTokenService.execute();

                // Then
                then(smartThingsOAuthClient).shouldHaveNoInteractions();
                then(smartThingsTokenRepository).should(never()).save(any(SmartThingsToken.class));
            }
        }

        @Nested
        @DisplayName("저장된 토큰이 없을 때")
        class Context_with_no_token {

            @Test
            @DisplayName("ExpectedException이 발생하고 NOT_FOUND 상태를 반환해야 한다")
            void it_throws_not_found_exception() {
                // Given
                given(smartThingsTokenRepository.findSingletonTokenWithLock()).willReturn(Optional.empty());

                // When & Then
                assertThatThrownBy(() -> refreshSmartThingsTokenService.execute())
                        .isInstanceOf(ExpectedException.class)
                        .hasMessage("SmartThings 토큰이 존재하지 않습니다")
                        .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                                .isEqualTo(HttpStatus.NOT_FOUND));

                then(smartThingsOAuthClient).shouldHaveNoInteractions();
            }
        }

        @Nested
        @DisplayName("갱신 API 호출이 실패할 때")
        class Context_when_refresh_fails {

            @Test
            @DisplayName("ExpectedException이 발생하고 INTERNAL_SERVER_ERROR 상태를 반환해야 한다")
            void it_throws_internal_server_error() {
                // Given
                var expiredToken = createExpiredToken();
                given(smartThingsTokenRepository.findSingletonTokenWithLock()).willReturn(Optional.of(expiredToken));
                given(smartThingsEnvironment.clientId()).willReturn("client-id");
                given(smartThingsEnvironment.clientSecret()).willReturn("client-secret");
                given(smartThingsOAuthClient.refreshToken(anyString(), anyString()))
                        .willThrow(new RuntimeException("API 오류"));

                // When & Then
                assertThatThrownBy(() -> refreshSmartThingsTokenService.execute())
                        .isInstanceOf(ExpectedException.class)
                        .hasMessageContaining("SmartThings 토큰 갱신에 실패했습니다")
                        .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));
            }
        }
    }
}
