package team.washer.server.v2.global.security.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import team.washer.server.v2.domain.smartthings.controller.AdminSmartThingsDeviceController;
import team.washer.server.v2.domain.smartthings.controller.AdminSmartThingsOAuthController;
import team.washer.server.v2.domain.smartthings.dto.response.DeviceSyncTriggerResDto;
import team.washer.server.v2.domain.smartthings.service.ExchangeSmartThingsTokenService;
import team.washer.server.v2.domain.smartthings.service.TriggerManualDeviceSyncService;
import team.washer.server.v2.global.config.ObjectMapperConfig;
import team.washer.server.v2.global.security.jwt.provider.JwtTokenProvider;
import team.washer.server.v2.global.thirdparty.smartthings.SmartThingsOAuthStateStore;
import team.washer.server.v2.global.thirdparty.smartthings.config.SmartThingsEnvironment;

/**
 * 관리자 SmartThings 엔드포인트의 인가 범위 분리를 검증하는 보안 통합 테스트
 *
 * <p>
 * OAuth 콜백만 비인증 공개 경로로 남기고, 인증 시작과 기기 수동 동기화는 관리자 권한을 요구하는지 확인합니다.
 */
@WebMvcTest(controllers = {AdminSmartThingsDeviceController.class, AdminSmartThingsOAuthController.class})
@Import({SecurityConfig.class, DomainAuthorizationConfig.class, ObjectMapperConfig.class,
        AdminSmartThingsAuthorizationTest.TestSecuritySupportConfig.class})
@DisplayName("관리자 SmartThings 엔드포인트 인가 설정은")
class AdminSmartThingsAuthorizationTest {

    private static final String SYNC_PATH = "/api/v2/admin/smartthings/devices/sync";
    private static final String AUTHORIZE_PATH = "/api/v2/admin/smartthings/oauth/authorize";
    private static final String CALLBACK_PATH = "/api/v2/admin/smartthings/oauth/callback";
    private static final String SYNC_BODY = """
            {"정말 실행하시겠습니까 이 작업은 SmartThings 전체 기기 목록을 즉시 재동기화하며 누락된 기기를 비활성화하는 결과를 촉발합니다": true}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TriggerManualDeviceSyncService triggerManualDeviceSyncService;

    @MockitoBean
    private ExchangeSmartThingsTokenService exchangeSmartThingsTokenService;

    @MockitoBean
    private SmartThingsOAuthStateStore stateStore;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Nested
    @DisplayName("기기 수동 동기화 경로는")
    class Describe_manual_device_sync {

        @Test
        @DisplayName("비인증 요청을 거부하고 서비스를 실행하지 않는다")
        void it_denies_anonymous_request() throws Exception {
            mockMvc.perform(post(SYNC_PATH).contentType(MediaType.APPLICATION_JSON).content(SYNC_BODY))
                    .andExpect(status().isForbidden());

            then(triggerManualDeviceSyncService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("일반 사용자 권한 요청을 거부하고 서비스를 실행하지 않는다")
        void it_denies_student_request() throws Exception {
            mockMvc.perform(post(SYNC_PATH).with(user("student").authorities(authority("STUDENT")))
                    .contentType(MediaType.APPLICATION_JSON).content(SYNC_BODY)).andExpect(status().isForbidden());

            then(triggerManualDeviceSyncService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("ADMIN 권한 요청을 정상적으로 접수한다")
        void it_accepts_admin_request() throws Exception {
            given(triggerManualDeviceSyncService.execute(any()))
                    .willReturn(new DeviceSyncTriggerResDto("접수되었습니다.", true, Instant.EPOCH, 1));

            mockMvc.perform(post(SYNC_PATH).with(user("admin").authorities(authority("ADMIN")))
                    .contentType(MediaType.APPLICATION_JSON).content(SYNC_BODY)).andExpect(status().isOk());

            then(triggerManualDeviceSyncService).should(times(1)).execute(any());
        }

        @Test
        @DisplayName("DORMITORY_COUNCIL 권한 요청을 정상적으로 접수한다")
        void it_accepts_dormitory_council_request() throws Exception {
            given(triggerManualDeviceSyncService.execute(any()))
                    .willReturn(new DeviceSyncTriggerResDto("접수되었습니다.", true, Instant.EPOCH, 1));

            mockMvc.perform(post(SYNC_PATH).with(user("council").authorities(authority("DORMITORY_COUNCIL")))
                    .contentType(MediaType.APPLICATION_JSON).content(SYNC_BODY)).andExpect(status().isOk());

            then(triggerManualDeviceSyncService).should(times(1)).execute(any());
        }
    }

    @Nested
    @DisplayName("OAuth 인증 시작 경로는")
    class Describe_oauth_authorize {

        @Test
        @DisplayName("관리자 전용이므로 비인증 요청을 거부하고 state를 생성하지 않는다")
        void it_denies_anonymous_request() throws Exception {
            mockMvc.perform(get(AUTHORIZE_PATH)).andExpect(status().isForbidden());

            then(stateStore).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("일반 사용자 권한 요청을 거부하고 state를 생성하지 않는다")
        void it_denies_student_request() throws Exception {
            mockMvc.perform(get(AUTHORIZE_PATH).with(user("student").authorities(authority("STUDENT"))))
                    .andExpect(status().isForbidden());

            then(stateStore).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("ADMIN 권한 요청에는 인증 URL을 반환하고 state를 저장한다")
        void it_accepts_admin_request() throws Exception {
            mockMvc.perform(get(AUTHORIZE_PATH).with(user("admin").authorities(authority("ADMIN"))))
                    .andExpect(status().isOk());

            then(stateStore).should(times(1)).save(anyString());
        }
    }

    @Nested
    @DisplayName("OAuth 콜백 경로는")
    class Describe_oauth_callback {

        @Test
        @DisplayName("비인증 리다이렉트 요청이라도 유효한 state이면 토큰 교환을 수행한다")
        void it_allows_anonymous_request_with_valid_state() throws Exception {
            given(stateStore.validateAndRemove("valid-state")).willReturn(true);

            mockMvc.perform(get(CALLBACK_PATH).param("code", "auth-code").param("state", "valid-state"))
                    .andExpect(status().isOk());

            then(exchangeSmartThingsTokenService).should(times(1)).execute(eq("auth-code"), anyString());
        }

        @Test
        @DisplayName("유효하지 않은 state이면 인가 거부가 아니라 state 검증 실패로 토큰 교환을 수행하지 않는다")
        void it_rejects_anonymous_request_with_invalid_state() throws Exception {
            given(stateStore.validateAndRemove("invalid-state")).willReturn(false);

            final var response = mockMvc
                    .perform(get(CALLBACK_PATH).param("code", "auth-code").param("state", "invalid-state")).andReturn()
                    .getResponse();

            assertThat(response.getStatus()).isNotEqualTo(HttpStatus.FORBIDDEN.value());
            then(stateStore).should(times(1)).validateAndRemove("invalid-state");
            then(exchangeSmartThingsTokenService).shouldHaveNoInteractions();
        }
    }

    private static org.springframework.security.core.GrantedAuthority authority(final String role) {
        return new org.springframework.security.core.authority.SimpleGrantedAuthority(role);
    }

    @TestConfiguration
    static class TestSecuritySupportConfig {

        /**
         * {@link SecurityConfig}가 {@code @Qualifier("configure")}로 주입받는 CORS 설정을 대체합니다.
         *
         * @return 테스트용 CORS 설정 소스
         */
        @Bean
        CorsConfigurationSource configure() {
            final var source = new UrlBasedCorsConfigurationSource();
            source.registerCorsConfiguration("/**", new CorsConfiguration());
            return source;
        }

        /**
         * 컨트롤러가 인증 URL을 조립할 때 사용하는 SmartThings 환경 설정을 제공합니다.
         *
         * @return 테스트용 SmartThings 환경 설정
         */
        @Bean
        SmartThingsEnvironment smartThingsEnvironment() {
            return new SmartThingsEnvironment(null,
                    null,
                    null,
                    "https://washer.test/api/v2/admin/smartthings/oauth/callback",
                    "test-client-id",
                    "test-client-secret");
        }
    }
}
