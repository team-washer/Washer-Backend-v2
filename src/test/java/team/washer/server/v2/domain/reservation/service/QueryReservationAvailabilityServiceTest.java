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
        @DisplayName("패널티가 없고 쿨다운/블록도 없는 사용자가 조회할 때")
        class Context_with_available_user {

            @Test
            @DisplayName("예약 가능 상태를 반환해야 한다")
            void it_returns_available() {
                // Given
                var userId = 1L;
                var user = createUser("301");
                given(currentUserProvider.getCurrentUserId()).willReturn(userId);
                given(userRepository.findById(userId)).willReturn(Optional.of(user));
                given(penaltyRedisUtil.isInCooldown(userId)).willReturn(false);
                given(penaltyRedisUtil.isBlocked("301")).willReturn(false);
                given(penaltyRedisUtil.getPenaltyExpiryTime(userId)).willReturn(null);

                // When
                var result = queryReservationAvailabilityService.execute();

                // Then
                assertThat(result).isNotNull();
                assertThat(result.canReserve()).isTrue();
                assertThat(result.penaltyExpiresAt()).isNull();
            }
        }

        @Nested
        @DisplayName("쿨다운 중인 사용자가 조회할 때")
        class Context_with_cooldown_user {

            @Test
            @DisplayName("예약 불가 상태를 반환해야 한다")
            void it_returns_unavailable_due_to_cooldown() {
                // Given
                var userId = 1L;
                var user = createUser("301");
                given(currentUserProvider.getCurrentUserId()).willReturn(userId);
                given(userRepository.findById(userId)).willReturn(Optional.of(user));
                given(penaltyRedisUtil.isInCooldown(userId)).willReturn(true);

                // When
                var result = queryReservationAvailabilityService.execute();

                // Then
                assertThat(result).isNotNull();
                assertThat(result.canReserve()).isFalse();
                assertThat(result.penaltyExpiresAt()).isNull();
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
                given(penaltyRedisUtil.isInCooldown(userId)).willReturn(false);
                given(penaltyRedisUtil.isBlocked("301")).willReturn(true);

                // When
                var result = queryReservationAvailabilityService.execute();

                // Then
                assertThat(result.canReserve()).isFalse();
            }
        }

        @Nested
        @DisplayName("기존 패널티가 아직 유효한 사용자가 조회할 때")
        class Context_with_active_legacy_penalty {

            @Test
            @DisplayName("예약 불가 상태와 만료 시간을 반환해야 한다")
            void it_returns_unavailable_with_expiry_time() {
                // Given
                var userId = 1L;
                var user = createUser("301");
                var penaltyExpiresAt = LocalDateTime.now().plusMinutes(10);
                given(currentUserProvider.getCurrentUserId()).willReturn(userId);
                given(userRepository.findById(userId)).willReturn(Optional.of(user));
                given(penaltyRedisUtil.isInCooldown(userId)).willReturn(false);
                given(penaltyRedisUtil.isBlocked("301")).willReturn(false);
                given(penaltyRedisUtil.getPenaltyExpiryTime(userId)).willReturn(penaltyExpiresAt);

                // When
                var result = queryReservationAvailabilityService.execute();

                // Then
                assertThat(result.canReserve()).isFalse();
                assertThat(result.penaltyExpiresAt()).isEqualTo(penaltyExpiresAt);
            }
        }
    }
}
