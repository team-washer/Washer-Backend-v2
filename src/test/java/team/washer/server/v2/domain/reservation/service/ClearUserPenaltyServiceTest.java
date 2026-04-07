package team.washer.server.v2.domain.reservation.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.reservation.service.impl.ClearUserPenaltyServiceImpl;
import team.washer.server.v2.domain.reservation.util.PenaltyRedisUtil;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.enums.UserRole;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.global.security.provider.CurrentUserProvider;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClearUserPenaltyServiceImpl 클래스의")
class ClearUserPenaltyServiceTest {

    @InjectMocks
    private ClearUserPenaltyServiceImpl clearUserPenaltyService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PenaltyRedisUtil penaltyRedisUtil;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private User createAdmin() {
        return User.builder().name("관리자").studentId("20210001").roomNumber("301").grade(3).floor(3)
                .role(UserRole.ADMIN).build();
    }

    private User createUser() {
        return User.builder().name("일반사용자").studentId("20210002").roomNumber("302").grade(3).floor(3)
                .role(UserRole.USER).build();
    }

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Nested
        @DisplayName("관리자가 사용자 패널티를 초기화할 때")
        class Context_with_admin_clearing_penalty {

            @Test
            @DisplayName("패널티를 정상적으로 초기화해야 한다")
            void it_clears_penalty() {
                // Given
                var adminId = 1L;
                var targetUserId = 2L;
                var admin = createAdmin();
                given(currentUserProvider.getCurrentUserId()).willReturn(adminId);
                given(userRepository.findById(adminId)).willReturn(Optional.of(admin));

                // When
                clearUserPenaltyService.execute(targetUserId);

                // Then
                then(penaltyRedisUtil).should(times(1)).clearPenalty(targetUserId);
            }
        }

        @Nested
        @DisplayName("일반 사용자가 패널티 초기화를 시도할 때")
        class Context_with_regular_user_clearing {

            @Test
            @DisplayName("ExpectedException이 발생하고 FORBIDDEN 상태를 반환해야 한다")
            void it_throws_forbidden_exception() {
                // Given
                var userId = 2L;
                var user = createUser();
                given(currentUserProvider.getCurrentUserId()).willReturn(userId);
                given(userRepository.findById(userId)).willReturn(Optional.of(user));

                // When & Then
                assertThatThrownBy(() -> clearUserPenaltyService.execute(3L))
                        .isInstanceOf(ExpectedException.class)
                        .hasMessage("관리자 권한이 필요합니다")
                        .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                                .isEqualTo(HttpStatus.FORBIDDEN));

                then(penaltyRedisUtil).shouldHaveNoInteractions();
            }
        }

        @Nested
        @DisplayName("존재하지 않는 관리자 ID로 요청할 때")
        class Context_with_nonexistent_admin {

            @Test
            @DisplayName("ExpectedException이 발생하고 NOT_FOUND 상태를 반환해야 한다")
            void it_throws_not_found_exception() {
                // Given
                var adminId = 999L;
                given(currentUserProvider.getCurrentUserId()).willReturn(adminId);
                given(userRepository.findById(adminId)).willReturn(Optional.empty());

                // When & Then
                assertThatThrownBy(() -> clearUserPenaltyService.execute(1L))
                        .isInstanceOf(ExpectedException.class)
                        .hasMessage("사용자를 찾을 수 없습니다")
                        .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                                .isEqualTo(HttpStatus.NOT_FOUND));

                then(penaltyRedisUtil).shouldHaveNoInteractions();
            }
        }
    }
}
