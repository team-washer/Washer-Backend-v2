package team.washer.server.v2.domain.reservation.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDateTime;
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
import team.washer.server.v2.domain.notification.support.ReservationNotificationSupport;
import team.washer.server.v2.domain.reservation.service.impl.ExtendCancellationBlockServiceImpl;
import team.washer.server.v2.domain.reservation.util.PenaltyRedisUtil;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.enums.UserRole;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.global.security.provider.CurrentUserProvider;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExtendCancellationBlockServiceImpl 클래스의")
class ExtendCancellationBlockServiceTest {

    @InjectMocks
    private ExtendCancellationBlockServiceImpl extendCancellationBlockService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PenaltyRedisUtil penaltyRedisUtil;

    @Mock
    private ReservationNotificationSupport reservationNotificationSupport;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private User createAdmin() {
        return User.builder().name("관리자").studentId("20210001").roomNumber("301").grade(3).floor(3).role(UserRole.ADMIN)
                .build();
    }

    private User createUser() {
        return User.builder().name("일반사용자").studentId("20210002").roomNumber("302").grade(3).floor(3)
                .role(UserRole.USER).build();
    }

    private User createUserWithoutRoom() {
        return User.builder().name("호실없는사용자").studentId("20210003").grade(3).floor(3).role(UserRole.USER).build();
    }

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Nested
        @DisplayName("관리자가 활성 차단 중인 호실 사용자에게 연장 요청할 때")
        class Context_with_valid_block_extension {

            @Test
            @DisplayName("차단 기간을 연장하고 사용자에게 알림을 전송해야 한다")
            void it_extends_block_and_sends_notification() {
                // Given
                var adminId = 1L;
                var targetId = 2L;
                var days = 2;
                var newExpiry = LocalDateTime.now().plusDays(days);
                var admin = createAdmin();
                var target = createUser();

                given(currentUserProvider.getCurrentUserId()).willReturn(adminId);
                given(userRepository.findById(adminId)).willReturn(Optional.of(admin));
                given(userRepository.findById(targetId)).willReturn(Optional.of(target));
                given(penaltyRedisUtil.extendBlock(target.getRoomNumber(), days)).willReturn(newExpiry);

                // When
                extendCancellationBlockService.execute(targetId, days);

                // Then
                then(penaltyRedisUtil).should(times(1)).extendBlock(target.getRoomNumber(), days);
                then(reservationNotificationSupport).should(times(1)).sendBlockExtension(target, newExpiry);
            }
        }

        @Nested
        @DisplayName("일반 사용자가 연장을 시도할 때")
        class Context_with_regular_user_extending {

            @Test
            @DisplayName("ExpectedException이 발생하고 FORBIDDEN 상태를 반환해야 한다")
            void it_throws_forbidden_exception() {
                // Given
                var userId = 2L;
                var user = createUser();
                given(currentUserProvider.getCurrentUserId()).willReturn(userId);
                given(userRepository.findById(userId)).willReturn(Optional.of(user));

                // When & Then
                assertThatThrownBy(() -> extendCancellationBlockService.execute(3L, 1))
                        .isInstanceOf(ExpectedException.class).hasMessage("관리자 권한이 필요합니다.")
                        .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                                .isEqualTo(HttpStatus.FORBIDDEN));

                then(penaltyRedisUtil).shouldHaveNoInteractions();
                then(reservationNotificationSupport).shouldHaveNoInteractions();
            }
        }

        @Nested
        @DisplayName("존재하지 않는 관리자 ID로 요청할 때")
        class Context_with_nonexistent_admin {

            @Test
            @DisplayName("ExpectedException이 발생하고 NOT_FOUND 상태를 반환해야 한다")
            void it_throws_not_found_for_admin() {
                // Given
                var adminId = 999L;
                given(currentUserProvider.getCurrentUserId()).willReturn(adminId);
                given(userRepository.findById(adminId)).willReturn(Optional.empty());

                // When & Then
                assertThatThrownBy(() -> extendCancellationBlockService.execute(1L, 1))
                        .isInstanceOf(ExpectedException.class).hasMessage("사용자를 찾을 수 없습니다.")
                        .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                                .isEqualTo(HttpStatus.NOT_FOUND));

                then(penaltyRedisUtil).shouldHaveNoInteractions();
                then(reservationNotificationSupport).shouldHaveNoInteractions();
            }
        }

        @Nested
        @DisplayName("존재하지 않는 대상 사용자 ID로 요청할 때")
        class Context_with_nonexistent_target_user {

            @Test
            @DisplayName("ExpectedException이 발생하고 NOT_FOUND 상태를 반환해야 한다")
            void it_throws_not_found_for_target() {
                // Given
                var adminId = 1L;
                var targetId = 999L;
                var admin = createAdmin();
                given(currentUserProvider.getCurrentUserId()).willReturn(adminId);
                given(userRepository.findById(adminId)).willReturn(Optional.of(admin));
                given(userRepository.findById(targetId)).willReturn(Optional.empty());

                // When & Then
                assertThatThrownBy(() -> extendCancellationBlockService.execute(targetId, 1))
                        .isInstanceOf(ExpectedException.class).hasMessage("사용자를 찾을 수 없습니다.")
                        .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                                .isEqualTo(HttpStatus.NOT_FOUND));

                then(penaltyRedisUtil).shouldHaveNoInteractions();
                then(reservationNotificationSupport).shouldHaveNoInteractions();
            }
        }

        @Nested
        @DisplayName("호실 정보가 없는 사용자에게 요청할 때")
        class Context_with_user_without_room {

            @Test
            @DisplayName("ExpectedException이 발생하고 NOT_FOUND 상태를 반환해야 한다")
            void it_throws_not_found_for_room() {
                // Given
                var adminId = 1L;
                var targetId = 2L;
                var admin = createAdmin();
                var target = createUserWithoutRoom();
                given(currentUserProvider.getCurrentUserId()).willReturn(adminId);
                given(userRepository.findById(adminId)).willReturn(Optional.of(admin));
                given(userRepository.findById(targetId)).willReturn(Optional.of(target));

                // When & Then
                assertThatThrownBy(() -> extendCancellationBlockService.execute(targetId, 1))
                        .isInstanceOf(ExpectedException.class).hasMessage("호실 정보를 찾을 수 없습니다.")
                        .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                                .isEqualTo(HttpStatus.NOT_FOUND));

                then(penaltyRedisUtil).shouldHaveNoInteractions();
                then(reservationNotificationSupport).shouldHaveNoInteractions();
            }
        }

        @Nested
        @DisplayName("활성 차단이 없는 호실에 연장 요청할 때")
        class Context_with_no_active_block {

            @Test
            @DisplayName("ExpectedException이 발생하고 BAD_REQUEST 상태를 반환해야 한다")
            void it_throws_bad_request_when_no_active_block() {
                // Given
                var adminId = 1L;
                var targetId = 2L;
                var admin = createAdmin();
                var target = createUser();
                given(currentUserProvider.getCurrentUserId()).willReturn(adminId);
                given(userRepository.findById(adminId)).willReturn(Optional.of(admin));
                given(userRepository.findById(targetId)).willReturn(Optional.of(target));
                given(penaltyRedisUtil.extendBlock(target.getRoomNumber(), 1))
                        .willThrow(new ExpectedException("활성화된 예약 차단이 없습니다.", HttpStatus.BAD_REQUEST));

                // When & Then
                assertThatThrownBy(() -> extendCancellationBlockService.execute(targetId, 1))
                        .isInstanceOf(ExpectedException.class).hasMessage("활성화된 예약 차단이 없습니다.")
                        .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                                .isEqualTo(HttpStatus.BAD_REQUEST));

                then(reservationNotificationSupport).shouldHaveNoInteractions();
            }
        }
    }
}
