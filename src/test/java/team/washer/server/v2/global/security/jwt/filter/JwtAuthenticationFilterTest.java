package team.washer.server.v2.global.security.jwt.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.servlet.FilterChain;
import team.washer.server.v2.domain.user.enums.UserRole;
import team.washer.server.v2.global.common.error.code.ErrorCode;
import team.washer.server.v2.global.common.error.exception.ErrorCodeException;
import team.washer.server.v2.global.security.jwt.dto.JwtPayload;
import team.washer.server.v2.global.security.jwt.provider.JwtTokenProvider;

@DisplayName("JWT 인증 필터는")
class JwtAuthenticationFilterTest {

    private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenProvider);

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final AtomicBoolean chainInvoked = new AtomicBoolean(false);
    private final AtomicReference<Authentication> authenticationInChain = new AtomicReference<>();

    /** 다음 필터가 호출된 시점의 인증 정보를 기록합니다. */
    private final FilterChain filterChain = (req, res) -> {
        chainInvoked.set(true);
        authenticationInChain.set(SecurityContextHolder.getContext().getAuthentication());
    };

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("Authorization 헤더가 없으면")
    class Context_withoutHeader {

        @Test
        @DisplayName("토큰을 파싱하지 않고 인증 정보 없이 필터 체인을 진행한다")
        void continuesWithoutAuthentication() throws Exception {
            // When
            filter.doFilter(request, response, filterChain);

            // Then
            assertThat(chainInvoked).isTrue();
            assertThat(authenticationInChain.get()).isNull();
            assertThat(request.getAttribute(JwtAuthenticationFilter.AUTHENTICATION_ERROR_ATTRIBUTE)).isNull();
            then(jwtTokenProvider).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("유효한 access token이면")
    class Context_withValidToken {

        @Test
        @DisplayName("사용자 ID와 권한으로 인증 정보를 설정한 뒤 필터 체인을 진행한다")
        void setsAuthentication() throws Exception {
            // Given
            given(jwtTokenProvider.parseAccessToken("valid-token")).willReturn(new JwtPayload(1L, UserRole.ADMIN));
            request.addHeader("Authorization", "Bearer valid-token");

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            assertThat(chainInvoked).isTrue();
            assertThat(authenticationInChain.get().getPrincipal()).isEqualTo(1L);
            assertThat(authenticationInChain.get().getAuthorities()).extracting(GrantedAuthority::getAuthority)
                    .containsExactly("ADMIN");
            assertThat(request.getAttribute(JwtAuthenticationFilter.AUTHENTICATION_ERROR_ATTRIBUTE)).isNull();
        }
    }

    @Nested
    @DisplayName("토큰 파싱에 실패하면")
    class Context_withUnparsableToken {

        @Test
        @DisplayName("응답을 끝내지 않고 오류를 request attribute에 보관한 채 필터 체인을 진행한다")
        void keepsErrorAndContinues() throws Exception {
            // Given
            final var tokenError = new ErrorCodeException(ErrorCode.ACCESS_TOKEN_EXPIRED);
            given(jwtTokenProvider.parseAccessToken("expired-token")).willThrow(tokenError);
            request.addHeader("Authorization", "Bearer expired-token");

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            assertThat(chainInvoked).isTrue();
            assertThat(authenticationInChain.get()).isNull();
            assertThat(request.getAttribute(JwtAuthenticationFilter.AUTHENTICATION_ERROR_ATTRIBUTE))
                    .isSameAs(tokenError);
            assertThat(response.isCommitted()).isFalse();
        }

        @Test
        @DisplayName("이미 설정된 인증 정보가 있어도 지운다")
        void clearsExistingAuthentication() throws Exception {
            // Given
            SecurityContextHolder.getContext()
                    .setAuthentication(new UsernamePasswordAuthenticationToken(99L, null, List.of()));
            given(jwtTokenProvider.parseAccessToken("invalid-token"))
                    .willThrow(new ErrorCodeException(ErrorCode.ACCESS_TOKEN_INVALID));
            request.addHeader("Authorization", "Bearer invalid-token");

            // When
            filter.doFilter(request, response, filterChain);

            // Then
            assertThat(chainInvoked).isTrue();
            assertThat(authenticationInChain.get()).isNull();
        }
    }

    @Nested
    @DisplayName("토큰 파싱 외의 예외가 발생하면")
    class Context_withUnexpectedException {

        @Test
        @DisplayName("예외를 삼키지 않고 필터 체인도 진행하지 않는다")
        void propagatesException() {
            // Given
            given(jwtTokenProvider.parseAccessToken("token")).willThrow(new IllegalStateException("unexpected"));
            request.addHeader("Authorization", "Bearer token");

            // When & Then
            assertThatThrownBy(() -> filter.doFilter(request, response, filterChain))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(chainInvoked).isFalse();
        }
    }
}
