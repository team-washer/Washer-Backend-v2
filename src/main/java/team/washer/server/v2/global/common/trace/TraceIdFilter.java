package team.washer.server.v2.global.common.trace;

import java.io.IOException;
import java.util.UUID;

import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 요청마다 추적 ID를 발급해 로그(MDC)와 응답 헤더, 오류 응답 본문을 연결하는 필터.
 *
 * <p>
 * 인증·인가 오류도 추적할 수 있도록 Spring Security 필터 체인보다 먼저 실행된다. 클라이언트가 보낸 값은 로그 위조를 막기
 * 위해 사용하지 않고 항상 서버에서 새로 발급한다. 컨테이너의 오류 디스패치에서도 같은 요청의 추적 ID를 이어 쓴다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";
    private static final String TRACE_ID_ATTRIBUTE = TraceIdFilter.class.getName() + ".traceId";

    /**
     * 현재 스레드에서 처리 중인 요청의 추적 ID를 반환합니다.
     *
     * @return 추적 ID. 요청 처리 스레드가 아니면 {@code null}
     */
    public static String currentTraceId() {
        return MDC.get(MDC_KEY);
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(final @NonNull HttpServletRequest request,
            final @NonNull HttpServletResponse response,
            final @NonNull FilterChain filterChain) throws ServletException, IOException {
        final var traceId = resolveTraceId(request);
        MDC.put(MDC_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private String resolveTraceId(final HttpServletRequest request) {
        if (request.getAttribute(TRACE_ID_ATTRIBUTE) instanceof String existingTraceId) {
            return existingTraceId;
        }
        final var traceId = UUID.randomUUID().toString();
        request.setAttribute(TRACE_ID_ATTRIBUTE, traceId);
        return traceId;
    }
}
