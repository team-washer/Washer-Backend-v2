package team.washer.server.v2.domain.smartthings.support;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import team.washer.server.v2.domain.smartthings.entity.SmartThingsToken;
import team.washer.server.v2.domain.smartthings.repository.SmartThingsTokenRepository;
import team.washer.server.v2.global.common.error.code.ErrorCode;
import team.washer.server.v2.global.common.error.exception.ErrorCodeException;

@ExtendWith(MockitoExtension.class)
@DisplayName("SmartThingsTokenProvider 클래스의")
class SmartThingsTokenProviderTest {

    @Mock
    private SmartThingsTokenRepository tokenRepository;

    @Test
    @DisplayName("외부에서 거절된 토큰은 DB에서도 만료되어 다시 사용되지 않는다")
    void invalidates_rejected_token_in_database() {
        final var token = token("rejected-access");
        final var provider = new SmartThingsTokenProvider(tokenRepository);
        given(tokenRepository.findSingletonToken()).willReturn(Optional.of(token));
        given(tokenRepository.findSingletonTokenWithLock()).willReturn(Optional.of(token));

        assertThat(provider.getValidAccessToken()).isEqualTo("rejected-access");
        provider.invalidate("rejected-access");

        assertThatThrownBy(provider::getValidAccessToken).isInstanceOf(ErrorCodeException.class)
                .satisfies(e -> assertThat(((ErrorCodeException) e).getErrorCode())
                        .isEqualTo(ErrorCode.SMARTTHINGS_TOKEN_INVALID));
    }

    @Test
    @DisplayName("다른 인스턴스가 갱신한 최신 토큰은 과거 401 응답으로 무효화하지 않는다")
    void preserves_newer_token_after_stale_rejection() {
        final var currentToken = token("new-access");
        final var provider = new SmartThingsTokenProvider(tokenRepository);
        provider.refresh(currentToken);
        given(tokenRepository.findSingletonTokenWithLock()).willReturn(Optional.of(currentToken));

        provider.invalidate("old-access");

        assertThat(provider.getValidAccessToken()).isEqualTo("new-access");
        assertThat(currentToken.isValid()).isTrue();
        then(tokenRepository).should(never()).findSingletonToken();
    }

    private SmartThingsToken token(final String accessToken) {
        return SmartThingsToken.builder().accessToken(accessToken).refreshToken("refresh-token")
                .expiresAt(LocalDateTime.now().plusHours(1)).build();
    }
}
