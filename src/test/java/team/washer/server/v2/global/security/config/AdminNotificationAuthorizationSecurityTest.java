package team.washer.server.v2.global.security.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willReturn;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

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
import team.washer.server.v2.domain.notification.controller.AdminNotificationController;
import team.washer.server.v2.domain.notification.dto.response.TestPushNotificationResDto;
import team.washer.server.v2.domain.notification.service.SendTestPushNotificationService;
import team.washer.server.v2.global.common.error.handler.GlobalExceptionHandler;
import team.washer.server.v2.global.security.jwt.provider.JwtTokenProvider;

@WebMvcTest(controllers = AdminNotificationController.class)
@ImportAutoConfiguration({SecurityAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class})
@Import({SecurityConfig.class, DomainAuthorizationConfig.class, GlobalExceptionHandler.class,
        AdminNotificationAuthorizationSecurityTest.TestBeansConfiguration.class})
@DisplayName("관리자 테스트 푸시 발송의 인가 규칙은")
class AdminNotificationAuthorizationSecurityTest {

    private static final String TEST_PUSH_PATH = "/api/v2/admin/notifications/test";

    @Resource
    private MockMvc mockMvc;

    @MockitoBean
    private SendTestPushNotificationService sendTestPushNotificationService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private ObjectMapper objectMapper;

    @Nested
    @DisplayName("ADMIN 권한이 없는 요청이면")
    class Context_withoutAdminAuthority {

        @Test
        @DisplayName("비인증은 401, USER와 DORMITORY_COUNCIL은 403으로 거부한다")
        void rejectsNonAdminRequests() throws Exception {
            mockMvc.perform(post(TEST_PUSH_PATH).contentType("application/json").content("{}"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(post(TEST_PUSH_PATH).with(user("user").authorities(new SimpleGrantedAuthority("USER")))
                    .contentType("application/json").content("{}")).andExpect(status().isForbidden());
            mockMvc.perform(post(TEST_PUSH_PATH)
                    .with(user("council").authorities(new SimpleGrantedAuthority("DORMITORY_COUNCIL")))
                    .contentType("application/json").content("{}")).andExpect(status().isForbidden());

            then(sendTestPushNotificationService).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("ADMIN 권한의 요청이면")
    class Context_withAdminAuthority {

        @Test
        @DisplayName("테스트 푸시 발송을 허용한다")
        void allowsAdminRequest() throws Exception {
            willReturn(new TestPushNotificationResDto("2108",
                    "홍길동",
                    "projects/washer/messages/1",
                    LocalDateTime.of(2026, 1, 1, 0, 0))).given(sendTestPushNotificationService).execute(any());

            mockMvc.perform(post(TEST_PUSH_PATH).with(user("admin").authorities(new SimpleGrantedAuthority("ADMIN")))
                    .contentType("application/json").content("{\"studentId\":\"2108\"}")).andExpect(status().isOk());

            then(sendTestPushNotificationService).should().execute(any());
        }

        @Test
        @DisplayName("학번 형식이 올바르지 않으면 400으로 거부한다")
        void rejectsMalformedStudentId() throws Exception {
            mockMvc.perform(post(TEST_PUSH_PATH).with(user("admin").authorities(new SimpleGrantedAuthority("ADMIN")))
                    .contentType("application/json").content("{\"studentId\":\"abcd\"}"))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(post(TEST_PUSH_PATH).with(user("admin").authorities(new SimpleGrantedAuthority("ADMIN")))
                    .contentType("application/json").content("{\"studentId\":\"1\"}"))
                    .andExpect(status().isBadRequest());

            then(sendTestPushNotificationService).shouldHaveNoInteractions();
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
