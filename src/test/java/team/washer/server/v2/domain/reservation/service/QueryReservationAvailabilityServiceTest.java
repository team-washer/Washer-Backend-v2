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

import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.reservation.service.impl.QueryReservationAvailabilityServiceImpl;
import team.washer.server.v2.domain.reservation.util.PenaltyRedisUtil;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.global.security.provider.CurrentUserProvider;

@ExtendWith(MockitoExtension.class)
@DisplayName("QueryReservationAvailabilityServiceImpl 클래스의")
class QueryReservationAvailabilityServiceTest {

    @InjectMocks
    private QueryReservationAvailabilityServiceImpl queryReservationAvailabilityService;

    @Mock
    private PenaltyRedisUtil penaltyRedisUtil;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private User createUser(final String roomNumber) {
        return User.builder().name("김철수").studentId("20210001").roomNumber(roomNumber).grade(3).floor(3).build();
    }

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Nested
        @DisplayName("쿨다운/블록이 없는 사용자가 조회할 때")
        class Context_with_available_user {

            @Test
            @DisplayName("예약 가능 상태를 반환해야 한다")
            void it_returns_available() {
                // Given
                var userId = 1L;
                var user = createUser("301");
                given(currentUserProvider.getCurrentUserId()).willReturn(userId);
                given(userRepository.findById(userId)).willReturn(Optional.of(user));
                given(penaltyRedisUtil.isBlocked("301")).willReturn(false);
                given(penaltyRedisUtil.isInCooldown(userId, MachineType.WASHER)).willReturn(false);

                // When
                var result = queryReservationAvailabilityService.execute();

                // Then
                assertThat(result).isNotNull();
                assertThat(result.canReserve()).isTrue();
                assertThat(result.penaltyExpiresAt()).isNull();
            }
        }

        @Nested
        @DisplayName("일부 유형(세탁기)만 쿨다운 중인 사용자가 조회할 때")
        class Context_with_single_type_cooldown {

            @Test
            @DisplayName("다른 유형은 예약 가능하므로 예약 가능 상태를 반환해야 한다")
            void it_returns_available_when_other_type_is_free() {
                // Given
                var userId = 1L;
                var user = createUser("301");
                given(currentUserProvider.getCurrentUserId()).willReturn(userId);
                given(userRepository.findById(userId)).willReturn(Optional.of(user));
                given(penaltyRedisUtil.isBlocked("301")).willReturn(false);
                given(penaltyRedisUtil.isInCooldown(userId, MachineType.WASHER)).willReturn(true);
                given(penaltyRedisUtil.isInCooldown(userId, MachineType.DRYER)).willReturn(false);

                // When
                var result = queryReservationAvailabilityService.execute();

                // Then
                assertThat(result.canReserve()).isTrue();
                assertThat(result.penaltyExpiresAt()).isNull();
            }
        }

        @Nested
        @DisplayName("모든 유형이 쿨다운 중인 사용자가 조회할 때")
        class Context_with_all_types_cooldown {

            @Test
            @DisplayName("예약 불가 상태를 반환해야 한다")
            void it_returns_unavailable_when_all_types_cooled() {
                // Given
                var userId = 1L;
                var user = createUser("301");
                var penaltyExpiresAt = LocalDateTime.now().plusMinutes(5);
                given(currentUserProvider.getCurrentUserId()).willReturn(userId);
                given(userRepository.findById(userId)).willReturn(Optional.of(user));
                given(penaltyRedisUtil.isBlocked("301")).willReturn(false);
                given(penaltyRedisUtil.isInCooldown(userId, MachineType.WASHER)).willReturn(true);
                given(penaltyRedisUtil.isInCooldown(userId, MachineType.DRYER)).willReturn(true);
                given(penaltyRedisUtil.getPenaltyExpiryTime(userId)).willReturn(penaltyExpiresAt);

                // When
                var result = queryReservationAvailabilityService.execute();

                // Then
                assertThat(result.canReserve()).isFalse();
                assertThat(result.penaltyExpiresAt()).isEqualTo(penaltyExpiresAt);
            }
        }

        @Nested
        @DisplayName("48시간 블록 중인 호실의 사용자가 조회할 때")
        class Context_with_blocked_room {

            @Test
            @DisplayName("예약 불가 상태를 반환해야 한다")
            void it_returns_unavailable_due_to_block() {
                // Given
                var userId = 1L;
                var user = createUser("301");
                given(currentUserProvider.getCurrentUserId()).willReturn(userId);
                given(userRepository.findById(userId)).willReturn(Optional.of(user));
                given(penaltyRedisUtil.isBlocked("301")).willReturn(true);

                // When
                var result = queryReservationAvailabilityService.execute();

                // Then
                assertThat(result.canReserve()).isFalse();
            }
        }
    }
}
