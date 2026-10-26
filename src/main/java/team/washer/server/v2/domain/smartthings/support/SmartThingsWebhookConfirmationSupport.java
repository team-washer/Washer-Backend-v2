package team.washer.server.v2.domain.smartthings.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * SmartThings Target URL 등록 시 전달되는 confirmationUrl을 호출해 등록을 확정한다.
 *
 * <p>
 * confirmationUrl은 요청 본문에서 오는 값이므로 SmartThings 도메인의 HTTPS URL만 호출하고 redirect를
 * 따르지 않아 SSRF를 막는다. URL에는 일회성 권한 값이 query로 담기므로 로그에는 host만 남긴다.
 * </p>
 */
@Component
@Slf4j
public class SmartThingsWebhookConfirmationSupport {

    private static final String ALLOWED_HOST_SUFFIX = ".smartthings.com";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER).build();

    /**
     * confirmationUrl이 SmartThings 도메인의 HTTPS URL인지 확인한다.
     *
     * @param confirmationUrl
     *            CONFIRMATION 요청의 confirmationUrl
     * @return 호출 허용 여부
     */
    public boolean isAllowed(final String confirmationUrl) {
        if (confirmationUrl == null || confirmationUrl.isBlank()) {
            return false;
        }
        try {
            final var uri = URI.create(confirmationUrl);
            final var host = uri.getHost();
            return "https".equals(uri.getScheme()) && uri.getRawUserInfo() == null && host != null
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && host.toLowerCase(Locale.ROOT).endsWith(ALLOWED_HOST_SUFFIX);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * confirmationUrl을 호출한다.
     *
     * @param confirmationUrl
     *            {@link #isAllowed(String)}를 통과한 URL
     * @return SmartThings가 2xx로 응답했는지 여부
     */
    public boolean confirm(final String confirmationUrl) {
        if (!isAllowed(confirmationUrl)) {
            throw new IllegalArgumentException("허용되지 않은 confirmationUrl입니다.");
        }
        final var uri = URI.create(confirmationUrl);
        final var request = HttpRequest.newBuilder(uri).timeout(REQUEST_TIMEOUT).GET().build();
        try {
            final var status = httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            log.info("SmartThings webhook confirmation requested host={} status={}", uri.getHost(), status);
            return status >= 200 && status < 300;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("SmartThings webhook confirmation interrupted host={}", uri.getHost());
            return false;
        } catch (IOException e) {
            log.warn("SmartThings webhook confirmation failed host={} error={}",
                    uri.getHost(),
                    e.getClass().getSimpleName());
            return false;
        }
    }
}
