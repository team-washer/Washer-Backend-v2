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
import org.springframework.data.redis.core.StringRedisTemplate;

import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.reservation.entity.redis.CancellationBlockEntity;
import team.washer.server.v2.domain.reservation.entity.redis.CooldownEntity;
import team.washer.server.v2.domain.reservation.entity.redis.TimeoutWarningEntity;
import team.washer.server.v2.domain.reservation.repository.redis.CancellationBlockRedisRepository;
import team.washer.server.v2.domain.reservation.repository.redis.CooldownRedisRepository;
import team.washer.server.v2.domain.reservation.repository.redis.TimeoutWarningRedisRepository;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.global.common.constants.PenaltyConstants;

@ExtendWith(MockitoExtension.class)
@DisplayName("PenaltyRedisUtil 단위 테스트")
class PenaltyRedisUtilTest {

    @InjectMocks
    private PenaltyRedisUtil penaltyRedisUtil;

    @Mock
    private CooldownRedisRepository cooldownRedisRepository;

    @Mock
    private TimeoutWarningRedisRepository timeoutWarningRedisRepository;

    @Mock
    private CancellationBlockRedisRepository cancellationBlockRedisRepository;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private UserRepository userRepository;

    @Nested
    @DisplayName("getPenaltyExpiryTime 메서드는")
    class Describe_getPenaltyExpiryTime {

        @Test
        @DisplayName("세탁기 쿨다운만 적용 중이면 쿨다운 잔여 TTL 기준 만료 시간을 반환한다")
        void it_returns_expiry_time_from_cooldown_ttl() {
            // Given
            Long userId = 1L;
            User user = mock(User.class);
            when(user.getRoomNumber()).thenReturn("101");
            when(cooldownRedisRepository.findById("1:WASHER"))
                    .thenReturn(Optional.of(CooldownEntity.builder().id("1:WASHER").ttl(300L).build()));
            when(cooldownRedisRepository.findById("1:DRYER")).thenReturn(Optional.empty());
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(cancellationBlockRedisRepository.findById("101")).thenReturn(Optional.empty());

            // When
            LocalDateTime result = penaltyRedisUtil.getPenaltyExpiryTime(userId);

            // Then
            assertThat(result).isBetween(LocalDateTime.now().plusSeconds(290), LocalDateTime.now().plusSeconds(310));
        }

        @Test
        @DisplayName("쿨다운과 블록이 함께 있으면 더 늦게 풀리는 블록 만료 시간을 반환한다")
        void it_returns_latest_expiry_between_cooldown_and_block() {
            // Given
            Long userId = 1L;
            User user = mock(User.class);
            when(user.getRoomNumber()).thenReturn("101");
            when(cooldownRedisRepository.findById("1:WASHER"))
                    .thenReturn(Optional.of(CooldownEntity.builder().id("1:WASHER").ttl(300L).build()));
            when(cooldownRedisRepository.findById("1:DRYER")).thenReturn(Optional.empty());
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(cancellationBlockRedisRepository.findById("101"))
                    .thenReturn(Optional.of(CancellationBlockEntity.builder().roomNumber("101").ttl(3600L).build()));

            // When
            LocalDateTime result = penaltyRedisUtil.getPenaltyExpiryTime(userId);

            // Then
            assertThat(result).isBetween(LocalDateTime.now().plusSeconds(3590), LocalDateTime.now().plusSeconds(3610));
        }

        @Test
        @DisplayName("쿨다운과 블록이 모두 없으면 null을 반환한다")
        void it_returns_null_when_no_restriction() {
            // Given
            Long userId = 1L;
            User user = mock(User.class);
            when(user.getRoomNumber()).thenReturn("101");
            when(cooldownRedisRepository.findById("1:WASHER")).thenReturn(Optional.empty());
            when(cooldownRedisRepository.findById("1:DRYER")).thenReturn(Optional.empty());
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(cancellationBlockRedisRepository.findById("101")).thenReturn(Optional.empty());

            // When
            LocalDateTime result = penaltyRedisUtil.getPenaltyExpiryTime(userId);

            // Then
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("clearAllRestrictions 메서드는")
    class Describe_clearAllRestrictions {

        @Test
        @DisplayName("쿨다운, 경고, 취소 이력, 호실 블록, 마지막 취소 시각을 모두 정리한다")
        void it_clears_all_restrictions() {
            // Given
            Long userId = 1L;
            User user = mock(User.class);
            when(user.getRoomNumber()).thenReturn("101");
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            // When
            penaltyRedisUtil.clearAllRestrictions(userId);

            // Then
            verify(cooldownRedisRepository, times(1)).deleteById("1:WASHER");
            verify(cooldownRedisRepository, times(1)).deleteById("1:DRYER");
            verify(timeoutWarningRedisRepository, times(1)).deleteById(userId);
            verify(stringRedisTemplate, times(1)).delete(PenaltyConstants.CANCEL_HISTORY_KEY_PREFIX + userId);
            verify(cancellationBlockRedisRepository, times(1)).deleteById("101");
            verify(user, times(1)).clearLastCancellationTime();
            verify(userRepository, times(1)).save(user);
        }
    }

    @Nested
    @DisplayName("applyCooldown / isInCooldown 메서드는")
    class Describe_cooldown {

        @Test
        @DisplayName("쿨다운을 적용하면 유형별 키로 Redis에 저장한다")
        void it_saves_cooldown_to_redis() {
            // Given
            Long userId = 1L;

            // When
            penaltyRedisUtil.applyCooldown(userId, MachineType.WASHER);

            // Then
            verify(cooldownRedisRepository, times(1)).save(any(CooldownEntity.class));
        }

        @Test
        @DisplayName("해당 유형 쿨다운 중이면 true를 반환한다")
        void it_returns_true_when_in_cooldown() {
            // Given
            Long userId = 1L;
            when(cooldownRedisRepository.existsById("1:WASHER")).thenReturn(true);

            // When
            boolean result = penaltyRedisUtil.isInCooldown(userId, MachineType.WASHER);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("해당 유형 쿨다운 중이 아니면 false를 반환한다")
        void it_returns_false_when_not_in_cooldown() {
            // Given
            Long userId = 1L;
            when(cooldownRedisRepository.existsById("1:DRYER")).thenReturn(false);

            // When
            boolean result = penaltyRedisUtil.isInCooldown(userId, MachineType.DRYER);

            // Then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("applyWarning / hasWarning 메서드는")
    class Describe_warning {

        @Test
        @DisplayName("경고를 적용하면 Redis에 저장한다")
        void it_saves_warning_to_redis() {
            // Given
            Long userId = 1L;

            // When
            penaltyRedisUtil.applyWarning(userId);

            // Then
            verify(timeoutWarningRedisRepository, times(1)).save(any(TimeoutWarningEntity.class));
        }

        @Test
        @DisplayName("경고가 있으면 true를 반환한다")
        void it_returns_true_when_warning_exists() {
            // Given
            Long userId = 1L;
            when(timeoutWarningRedisRepository.existsById(userId)).thenReturn(true);

            // When
            boolean result = penaltyRedisUtil.hasWarning(userId);

            // Then
            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("applyBlock / isBlocked 메서드는")
    class Describe_block {

        @Test
        @DisplayName("48시간 블록을 적용하면 Redis에 저장한다")
        void it_saves_block_to_redis() {
            // Given
            String roomNumber = "101";

            // When
            penaltyRedisUtil.applyBlock(roomNumber);

            // Then
            verify(cancellationBlockRedisRepository, times(1)).save(any(CancellationBlockEntity.class));
        }

        @Test
        @DisplayName("블록 중이면 true를 반환한다")
        void it_returns_true_when_blocked() {
            // Given
            String roomNumber = "101";
            when(cancellationBlockRedisRepository.existsById(roomNumber)).thenReturn(true);

            // When
            boolean result = penaltyRedisUtil.isBlocked(roomNumber);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("블록 중이 아니면 false를 반환한다")
        void it_returns_false_when_not_blocked() {
            // Given
            String roomNumber = "101";
            when(cancellationBlockRedisRepository.existsById(roomNumber)).thenReturn(false);

            // When
            boolean result = penaltyRedisUtil.isBlocked(roomNumber);

            // Then
            assertThat(result).isFalse();
        }
    }
}
