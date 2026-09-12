package team.washer.server.v2.global.security.config;

import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willReturn;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.Resource;
import team.washer.server.v2.domain.smartthings.controller.AdminSmartThingsDeviceController;
import team.washer.server.v2.domain.smartthings.controller.AdminSmartThingsOAuthController;
import team.washer.server.v2.domain.smartthings.dto.response.DeviceSyncTriggerResDto;
import team.washer.server.v2.domain.smartthings.service.ExchangeSmartThingsTokenService;
import team.washer.server.v2.domain.smartthings.service.TriggerManualDeviceSyncService;
import team.washer.server.v2.global.common.error.handler.GlobalExceptionHandler;
import team.washer.server.v2.global.security.jwt.provider.JwtTokenProvider;
import team.washer.server.v2.global.thirdparty.smartthings.SmartThingsOAuthStateStore;
import team.washer.server.v2.global.thirdparty.smartthings.config.SmartThingsEnvironment;

@WebMvcTest(controllers = {AdminSmartThingsDeviceController.class, AdminSmartThingsOAuthController.class})
@ImportAutoConfiguration({SecurityAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class})
@Import({DomainAuthorizationConfig.class, SmartThingsOAuthStateStore.class, GlobalExceptionHandler.class,
        SmartThingsAuthorizationSecurityTest.TestSecurityConfig.class})
class SmartThingsAuthorizationSecurityTest {

    private static final String SYNC_PATH = "/api/v2/admin/smartthings/devices/sync";
    private static final String AUTHORIZE_PATH = "/api/v2/admin/smartthings/oauth/authorize";
    private static final String CALLBACK_PATH = "/api/v2/admin/smartthings/oauth/callback";
    private static final String REDIRECT_URI = "https://example.test/oauth/callback";

    @Resource
    private MockMvc mockMvc;

    @Resource
    private SmartThingsOAuthStateStore stateStore;

    @MockitoBean
    private TriggerManualDeviceSyncService triggerManualDeviceSyncService;

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

    @Test
    void 비인증과_일반_사용자는_수동_동기화를_실행할_수_없다() throws Exception {
        mockMvc.perform(post(SYNC_PATH).contentType("application/json").content("{\"confirmed\":true}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(SYNC_PATH).with(user("user").authorities(new SimpleGrantedAuthority("USER")))
                .contentType("application/json").content("{\"confirmed\":true}")).andExpect(status().isForbidden());

        then(triggerManualDeviceSyncService).shouldHaveNoInteractions();
    }

    @Test
    void 관리자_권한은_수동_동기화를_실행할_수_있다() throws Exception {
        willReturn(new DeviceSyncTriggerResDto("접수됨", true, Instant.parse("2026-01-01T00:00:00Z"), 1))
                .given(triggerManualDeviceSyncService).execute(org.mockito.ArgumentMatchers.any());

        mockMvc.perform(post(SYNC_PATH).with(user("admin").authorities(new SimpleGrantedAuthority("ADMIN")))
                .contentType("application/json").content("{\"confirmed\":true}")).andExpect(status().isOk());

        then(triggerManualDeviceSyncService).should().execute(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void authorize_경로는_관리자_인증이_필요하다() throws Exception {
        mockMvc.perform(get(AUTHORIZE_PATH)).andExpect(status().isForbidden());
        mockMvc.perform(get(AUTHORIZE_PATH).with(user("user").authorities(new SimpleGrantedAuthority("USER"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(AUTHORIZE_PATH).with(user("admin").authorities(new SimpleGrantedAuthority("ADMIN"))))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("state=")));
    }

    @Test
    void callback_은_인증_없이_유효한_state를_한_번만_처리한다() throws Exception {
        stateStore.save("test-state");

        mockMvc.perform(get(CALLBACK_PATH).param("code", "test-code").param("state", "test-state"))
                .andExpect(status().isOk());
        mockMvc.perform(get(CALLBACK_PATH).param("code", "test-code").param("state", "test-state"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"code\":401")));

        then(exchangeSmartThingsTokenService).should().execute("test-code", REDIRECT_URI);
    }

    @TestConfiguration
    static class TestSecurityConfig {

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http, DomainAuthorizationConfig domainAuthorizationConfig)
                throws Exception {
            return http.csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(domainAuthorizationConfig::configure).build();
        }
    }
}
