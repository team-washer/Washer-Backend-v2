package team.washer.server.v2.global.security.jwt.filter;

import java.io.IOException;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import team.washer.server.v2.global.common.error.exception.ErrorCodeException;
import team.washer.server.v2.global.security.jwt.provider.JwtTokenProvider;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    /** 토큰 파싱에 실패했을 때 그 {@link ErrorCodeException}을 보관하는 request attribute 이름입니다. */
    public static final String AUTHENTICATION_ERROR_ATTRIBUTE = JwtAuthenticationFilter.class.getName()
            + ".AUTHENTICATION_ERROR";

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
            final @NonNull HttpServletResponse response,
            final @NonNull FilterChain filterChain) throws ServletException, IOException {

        final var authorizationHeader = request.getHeader(AUTHORIZATION_HEADER);

        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        final var token = authorizationHeader.substring(BEARER_PREFIX.length());

        try {
            final var payload = jwtTokenProvider.parseAccessToken(token);

            final var authorities = payload.role() != null
                    ? List.<GrantedAuthority>of(new SimpleGrantedAuthority(payload.role().name()))
                    : List.<GrantedAuthority>of();
            final var authentication = new UsernamePasswordAuthenticationToken(payload.userId(), null, authorities);

            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (final ErrorCodeException e) {
            // 공개 경로는 토큰 오류와 관계없이 응답해야 하므로 여기서 응답을 끝내지 않는다.
            // 보호 경로의 401은 인가 단계의 entry point가 보관된 오류 코드로 응답한다.
            SecurityContextHolder.clearContext();
            request.setAttribute(AUTHENTICATION_ERROR_ATTRIBUTE, e);
        }

        filterChain.doFilter(request, response);
    }

}
