package team.washer.server.v2.global.security.jwt.filter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import team.washer.server.v2.global.common.error.exception.ExpectedException;
import team.washer.server.v2.global.security.jwt.provider.JwtTokenProvider;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
            final HttpServletResponse response,
            final FilterChain filterChain) throws ServletException, IOException {

        final var authorizationHeader = request.getHeader(AUTHORIZATION_HEADER);

        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        final var token = authorizationHeader.substring(BEARER_PREFIX.length());

        try {
            final var payload = jwtTokenProvider.parseToken(token);

            final var authorities = payload.role() != null
                    ? java.util.List.<org.springframework.security.core.GrantedAuthority>of(
                            new SimpleGrantedAuthority(payload.role().name()))
                    : java.util.List.<org.springframework.security.core.GrantedAuthority>of();

            final var authentication = new UsernamePasswordAuthenticationToken(payload.userId(), null, authorities);

            SecurityContextHolder.getContext().setAuthentication(authentication);

            filterChain.doFilter(request, response);
        } catch (final ExpectedException e) {
            sendErrorResponse(response, e.getStatusCode().value(), e.getStatusCode().name(), e.getMessage());
        }
    }

    private void sendErrorResponse(final HttpServletResponse response,
            final int statusCode,
            final String statusName,
            final String message) throws IOException {
        response.setStatus(statusCode);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        final var errorResponse = java.util.Map.of("status", statusName, "code", statusCode, "message", message);

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
