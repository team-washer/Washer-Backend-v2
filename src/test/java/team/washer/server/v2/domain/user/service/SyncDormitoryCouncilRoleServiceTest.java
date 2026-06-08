package team.washer.server.v2.domain.user.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.enums.UserRole;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.domain.user.service.impl.SyncDormitoryCouncilRoleServiceImpl;
import team.washer.server.v2.global.thirdparty.datagsm.config.DataGsmEnvironment;
import team.washer.server.v2.global.thirdparty.datagsm.dto.response.DataGsmStudentSearchResDto;
import team.washer.server.v2.global.thirdparty.datagsm.dto.response.DataGsmStudentSearchResDto.Data;
import team.washer.server.v2.global.thirdparty.datagsm.dto.response.DataGsmStudentSearchResDto.Student;
import team.washer.server.v2.global.thirdparty.datagsm.feign.DataGsmOpenApiClient;

@ExtendWith(MockitoExtension.class)
@DisplayName("SyncDormitoryCouncilRoleServiceImpl 클래스의")
class SyncDormitoryCouncilRoleServiceTest {

    @Mock
    private DataGsmOpenApiClient dataGsmOpenApiClient;

    @Mock
    private UserRepository userRepository;

    private SyncDormitoryCouncilRoleServiceImpl createService(final String apiKey) {
        final DataGsmEnvironment environment = new DataGsmEnvironment("client-id",
                "client-secret",
                "redirect-uri",
                apiKey);
        final TransactionTemplate transactionTemplate = new TransactionTemplate(mock(PlatformTransactionManager.class));
        return new SyncDormitoryCouncilRoleServiceImpl(dataGsmOpenApiClient,
                environment,
                userRepository,
                transactionTemplate);
    }

    private User createUser(final String studentId, final UserRole role) {
        return User.builder().name("김철수").studentId(studentId).roomNumber("301").grade(3).floor(3).penaltyCount(0)
                .role(role).build();
    }

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Test
        @DisplayName("API 키가 없으면 외부 호출 없이 0을 반환한다")
        void it_skips_without_api_key() {
            final SyncDormitoryCouncilRoleServiceImpl service = createService("");

            final int promoted = service.execute();

            assertThat(promoted).isZero();
            then(dataGsmOpenApiClient).shouldHaveNoInteractions();
            then(userRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("기숙사 관리 역할 학생과 일치하는 일반 사용자를 승격하고 승격 수를 반환한다")
        void it_promotes_matching_users() {
            final SyncDormitoryCouncilRoleServiceImpl service = createService("api-key");
            given(dataGsmOpenApiClient
                    .searchStudents(eq("api-key"), eq("DORMITORY_MANAGER"), eq(true), eq(0), anyInt()))
                    .willReturn(new DataGsmStudentSearchResDto("OK",
                            200,
                            "OK",
                            new Data(1,
                                    2L,
                                    List.of(new Student(1101, "DORMITORY_MANAGER"),
                                            new Student(1102, "DORMITORY_MANAGER")))));
            final User user1 = createUser("1101", UserRole.USER);
            final User user2 = createUser("1102", UserRole.USER);
            given(userRepository.findByRoleAndStudentIdIn(eq(UserRole.USER), anyCollection()))
                    .willReturn(List.of(user1, user2));

            final int promoted = service.execute();

            assertThat(promoted).isEqualTo(2);
            assertThat(user1.getRole()).isEqualTo(UserRole.DORMITORY_COUNCIL);
            assertThat(user2.getRole()).isEqualTo(UserRole.DORMITORY_COUNCIL);
        }

        @Test
        @DisplayName("여러 페이지에 걸친 학생을 모두 조회한다")
        void it_traverses_all_pages() {
            final SyncDormitoryCouncilRoleServiceImpl service = createService("api-key");
            given(dataGsmOpenApiClient
                    .searchStudents(eq("api-key"), eq("DORMITORY_MANAGER"), eq(true), eq(0), anyInt()))
                    .willReturn(new DataGsmStudentSearchResDto("OK",
                            200,
                            "OK",
                            new Data(2, 2L, List.of(new Student(1101, "DORMITORY_MANAGER")))));
            given(dataGsmOpenApiClient
                    .searchStudents(eq("api-key"), eq("DORMITORY_MANAGER"), eq(true), eq(1), anyInt()))
                    .willReturn(new DataGsmStudentSearchResDto("OK",
                            200,
                            "OK",
                            new Data(2, 2L, List.of(new Student(1102, "DORMITORY_MANAGER")))));
            given(userRepository.findByRoleAndStudentIdIn(eq(UserRole.USER), anyCollection()))
                    .willReturn(List.of(createUser("1101", UserRole.USER), createUser("1102", UserRole.USER)));

            final int promoted = service.execute();

            assertThat(promoted).isEqualTo(2);
            then(dataGsmOpenApiClient).should(times(2)).searchStudents(any(), any(), anyBoolean(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("기숙사 관리 역할 학생이 없으면 0을 반환한다")
        void it_returns_zero_when_no_managers() {
            final SyncDormitoryCouncilRoleServiceImpl service = createService("api-key");
            given(dataGsmOpenApiClient
                    .searchStudents(eq("api-key"), eq("DORMITORY_MANAGER"), eq(true), eq(0), anyInt()))
                    .willReturn(new DataGsmStudentSearchResDto("OK", 200, "OK", new Data(1, 0L, List.of())));

            final int promoted = service.execute();

            assertThat(promoted).isZero();
            then(userRepository).shouldHaveNoInteractions();
        }
    }
}
