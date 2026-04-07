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
import team.washer.server.v2.domain.reservation.service.impl.ActivateSundayReservationServiceImpl;
import team.washer.server.v2.domain.reservation.util.SundayReservationRedisUtil;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.enums.UserRole;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.global.security.provider.CurrentUserProvider;

@ExtendWith(MockitoExtension.class)
@DisplayName("ActivateSundayReservationServiceImpl 클래스의")
class ActivateSundayReservationServiceTest {

    @InjectMocks
    private ActivateSundayReservationServiceImpl activateSundayReservationService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SundayReservationRedisUtil sundayReservationRedisUtil;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private User createAdmin() {
        return User.builder().name("관리자").studentId("20210001").roomNumber("301").grade(3).floor(3)
                .role(UserRole.ADMIN).build();
    }

    private User createDormCouncil() {
        return User.builder().name("기숙사자치위원").studentId("20210002").roomNumber("302").grade(3).floor(3)
                .role(UserRole.DORMITORY_COUNCIL).build();
    }

    private User createUser() {
        return User.builder().name("일반사용자").studentId("20210003").roomNumber("303").grade(3).floor(3)
                .role(UserRole.USER).build();
    }

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Nested
        @DisplayName("관리자가 일요일 예약을 활성화할 때")
        class Context_with_admin_activating {

            @Test
            @DisplayName("정상적으로 활성화해야 한다")
            void it_activates_sunday_reservation() {
                // Given
                var adminId = 1L;
                var admin = createAdmin();
                given(currentUserProvider.getCurrentUserId()).willReturn(adminId);
                given(userRepository.findById(adminId)).willReturn(Optional.of(admin));

                // When
                activateSundayReservationService.execute("테스트 활성화");

                // Then
                then(sundayReservationRedisUtil).should(times(1)).persistActivation(admin, "테스트 활성화", true);
            }
        }

        @Nested
        @DisplayName("기숙사자치위원회가 일요일 예약을 활성화할 때")
        class Context_with_dormitory_council_activating {

            @Test
            @DisplayName("정상적으로 활성화해야 한다")
            void it_activates_sunday_reservation() {
                // Given
                var councilId = 2L;
                var council = createDormCouncil();
                given(currentUserProvider.getCurrentUserId()).willReturn(councilId);
                given(userRepository.findById(councilId)).willReturn(Optional.of(council));

                // When
                activateSundayReservationService.execute(null);

                // Then
                then(sundayReservationRedisUtil).should(times(1)).persistActivation(council, null, true);
            }
        }

        @Nested
        @DisplayName("일반 사용자가 일요일 예약을 활성화하려 할 때")
        class Context_with_regular_user_activating {

            @Test
            @DisplayName("ExpectedException이 발생하고 FORBIDDEN 상태를 반환해야 한다")
            void it_throws_forbidden_exception() {
                // Given
                var userId = 3L;
                var user = createUser();
                given(currentUserProvider.getCurrentUserId()).willReturn(userId);
                given(userRepository.findById(userId)).willReturn(Optional.of(user));

                // When & Then
                assertThatThrownBy(() -> activateSundayReservationService.execute("메모"))
                        .isInstanceOf(ExpectedException.class)
                        .hasMessage("일요일 예약 관리 권한이 없습니다")
                        .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                                .isEqualTo(HttpStatus.FORBIDDEN));

                then(sundayReservationRedisUtil).shouldHaveNoInteractions();
            }
        }

        @Nested
        @DisplayName("존재하지 않는 사용자가 요청할 때")
        class Context_with_nonexistent_user {

            @Test
            @DisplayName("ExpectedException이 발생하고 NOT_FOUND 상태를 반환해야 한다")
            void it_throws_not_found_exception() {
                // Given
                var userId = 999L;
                given(currentUserProvider.getCurrentUserId()).willReturn(userId);
                given(userRepository.findById(userId)).willReturn(Optional.empty());

                // When & Then
                assertThatThrownBy(() -> activateSundayReservationService.execute("메모"))
                        .isInstanceOf(ExpectedException.class)
                        .hasMessage("사용자를 찾을 수 없습니다")
                        .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                                .isEqualTo(HttpStatus.NOT_FOUND));

                then(sundayReservationRedisUtil).shouldHaveNoInteractions();
            }
        }
    }
}
