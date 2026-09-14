package team.washer.server.v2.global.security.config;

import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.times;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.cors.CorsConfigurationSource;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.Resource;
import team.washer.server.v2.domain.smartthings.controller.AdminSmartThingsDeviceController;
import team.washer.server.v2.domain.smartthings.controller.AdminSmartThingsOAuthController;
import team.washer.server.v2.domain.smartthings.controller.SmartThingsTokenController;
import team.washer.server.v2.domain.smartthings.dto.response.DeviceSyncTriggerResDto;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsAccessTokenResDto;
import team.washer.server.v2.domain.smartthings.service.ExchangeSmartThingsTokenService;
import team.washer.server.v2.domain.smartthings.service.QuerySmartThingsAccessTokenService;
import team.washer.server.v2.domain.smartthings.service.TriggerManualDeviceSyncService;
import team.washer.server.v2.global.common.error.handler.GlobalExceptionHandler;
import team.washer.server.v2.global.security.jwt.provider.JwtTokenProvider;
import team.washer.server.v2.global.thirdparty.smartthings.SmartThingsOAuthStateStore;
import team.washer.server.v2.global.thirdparty.smartthings.config.SmartThingsEnvironment;

@WebMvcTest(controllers = {AdminSmartThingsDeviceController.class, AdminSmartThingsOAuthController.class,
        SmartThingsTokenController.class})
@ImportAutoConfiguration({SecurityAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class})
@Import({SecurityConfig.class, DomainAuthorizationConfig.class, SmartThingsOAuthStateStore.class,
        GlobalExceptionHandler.class, SmartThingsAuthorizationSecurityTest.TestBeansConfiguration.class})
@DisplayName("SmartThings 관리자 기능의 인가 규칙은")
class SmartThingsAuthorizationSecurityTest {

    private static final String SYNC_PATH = "/api/v2/admin/smartthings/devices/sync";
    private static final String AUTHORIZE_PATH = "/api/v2/admin/smartthings/oauth/authorize";
    private static final String CALLBACK_PATH = "/api/v2/admin/smartthings/oauth/callback";
    private static final String TOKEN_PATH = "/api/v2/smartthings/token";
    private static final String REDIRECT_URI = "https://example.test/oauth/callback";

    @Resource
    private MockMvc mockMvc;

    @Resource
    private SmartThingsOAuthStateStore stateStore;

    @MockitoBean
    private TriggerManualDeviceSyncService triggerManualDeviceSyncService;

    @MockitoBean
    private QuerySmartThingsAccessTokenService querySmartThingsAccessTokenService;

    @MockitoBean
    private SmartThingsEnvironment smartThingsEnvironment;

    @MockitoBean
    private ExchangeSmartThingsTokenService exchangeSmartThingsTokenService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        willReturn("https://smartthings.example/authorize").given(smartThingsEnvironment).authorizeUrl();
        willReturn(REDIRECT_URI).given(smartThingsEnvironment).redirectUri();
        willReturn("client-id").given(smartThingsEnvironment).clientId();
    }

    @Nested
    @DisplayName("수동 기기 동기화는")
    class Describe_manualSync {

        @Test
        @DisplayName("비인증 사용자와 일반 사용자의 요청을 거부한다")
        void rejectsUnauthenticatedAndUserRequests() throws Exception {
            mockMvc.perform(post(SYNC_PATH).contentType("application/json").content("{\"confirmed\":true}"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post(SYNC_PATH).with(user("user").authorities(new SimpleGrantedAuthority("USER")))
                    .contentType("application/json").content("{\"confirmed\":true}")).andExpect(status().isForbidden());

            then(triggerManualDeviceSyncService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("관리자급 권한의 요청을 허용한다")
        void allowsManagerRequests() throws Exception {
            willReturn(new DeviceSyncTriggerResDto("접수됨", true, Instant.parse("2026-01-01T00:00:00Z"), 1))
                    .given(triggerManualDeviceSyncService).execute(org.mockito.ArgumentMatchers.any());

            mockMvc.perform(
                    post(SYNC_PATH).with(user("council").authorities(new SimpleGrantedAuthority("DORMITORY_COUNCIL")))
                            .contentType("application/json").content("{\"confirmed\":true}"))
                    .andExpect(status().isOk());
            mockMvc.perform(post(SYNC_PATH).with(user("admin").authorities(new SimpleGrantedAuthority("ADMIN")))
                    .contentType("application/json").content("{\"confirmed\":true}")).andExpect(status().isOk());

            then(triggerManualDeviceSyncService).should(times(2)).execute(org.mockito.ArgumentMatchers.any());
        }
    }

    @Nested
    @DisplayName("OAuth 인증 시작은")
    class Describe_authorize {

        @Test
        @DisplayName("일반 사용자의 요청을 거부하고 관리자급 권한을 허용한다")
        void requiresManagerAuthority() throws Exception {
            mockMvc.perform(get(AUTHORIZE_PATH)).andExpect(status().isForbidden());
            mockMvc.perform(get(AUTHORIZE_PATH).with(user("user").authorities(new SimpleGrantedAuthority("USER"))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(AUTHORIZE_PATH)
                    .with(user("council").authorities(new SimpleGrantedAuthority("DORMITORY_COUNCIL"))))
                    .andExpect(status().isOk());
            mockMvc.perform(get(AUTHORIZE_PATH).with(user("admin").authorities(new SimpleGrantedAuthority("ADMIN"))))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("state=")));
        }
    }

    @Nested
    @DisplayName("SmartThings 액세스 토큰 조회는")
    class Describe_accessToken {

        @Test
        @DisplayName("관리자급 권한이 없는 요청을 거부한다")
        void rejectsNonManagerRequests() throws Exception {
            mockMvc.perform(get(TOKEN_PATH)).andExpect(status().isForbidden());
            mockMvc.perform(get(TOKEN_PATH).with(user("user").authorities(new SimpleGrantedAuthority("USER"))))
                    .andExpect(status().isForbidden());

            then(querySmartThingsAccessTokenService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("관리자급 권한의 요청을 허용한다")
        void allowsManagerRequests() throws Exception {
            willReturn(new SmartThingsAccessTokenResDto("test-access-token", LocalDateTime.of(2026, 1, 1, 0, 0)))
                    .given(querySmartThingsAccessTokenService).execute();

            mockMvc.perform(
                    get(TOKEN_PATH).with(user("council").authorities(new SimpleGrantedAuthority("DORMITORY_COUNCIL"))))
                    .andExpect(status().isOk());
            mockMvc.perform(get(TOKEN_PATH).with(user("admin").authorities(new SimpleGrantedAuthority("ADMIN"))))
                    .andExpect(status().isOk());

            then(querySmartThingsAccessTokenService).should(times(2)).execute();
        }
    }

    @Nested
    @DisplayName("OAuth callback은")
    class Describe_callback {

        @Test
        @DisplayName("인증 없이 유효한 state를 한 번만 처리한다")
        void processesValidStateOnlyOnce() throws Exception {
            stateStore.save("test-state");

            mockMvc.perform(get(CALLBACK_PATH).param("code", "test-code").param("state", "test-state"))
                    .andExpect(status().isOk());
            mockMvc.perform(get(CALLBACK_PATH).param("code", "test-code").param("state", "test-state"))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("\"code\":401")));

            then(exchangeSmartThingsTokenService).should().execute("test-code", REDIRECT_URI);
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
