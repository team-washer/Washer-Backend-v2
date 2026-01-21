package team.washer.server.v2.domain.reservation.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import team.washer.server.v2.domain.reservation.entity.redis.PenaltyEntity;
import team.washer.server.v2.domain.reservation.repository.redis.PenaltyRedisRepository;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.global.common.constants.PenaltyConstants;

@ExtendWith(MockitoExtension.class)
@DisplayName("PenaltyRedisUtil 단위 테스트")
class PenaltyRedisUtilTest {

    @InjectMocks
    private PenaltyRedisUtil penaltyRedisUtil;

    @Mock
    private PenaltyRedisRepository penaltyRedisRepository;

    @Mock
    private UserRepository userRepository;

    @Nested
    @DisplayName("applyPenalty 메서드는")
    class Describe_applyPenalty {

        @Test
        @DisplayName("사용자에게 패널티를 부여하고 Redis와 DB에 저장한다")
        void it_applies_penalty_and_saves_to_redis_and_db() {
            // Given
            User user = mock(User.class);
            when(user.getId()).thenReturn(1L);

            // When
            penaltyRedisUtil.applyPenalty(user);

            // Then
            verify(penaltyRedisRepository, times(1)).save(any(PenaltyEntity.class));
            verify(user, times(1)).updateLastCancellationTime();
            verify(userRepository, times(1)).save(user);
        }
    }

    @Nested
    @DisplayName("getPenaltyExpiryTime 메서드는")
    class Describe_getPenaltyExpiryTime {

        @Test
        @DisplayName("Redis에 패널티 정보가 있으면 만료 시간을 반환한다")
        void it_returns_expiry_time_from_redis_if_exists() {
            // Given
            Long userId = 1L;
            LocalDateTime expiryTime = LocalDateTime.now().plusMinutes(10);
            PenaltyEntity penalty = PenaltyEntity.builder().userId(userId).expiryTime(expiryTime).build();

            when(penaltyRedisRepository.findById(userId)).thenReturn(Optional.of(penalty));

            // When
            LocalDateTime result = penaltyRedisUtil.getPenaltyExpiryTime(userId);

            // Then
            assertThat(result).isEqualTo(expiryTime);
        }

        @Test
        @DisplayName("Redis에 정보가 없고 DB에 최근 취소 이력이 있으면 만료 시간을 계산해서 반환한다")
        void it_returns_expiry_time_from_db_if_not_in_redis() {
            // Given
            Long userId = 1L;
            User user = mock(User.class);
            LocalDateTime lastCancellationAt = LocalDateTime.now().minusMinutes(5);

            when(penaltyRedisRepository.findById(userId)).thenReturn(Optional.empty());
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(user.getLastCancellationAt()).thenReturn(lastCancellationAt);

            // When
            LocalDateTime result = penaltyRedisUtil.getPenaltyExpiryTime(userId);

            // Then
            assertThat(result).isEqualTo(lastCancellationAt.plusMinutes(PenaltyConstants.PENALTY_DURATION_MINUTES));
        }
    }

    @Nested
    @DisplayName("clearPenalty 메서드는")
    class Describe_clearPenalty {

        @Test
        @DisplayName("Redis와 DB에서 패널티 정보를 삭제한다")
        void it_clears_penalty_from_redis_and_db() {
            // Given
            Long userId = 1L;
            User user = mock(User.class);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            // When
            penaltyRedisUtil.clearPenalty(userId);

            // Then
            verify(penaltyRedisRepository, times(1)).deleteById(userId);
            verify(user, times(1)).clearLastCancellationTime();
            verify(userRepository, times(1)).save(user);
        }
    }
}
