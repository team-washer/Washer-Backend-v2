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
import team.washer.server.v2.domain.smartthings.entity.SmartThingsToken;
import team.washer.server.v2.domain.smartthings.repository.SmartThingsTokenRepository;
import team.washer.server.v2.domain.smartthings.service.impl.QuerySmartThingsAccessTokenServiceImpl;

@ExtendWith(MockitoExtension.class)
@DisplayName("QuerySmartThingsAccessTokenServiceImpl 클래스의")
class QuerySmartThingsAccessTokenServiceTest {

    @InjectMocks
    private QuerySmartThingsAccessTokenServiceImpl querySmartThingsAccessTokenService;

    @Mock
    private SmartThingsTokenRepository smartThingsTokenRepository;

    private SmartThingsToken createValidToken() {
        return SmartThingsToken.builder().accessToken("valid-access").refreshToken("valid-refresh")
                .expiresAt(LocalDateTime.now().plusHours(2)).build();
    }

    private SmartThingsToken createExpiredToken() {
        return SmartThingsToken.builder().accessToken("expired-access").refreshToken("expired-refresh")
                .expiresAt(LocalDateTime.now().minusMinutes(1)).build();
    }

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Nested
        @DisplayName("유효한 토큰이 있을 때")
        class Context_with_valid_token {

            @Test
            @DisplayName("액세스 토큰과 만료 시각을 반환해야 한다")
            void it_returns_access_token() {
                // Given
                var validToken = createValidToken();
                given(smartThingsTokenRepository.findSingletonToken()).willReturn(Optional.of(validToken));

                // When
                var result = querySmartThingsAccessTokenService.execute();

                // Then
                assertThat(result.accessToken()).isEqualTo("valid-access");
                assertThat(result.expiresAt()).isEqualTo(validToken.getExpiresAt());
            }
        }

        @Nested
        @DisplayName("저장된 토큰이 없을 때")
        class Context_with_no_token {

            @Test
            @DisplayName("ExpectedException이 발생하고 NOT_FOUND 상태를 반환해야 한다")
            void it_throws_not_found_exception() {
                // Given
                given(smartThingsTokenRepository.findSingletonToken()).willReturn(Optional.empty());

                // When & Then
                assertThatThrownBy(() -> querySmartThingsAccessTokenService.execute())
                        .isInstanceOf(ExpectedException.class).hasMessage("SmartThings 토큰이 존재하지 않습니다")
                        .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                                .isEqualTo(HttpStatus.NOT_FOUND));
            }
        }

        @Nested
        @DisplayName("토큰이 만료되었을 때")
        class Context_with_expired_token {

            @Test
            @DisplayName("ExpectedException이 발생하고 NOT_FOUND 상태를 반환해야 한다")
            void it_throws_not_found_exception() {
                // Given
                var expiredToken = createExpiredToken();
                given(smartThingsTokenRepository.findSingletonToken()).willReturn(Optional.of(expiredToken));

                // When & Then
                assertThatThrownBy(() -> querySmartThingsAccessTokenService.execute())
                        .isInstanceOf(ExpectedException.class).hasMessage("SmartThings 토큰이 만료되었거나 유효하지 않습니다")
                        .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                                .isEqualTo(HttpStatus.NOT_FOUND));
            }
        }
    }
}
