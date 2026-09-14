package team.washer.server.v2.global.security.jwt.filter;

import java.io.IOException;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.global.security.jwt.provider.JwtTokenProvider;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final HandlerExceptionResolver handlerExceptionResolver;

    public JwtAuthenticationFilter(final JwtTokenProvider jwtTokenProvider,
            @Qualifier("handlerExceptionResolver") final HandlerExceptionResolver handlerExceptionResolver) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

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

            filterChain.doFilter(request, response);
        } catch (final ExpectedException e) {
            // 컨트롤러 오류와 같은 응답 형식(오류 코드·추적 ID)을 쓰도록 전역 예외 처리기에 위임한다
            handlerExceptionResolver.resolveException(request, response, null, e);
        }
    }

}
