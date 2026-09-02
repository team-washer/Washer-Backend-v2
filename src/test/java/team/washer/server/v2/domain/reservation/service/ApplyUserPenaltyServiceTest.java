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
import team.washer.server.v2.domain.notification.support.ReservationNotificationSupport;
import team.washer.server.v2.domain.reservation.service.impl.ApplyUserPenaltyServiceImpl;
import team.washer.server.v2.domain.reservation.util.PenaltyRedisUtil;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.enums.UserRole;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.global.security.provider.CurrentUserProvider;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApplyUserPenaltyServiceImpl 클래스의")
class ApplyUserPenaltyServiceTest {

    private static final Long ACTOR_ID = 1L;
    private static final Long TARGET_ID = 2L;
    private static final String TARGET_ROOM = "302";
    private static final String REASON = "세탁물 장기 방치로 기기 점유";

    @InjectMocks
    private ApplyUserPenaltyServiceImpl applyUserPenaltyService;

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

    private User createDormitoryCouncil() {
        return User.builder().name("자치위원").studentId("20210003").roomNumber("303").grade(3).floor(3)
                .role(UserRole.DORMITORY_COUNCIL).build();
    }

    private User createTarget() {
        return User.builder().name("대상사용자").studentId("20210002").roomNumber(TARGET_ROOM).grade(3).floor(3)
                .role(UserRole.USER).build();
    }

    private User createTargetWithoutRoom() {
        return User.builder().name("호실없음").studentId("20210004").grade(3).floor(3).role(UserRole.USER).build();
    }

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Nested
        @DisplayName("관리자가 패널티를 부과할 때")
        class Context_with_admin {

            @Test
            @DisplayName("호실에 48시간 차단을 적용하고 알림을 발송해야 한다")
            void it_applies_block_and_sends_notification() {
                // Given
                final var target = createTarget();
                given(currentUserProvider.getCurrentUserId()).willReturn(ACTOR_ID);
                given(userRepository.findById(ACTOR_ID)).willReturn(Optional.of(createAdmin()));
                given(userRepository.findById(TARGET_ID)).willReturn(Optional.of(target));
                given(penaltyRedisUtil.isBlocked(TARGET_ROOM)).willReturn(true);

                // When
                applyUserPenaltyService.execute(TARGET_ID, REASON);

                // Then
                then(penaltyRedisUtil).should(times(1)).applyBlock(TARGET_ROOM);
                then(reservationNotificationSupport).should(times(1)).sendAdminPenalty(target, REASON);
            }
        }

        @Nested
        @DisplayName("기숙사자치위원회가 패널티를 부과할 때")
        class Context_with_dormitory_council {

            @Test
            @DisplayName("관리자와 동일하게 패널티가 부과되어야 한다")
            void it_applies_block_for_dormitory_council() {
                // Given
                final var target = createTarget();
                given(currentUserProvider.getCurrentUserId()).willReturn(ACTOR_ID);
                given(userRepository.findById(ACTOR_ID)).willReturn(Optional.of(createDormitoryCouncil()));
                given(userRepository.findById(TARGET_ID)).willReturn(Optional.of(target));
                given(penaltyRedisUtil.isBlocked(TARGET_ROOM)).willReturn(true);

                // When
                applyUserPenaltyService.execute(TARGET_ID, REASON);

                // Then
                then(penaltyRedisUtil).should(times(1)).applyBlock(TARGET_ROOM);
                then(reservationNotificationSupport).should(times(1)).sendAdminPenalty(target, REASON);
            }
        }

        @Nested
        @DisplayName("자기 자신에게 패널티를 부과할 때")
        class Context_with_self_target {

            @Test
            @DisplayName("ExpectedException이 발생하고 BAD_REQUEST 상태를 반환해야 한다")
            void it_throws_bad_request_exception() {
                // Given
                given(currentUserProvider.getCurrentUserId()).willReturn(ACTOR_ID);
                given(userRepository.findById(ACTOR_ID)).willReturn(Optional.of(createAdmin()));

                // When & Then
                assertThatThrownBy(() -> applyUserPenaltyService.execute(ACTOR_ID, REASON))
                        .isInstanceOf(ExpectedException.class).hasMessage("자신에게는 패널티를 부과할 수 없습니다.")
                        .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                                .isEqualTo(HttpStatus.BAD_REQUEST));

                then(penaltyRedisUtil).shouldHaveNoInteractions();
                then(reservationNotificationSupport).shouldHaveNoInteractions();
            }
        }

        @Nested
        @DisplayName("존재하지 않는 부과자 ID로 요청할 때")
        class Context_with_nonexistent_actor {

            @Test
            @DisplayName("ExpectedException이 발생하고 NOT_FOUND 상태를 반환해야 한다")
            void it_throws_not_found_exception() {
                // Given
                given(currentUserProvider.getCurrentUserId()).willReturn(ACTOR_ID);
                given(userRepository.findById(ACTOR_ID)).willReturn(Optional.empty());

                // When & Then
                assertThatThrownBy(() -> applyUserPenaltyService.execute(TARGET_ID, REASON))
                        .isInstanceOf(ExpectedException.class).hasMessage("사용자를 찾을 수 없습니다.")
                        .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                                .isEqualTo(HttpStatus.NOT_FOUND));

                then(penaltyRedisUtil).shouldHaveNoInteractions();
                then(reservationNotificationSupport).shouldHaveNoInteractions();
            }
        }

        @Nested
        @DisplayName("존재하지 않는 대상 사용자에게 부과할 때")
        class Context_with_nonexistent_target {

            @Test
            @DisplayName("ExpectedException이 발생하고 NOT_FOUND 상태를 반환해야 한다")
            void it_throws_not_found_exception() {
                // Given
                given(currentUserProvider.getCurrentUserId()).willReturn(ACTOR_ID);
                given(userRepository.findById(ACTOR_ID)).willReturn(Optional.of(createAdmin()));
                given(userRepository.findById(TARGET_ID)).willReturn(Optional.empty());

                // When & Then
                assertThatThrownBy(() -> applyUserPenaltyService.execute(TARGET_ID, REASON))
                        .isInstanceOf(ExpectedException.class).hasMessage("사용자를 찾을 수 없습니다.")
                        .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                                .isEqualTo(HttpStatus.NOT_FOUND));

                then(penaltyRedisUtil).shouldHaveNoInteractions();
                then(reservationNotificationSupport).shouldHaveNoInteractions();
            }
        }

        @Nested
        @DisplayName("대상 사용자의 호실 정보가 없을 때")
        class Context_without_room_number {

            @Test
            @DisplayName("ExpectedException이 발생하고 NOT_FOUND 상태를 반환해야 한다")
            void it_throws_not_found_exception() {
                // Given
                given(currentUserProvider.getCurrentUserId()).willReturn(ACTOR_ID);
                given(userRepository.findById(ACTOR_ID)).willReturn(Optional.of(createAdmin()));
                given(userRepository.findById(TARGET_ID)).willReturn(Optional.of(createTargetWithoutRoom()));

                // When & Then
                assertThatThrownBy(() -> applyUserPenaltyService.execute(TARGET_ID, REASON))
                        .isInstanceOf(ExpectedException.class).hasMessage("호실 정보를 찾을 수 없습니다.")
                        .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                                .isEqualTo(HttpStatus.NOT_FOUND));

                then(penaltyRedisUtil).shouldHaveNoInteractions();
                then(reservationNotificationSupport).shouldHaveNoInteractions();
            }
        }

        @Nested
        @DisplayName("Redis 장애로 차단 적용에 실패했을 때")
        class Context_with_redis_failure {

            @Test
            @DisplayName("ExpectedException이 발생하고 알림을 발송하지 않아야 한다")
            void it_throws_and_skips_notification() {
                // Given
                given(currentUserProvider.getCurrentUserId()).willReturn(ACTOR_ID);
                given(userRepository.findById(ACTOR_ID)).willReturn(Optional.of(createAdmin()));
                given(userRepository.findById(TARGET_ID)).willReturn(Optional.of(createTarget()));
                given(penaltyRedisUtil.isBlocked(TARGET_ROOM)).willReturn(false);

                // When & Then
                assertThatThrownBy(() -> applyUserPenaltyService.execute(TARGET_ID, REASON))
                        .isInstanceOf(ExpectedException.class).hasMessage("패널티 부과에 실패했습니다. 잠시 후 다시 시도해 주세요.")
                        .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));

                then(penaltyRedisUtil).should(times(1)).applyBlock(TARGET_ROOM);
                then(reservationNotificationSupport).shouldHaveNoInteractions();
            }
        }
    }
}
