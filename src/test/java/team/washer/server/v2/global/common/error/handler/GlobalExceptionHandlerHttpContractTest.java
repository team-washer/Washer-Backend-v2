package team.washer.server.v2.global.common.error.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.cors.CorsConfigurationSource;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import team.themoment.sdk.autoconfigure.SdkAutoConfiguration;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.global.common.trace.TraceIdFilter;
import team.washer.server.v2.global.security.config.DomainAuthorizationConfig;
import team.washer.server.v2.global.security.config.SecurityConfig;
import team.washer.server.v2.global.security.jwt.provider.JwtTokenProvider;
import team.washer.server.v2.global.thirdparty.discord.service.DiscordErrorNotificationService;

/**
 * 실제 HTTP 요청 흐름(보안 필터 체인 → DispatcherServlet → 전역 예외 처리기 → SDK 응답 래퍼)에서 오류 응답의
 * 상태 코드와 본문 계약을 검증합니다.
 */
@WebMvcTest(controllers = GlobalExceptionHandlerHttpContractTest.ErrorTestController.class)
@ImportAutoConfiguration({SecurityAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class,
        SdkAutoConfiguration.class})
@Import({SecurityConfig.class, DomainAuthorizationConfig.class, GlobalExceptionHandler.class, TraceIdFilter.class,
        GlobalExceptionHandlerHttpContractTest.ErrorTestController.class,
        GlobalExceptionHandlerHttpContractTest.TestBeansConfiguration.class})
@DisplayName("전역 예외 처리기의 HTTP 오류 응답 계약은")
class GlobalExceptionHandlerHttpContractTest {

    private static final String BASE_PATH = "/api/v2/test-errors";

    @Resource
    private MockMvc mockMvc;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private DiscordErrorNotificationService discordErrorNotificationService;

    private ResultActions performAsUser(final MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(user("user").authorities(new SimpleGrantedAuthority("USER"))));
    }

    /**
     * 기존 message 계약과 추가된 errorCode·traceId를 함께 검증하고, 응답 헤더와 본문의 추적 ID가 같은지 확인합니다.
     */
    private void assertErrorContract(final ResultActions result, final HttpStatus status, final String errorCode)
            throws Exception {
        final var response = result.andExpect(status().is(status.value()))
                .andExpect(jsonPath("$.status").value(containsString(status.name())))
                .andExpect(jsonPath("$.code").value(status.value())).andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.data.errorCode").value(errorCode))
                .andExpect(header().exists(TraceIdFilter.TRACE_ID_HEADER)).andReturn().getResponse();

        final var traceId = response.getHeader(TraceIdFilter.TRACE_ID_HEADER);
        assertThat(response.getContentAsString()).contains("\"traceId\":\"" + traceId + "\"");
    }

    private void assertNotNotified() {
        then(discordErrorNotificationService).shouldHaveNoInteractions();
    }

    @Nested
    @DisplayName("400 요청 오류는")
    class Describe_badRequest {

        @Test
        @DisplayName("본문 검증 실패 시 첫 입력값 오류를 message로, 전체를 fieldErrors로 응답한다")
        void respondsValidationFailureWithFieldErrors() throws Exception {
            final var result = performAsUser(post(BASE_PATH + "/items").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"\",\"quantity\":1}"));

            assertErrorContract(result, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
            result.andExpect(jsonPath("$.message").value("이름은 필수입니다"))
                    .andExpect(jsonPath("$.data.fieldErrors[0].field").value("name"))
                    .andExpect(jsonPath("$.data.fieldErrors[0].message").value("이름은 필수입니다"))
                    .andExpect(content().string(not(containsString("fieldErrors':"))));
            assertNotNotified();
        }

        @Test
        @DisplayName("잘못된 JSON은 파서 상세를 노출하지 않고 INVALID_REQUEST_BODY로 응답한다")
        void respondsMalformedJson() throws Exception {
            final var result = performAsUser(
                    post(BASE_PATH + "/items").contentType(MediaType.APPLICATION_JSON).content("{\"name\":"));

            assertErrorContract(result, HttpStatus.BAD_REQUEST, "INVALID_REQUEST_BODY");
            result.andExpect(jsonPath("$.message").value("요청 본문 형식이 올바르지 않습니다."))
                    .andExpect(content().string(not(containsString("JSON parse error"))))
                    .andExpect(content().string(not(containsString("jackson"))));
            assertNotNotified();
        }

        @Test
        @DisplayName("경로 변수 타입 불일치는 500이 아닌 TYPE_MISMATCH로 응답한다")
        void respondsTypeMismatch() throws Exception {
            final var result = performAsUser(get(BASE_PATH + "/items/abc"));

            assertErrorContract(result, HttpStatus.BAD_REQUEST, "TYPE_MISMATCH");
            result.andExpect(jsonPath("$.data.fieldErrors[0].field").value("id"))
                    .andExpect(content().string(not(containsString("NumberFormatException"))));
            assertNotNotified();
        }

        @Test
        @DisplayName("필수 쿼리 파라미터 누락은 500이 아닌 MISSING_PARAMETER로 응답한다")
        void respondsMissingParameter() throws Exception {
            final var result = performAsUser(get(BASE_PATH + "/search"));

            assertErrorContract(result, HttpStatus.BAD_REQUEST, "MISSING_PARAMETER");
            result.andExpect(jsonPath("$.data.fieldErrors[0].field").value("keyword"));
            assertNotNotified();
        }
    }

    @Nested
    @DisplayName("401·403 인증·인가 오류는")
    class Describe_security {

        @Test
        @DisplayName("토큰 없이 보호된 API를 호출하면 UNAUTHORIZED로 응답한다")
        void respondsUnauthorizedWithoutToken() throws Exception {
            final var result = mockMvc.perform(get(BASE_PATH + "/items/1"));

            assertErrorContract(result, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
            assertNotNotified();
        }

        @Test
        @DisplayName("유효하지 않은 토큰이면 JWT 필터의 문구를 유지하며 같은 형식으로 응답한다")
        void respondsInvalidTokenInSameFormat() throws Exception {
            given(jwtTokenProvider.parseAccessToken("invalid-token"))
                    .willThrow(new ExpectedException("유효하지 않은 토큰입니다.", HttpStatus.UNAUTHORIZED));

            final var result = mockMvc
                    .perform(get(BASE_PATH + "/items/1").header("Authorization", "Bearer invalid-token"));

            assertErrorContract(result, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
            result.andExpect(jsonPath("$.message").value("유효하지 않은 토큰입니다."));
            assertNotNotified();
        }

        @Test
        @DisplayName("권한이 부족하면 FORBIDDEN으로 응답한다")
        void respondsForbiddenWithoutAuthority() throws Exception {
            final var result = performAsUser(get("/api/v2/admin/test-errors"));

            assertErrorContract(result, HttpStatus.FORBIDDEN, "FORBIDDEN");
            assertNotNotified();
        }
    }

    @Nested
    @DisplayName("404·405 요청 경로 오류는")
    class Describe_routing {

        @Test
        @DisplayName("없는 경로는 NoResourceFoundException을 거쳐 NOT_FOUND로 응답한다")
        void respondsNotFoundForUnknownPath() throws Exception {
            final var result = performAsUser(get(BASE_PATH + "/unknown-path"));

            assertErrorContract(result, HttpStatus.NOT_FOUND, "NOT_FOUND");
            assertNotNotified();
        }

        @Test
        @DisplayName("ExpectedException의 404는 서비스 문구와 상태 이름 오류 코드로 응답한다")
        void respondsExpectedExceptionMessage() throws Exception {
            final var result = performAsUser(get(BASE_PATH + "/expected"));

            assertErrorContract(result, HttpStatus.NOT_FOUND, "NOT_FOUND");
            result.andExpect(jsonPath("$.message").value("항목을 찾을 수 없습니다"));
            assertNotNotified();
        }

        @Test
        @DisplayName("지원하지 않는 메서드는 METHOD_NOT_ALLOWED와 Allow 헤더로 응답한다")
        void respondsMethodNotAllowed() throws Exception {
            final var result = performAsUser(delete(BASE_PATH + "/search"));

            assertErrorContract(result, HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED");
            result.andExpect(header().string("Allow", containsString("GET")));
            assertNotNotified();
        }
    }

    @Nested
    @DisplayName("409·503·500 서버 측 오류는")
    class Describe_serverSide {

        @Test
        @DisplayName("동시성 충돌은 CONFLICT로 응답하고 운영 알림을 보내지 않는다")
        void respondsConflictWithoutNotification() throws Exception {
            final var result = performAsUser(get(BASE_PATH + "/conflict"));

            assertErrorContract(result, HttpStatus.CONFLICT, "CONFLICT");
            result.andExpect(content().string(not(containsString("Reservation"))));
            assertNotNotified();
        }

        @Test
        @DisplayName("외부 장애를 감싼 5xx ExpectedException은 서비스 문구로 응답하고 운영 알림을 보낸다")
        void respondsExpectedServerExceptionWithNotification() throws Exception {
            final var result = performAsUser(get(BASE_PATH + "/external"));

            assertErrorContract(result, HttpStatus.BAD_GATEWAY, "BAD_GATEWAY");
            result.andExpect(jsonPath("$.message").value("기기 상태를 확인할 수 없습니다"));
            then(discordErrorNotificationService).should()
                    .notifyError(any(ExpectedException.class), isNull(), anyMap());
        }

        @Test
        @DisplayName("Redis 연결 실패는 SERVICE_UNAVAILABLE로 응답하고 운영 알림을 보낸다")
        void respondsServiceUnavailableWithNotification() throws Exception {
            final var result = performAsUser(get(BASE_PATH + "/redis"));

            assertErrorContract(result, HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE");
            result.andExpect(content().string(not(containsString("10.0.0.1"))));
            then(discordErrorNotificationService).should()
                    .notifyError(any(RedisConnectionFailureException.class), isNull(), anyMap());
        }

        @Test
        @DisplayName("예상하지 못한 오류는 내부 정보를 숨긴 채 INTERNAL_SERVER_ERROR로 응답하고 운영 알림을 보낸다")
        void respondsInternalServerErrorWithoutLeakingDetails() throws Exception {
            final var result = performAsUser(get(BASE_PATH + "/unexpected"));

            assertErrorContract(result, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR");
            result.andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."))
                    .andExpect(content().string(not(containsString("SELECT"))))
                    .andExpect(content().string(not(containsString("IllegalStateException"))))
                    .andExpect(content().string(not(containsString("secret-token"))))
                    .andExpect(content().string(not(containsString("at team.washer"))));
            then(discordErrorNotificationService).should()
                    .notifyError(any(IllegalStateException.class), isNull(), anyMap());
        }
    }

    @Nested
    @TestPropertySource(properties = "spring.web.resources.add-mappings=false")
    @DisplayName("정적 리소스 매핑을 끈 환경에서는")
    class Describe_noHandlerFound {

        @Test
        @DisplayName("없는 경로가 NoHandlerFoundException을 거쳐 NOT_FOUND로 응답한다")
        void respondsNotFoundForUnknownPath() throws Exception {
            final var result = performAsUser(get(BASE_PATH + "/unknown-path"));

            assertErrorContract(result, HttpStatus.NOT_FOUND, "NOT_FOUND");
            assertNotNotified();
        }
    }

    record ItemReqDto(@NotBlank(message = "이름은 필수입니다") String name, @NotNull(message = "수량은 필수입니다") Integer quantity) {
    }

    record ItemResDto(Long id) {
    }

    @RestController
    @RequestMapping(BASE_PATH)
    static class ErrorTestController {

        @GetMapping("/items/{id}")
        ItemResDto findItem(@PathVariable Long id) {
            return new ItemResDto(id);
        }

        @PostMapping("/items")
        ItemResDto createItem(@Valid @RequestBody ItemReqDto reqDto) {
            return new ItemResDto(1L);
        }

        @GetMapping("/search")
        ItemResDto search(@RequestParam String keyword) {
            return new ItemResDto(1L);
        }

        @GetMapping("/expected")
        ItemResDto expected() {
            throw new ExpectedException("항목을 찾을 수 없습니다", HttpStatus.NOT_FOUND);
        }

        @GetMapping("/external")
        ItemResDto external() {
            throw new ExpectedException("기기 상태를 확인할 수 없습니다", HttpStatus.BAD_GATEWAY);
        }

        @GetMapping("/conflict")
        ItemResDto conflict() {
            throw new ObjectOptimisticLockingFailureException(Reservation.class, 1L);
        }

        @GetMapping("/redis")
        ItemResDto redis() {
            throw new RedisConnectionFailureException("Unable to connect to Redis at 10.0.0.1:6379");
        }

        @GetMapping("/unexpected")
        ItemResDto unexpected() {
            throw new IllegalStateException("SELECT * FROM users WHERE token = 'secret-token'");
        }
    }

    @TestConfiguration
    static class TestBeansConfiguration {

        @Bean(name = "configure")
        CorsConfigurationSource corsConfigurationSource() {
            return request -> null;
        }
    }
}
