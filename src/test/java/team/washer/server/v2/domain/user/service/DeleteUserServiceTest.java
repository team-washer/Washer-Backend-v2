package team.washer.server.v2.domain.user.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.domain.user.service.impl.DeleteUserServiceImpl;
import team.washer.server.v2.global.common.error.exception.ExpectedException;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeleteUserServiceImpl 클래스의")
class DeleteUserServiceTest {

    @InjectMocks
    private DeleteUserServiceImpl deleteUserService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ReservationRepository reservationRepository;

    private User createUser() {
        return User.builder().name("김철수").studentId("20210001").roomNumber("301").grade(3).floor(3).penaltyCount(0)
                .build();
    }

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Nested
        @DisplayName("활성 예약이 없는 사용자를 삭제할 때")
        class Context_without_active_reservations {

            @Test
            @DisplayName("사용자를 삭제해야 한다")
            void it_deletes_user() {
                // Given
                Long userId = 1L;
                User user = createUser();
                List<ReservationStatus> activeStatuses = List
                        .of(ReservationStatus.RESERVED, ReservationStatus.CONFIRMED, ReservationStatus.RUNNING);

                given(userRepository.findById(userId)).willReturn(Optional.of(user));
                given(reservationRepository.existsByUserAndStatusIn(user, activeStatuses)).willReturn(false);

                // When
                deleteUserService.execute(userId);

                // Then
                then(userRepository).should(times(1)).findById(userId);
                then(reservationRepository).should(times(1)).existsByUserAndStatusIn(user, activeStatuses);
                then(userRepository).should(times(1)).delete(user);
            }
        }

        @Nested
        @DisplayName("RESERVED 상태의 예약이 있는 사용자를 삭제하려 할 때")
        class Context_with_reserved_reservation {

            @Test
            @DisplayName("ExpectedException을 던져야 한다")
            void it_throws_expected_exception() {
                // Given
                Long userId = 1L;
                User user = createUser();
                List<ReservationStatus> activeStatuses = List
                        .of(ReservationStatus.RESERVED, ReservationStatus.CONFIRMED, ReservationStatus.RUNNING);

                given(userRepository.findById(userId)).willReturn(Optional.of(user));
                given(reservationRepository.existsByUserAndStatusIn(user, activeStatuses)).willReturn(true);

                // When & Then
                assertThatThrownBy(() -> deleteUserService.execute(userId)).isInstanceOf(ExpectedException.class)
                        .hasMessage("활성 예약이 있는 사용자는 삭제할 수 없습니다")
                        .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);

                then(userRepository).should(times(1)).findById(userId);
                then(reservationRepository).should(times(1)).existsByUserAndStatusIn(user, activeStatuses);
                then(userRepository).should(never()).delete(any(User.class));
            }
        }

        @Nested
        @DisplayName("CONFIRMED 상태의 예약이 있는 사용자를 삭제하려 할 때")
        class Context_with_confirmed_reservation {

            @Test
            @DisplayName("ExpectedException을 던져야 한다")
            void it_throws_expected_exception() {
                // Given
                Long userId = 1L;
                User user = createUser();
                List<ReservationStatus> activeStatuses = List
                        .of(ReservationStatus.RESERVED, ReservationStatus.CONFIRMED, ReservationStatus.RUNNING);

                given(userRepository.findById(userId)).willReturn(Optional.of(user));
                given(reservationRepository.existsByUserAndStatusIn(user, activeStatuses)).willReturn(true);

                // When & Then
                assertThatThrownBy(() -> deleteUserService.execute(userId)).isInstanceOf(ExpectedException.class)
                        .hasMessage("활성 예약이 있는 사용자는 삭제할 수 없습니다")
                        .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);

                then(userRepository).should(times(1)).findById(userId);
                then(reservationRepository).should(times(1)).existsByUserAndStatusIn(user, activeStatuses);
                then(userRepository).should(never()).delete(any(User.class));
            }
        }

        @Nested
        @DisplayName("RUNNING 상태의 예약이 있는 사용자를 삭제하려 할 때")
        class Context_with_running_reservation {

            @Test
            @DisplayName("ExpectedException을 던져야 한다")
            void it_throws_expected_exception() {
                // Given
                Long userId = 1L;
                User user = createUser();
                List<ReservationStatus> activeStatuses = List
                        .of(ReservationStatus.RESERVED, ReservationStatus.CONFIRMED, ReservationStatus.RUNNING);

                given(userRepository.findById(userId)).willReturn(Optional.of(user));
                given(reservationRepository.existsByUserAndStatusIn(user, activeStatuses)).willReturn(true);

                // When & Then
                assertThatThrownBy(() -> deleteUserService.execute(userId)).isInstanceOf(ExpectedException.class)
                        .hasMessage("활성 예약이 있는 사용자는 삭제할 수 없습니다")
                        .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);

                then(userRepository).should(times(1)).findById(userId);
                then(reservationRepository).should(times(1)).existsByUserAndStatusIn(user, activeStatuses);
                then(userRepository).should(never()).delete(any(User.class));
            }
        }

        @Nested
        @DisplayName("COMPLETED 상태의 예약만 있는 사용자를 삭제할 때")
        class Context_with_completed_reservations_only {

            @Test
            @DisplayName("사용자를 삭제해야 한다")
            void it_deletes_user() {
                // Given
                Long userId = 1L;
                User user = createUser();
                List<ReservationStatus> activeStatuses = List
                        .of(ReservationStatus.RESERVED, ReservationStatus.CONFIRMED, ReservationStatus.RUNNING);

                given(userRepository.findById(userId)).willReturn(Optional.of(user));
                given(reservationRepository.existsByUserAndStatusIn(user, activeStatuses)).willReturn(false);

                // When
                deleteUserService.execute(userId);

                // Then
                then(userRepository).should(times(1)).findById(userId);
                then(reservationRepository).should(times(1)).existsByUserAndStatusIn(user, activeStatuses);
                then(userRepository).should(times(1)).delete(user);
            }
        }

        @Nested
        @DisplayName("CANCELLED 상태의 예약만 있는 사용자를 삭제할 때")
        class Context_with_cancelled_reservations_only {

            @Test
            @DisplayName("사용자를 삭제해야 한다")
            void it_deletes_user() {
                // Given
                Long userId = 1L;
                User user = createUser();
                List<ReservationStatus> activeStatuses = List
                        .of(ReservationStatus.RESERVED, ReservationStatus.CONFIRMED, ReservationStatus.RUNNING);

                given(userRepository.findById(userId)).willReturn(Optional.of(user));
                given(reservationRepository.existsByUserAndStatusIn(user, activeStatuses)).willReturn(false);

                // When
                deleteUserService.execute(userId);

                // Then
                then(userRepository).should(times(1)).findById(userId);
                then(reservationRepository).should(times(1)).existsByUserAndStatusIn(user, activeStatuses);
                then(userRepository).should(times(1)).delete(user);
            }
        }

        @Nested
        @DisplayName("존재하지 않는 사용자 ID로 요청할 때")
        class Context_with_nonexistent_user_id {

            @Test
            @DisplayName("ExpectedException을 던져야 한다")
            void it_throws_expected_exception() {
                // Given
                Long userId = 999L;

                given(userRepository.findById(userId)).willReturn(Optional.empty());

                // When & Then
                assertThatThrownBy(() -> deleteUserService.execute(userId)).isInstanceOf(ExpectedException.class)
                        .hasMessage("사용자를 찾을 수 없습니다").hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);

                then(userRepository).should(times(1)).findById(userId);
                then(reservationRepository).should(never()).existsByUserAndStatusIn(any(User.class), anyList());
                then(userRepository).should(never()).delete(any(User.class));
            }
        }
    }
}
