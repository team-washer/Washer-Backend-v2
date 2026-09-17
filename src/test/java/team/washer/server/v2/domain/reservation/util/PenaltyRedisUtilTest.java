package team.washer.server.v2.domain.reservation.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.reservation.entity.redis.CancellationBlockEntity;
import team.washer.server.v2.domain.reservation.entity.redis.CooldownEntity;
import team.washer.server.v2.domain.reservation.entity.redis.TimeoutWarningEntity;
import team.washer.server.v2.domain.reservation.enums.RestrictionStatus;
import team.washer.server.v2.domain.reservation.repository.redis.CancellationBlockRedisRepository;
import team.washer.server.v2.domain.reservation.repository.redis.CooldownRedisRepository;
import team.washer.server.v2.domain.reservation.repository.redis.TimeoutWarningRedisRepository;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.global.common.constants.PenaltyConstants;
import team.washer.server.v2.global.common.error.code.ErrorCode;
import team.washer.server.v2.global.common.error.exception.ErrorCodeException;
import team.washer.server.v2.global.thirdparty.discord.service.DiscordErrorNotificationService;
import team.washer.server.v2.global.util.DateTimeUtil;

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

    @Mock
    private ObjectProvider<DiscordErrorNotificationService> discordErrorNotificationServiceProvider;

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
            assertThat(result).isBetween(DateTimeUtil.nowInKorea().plusSeconds(290),
                    DateTimeUtil.nowInKorea().plusSeconds(310));
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
            assertThat(result).isBetween(DateTimeUtil.nowInKorea().plusSeconds(3590),
                    DateTimeUtil.nowInKorea().plusSeconds(3610));
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
    @DisplayName("applyCooldown / checkCooldown 메서드는")
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
        @DisplayName("해당 유형 쿨다운 중이면 RESTRICTED를 반환한다")
        void it_returns_restricted_when_in_cooldown() {
            // Given
            Long userId = 1L;
            when(cooldownRedisRepository.existsById("1:WASHER")).thenReturn(true);

            // When
            RestrictionStatus result = penaltyRedisUtil.checkCooldown(userId, MachineType.WASHER);

            // Then
            assertThat(result).isEqualTo(RestrictionStatus.RESTRICTED);
        }

        @Test
        @DisplayName("해당 유형 쿨다운 중이 아니면 NONE을 반환한다")
        void it_returns_none_when_not_in_cooldown() {
            // Given
            Long userId = 1L;
            when(cooldownRedisRepository.existsById("1:DRYER")).thenReturn(false);

            // When
            RestrictionStatus result = penaltyRedisUtil.checkCooldown(userId, MachineType.DRYER);

            // Then
            assertThat(result).isEqualTo(RestrictionStatus.NONE);
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
    @DisplayName("applyBlock / checkBlock 메서드는")
    class Describe_block {

        @Test
        @DisplayName("48시간 블록을 적용하면 Redis에 저장한다")
        void it_saves_block_to_redis() {
            // Given
            String roomNumber = "101";

            // When
            boolean result = penaltyRedisUtil.applyBlock(roomNumber);

            // Then
            assertThat(result).isTrue();
            verify(cancellationBlockRedisRepository, times(1)).save(any(CancellationBlockEntity.class));
        }

        @Test
        @DisplayName("블록 중이면 RESTRICTED를 반환한다")
        void it_returns_restricted_when_blocked() {
            // Given
            String roomNumber = "101";
            when(cancellationBlockRedisRepository.existsById(roomNumber)).thenReturn(true);

            // When
            RestrictionStatus result = penaltyRedisUtil.checkBlock(roomNumber);

            // Then
            assertThat(result).isEqualTo(RestrictionStatus.RESTRICTED);
        }

        @Test
        @DisplayName("블록 중이 아니면 NONE을 반환한다")
        void it_returns_none_when_not_blocked() {
            // Given
            String roomNumber = "101";
            when(cancellationBlockRedisRepository.existsById(roomNumber)).thenReturn(false);

            // When
            RestrictionStatus result = penaltyRedisUtil.checkBlock(roomNumber);

            // Then
            assertThat(result).isEqualTo(RestrictionStatus.NONE);
        }
    }

    @Nested
    @DisplayName("조회 계열은 Redis 조회에 실패하면")
    class Describe_lookup_failure {

        @Test
        @DisplayName("checkCooldown이 제한 없음과 구분되는 UNAVAILABLE을 반환한다")
        void it_returns_unavailable_when_cooldown_lookup_fails() {
            // Given
            when(cooldownRedisRepository.existsById("1:WASHER")).thenThrow(new RuntimeException("redis down"));

            // When
            RestrictionStatus result = penaltyRedisUtil.checkCooldown(1L, MachineType.WASHER);

            // Then
            assertThat(result).isEqualTo(RestrictionStatus.UNAVAILABLE);
        }

        @Test
        @DisplayName("checkBlock이 제한 없음과 구분되는 UNAVAILABLE을 반환한다")
        void it_returns_unavailable_when_block_lookup_fails() {
            // Given
            when(cancellationBlockRedisRepository.existsById("101")).thenThrow(new RuntimeException("redis down"));

            // When
            RestrictionStatus result = penaltyRedisUtil.checkBlock("101");

            // Then
            assertThat(result).isEqualTo(RestrictionStatus.UNAVAILABLE);
        }

        @Test
        @DisplayName("getPenaltyExpiryTimeOrThrow가 503 오류 코드 예외를 던진다")
        void it_throws_when_expiry_lookup_fails_in_strict_mode() {
            // Given
            when(cooldownRedisRepository.findById("1:WASHER")).thenThrow(new RuntimeException("redis down"));

            // When & Then
            assertThatThrownBy(() -> penaltyRedisUtil.getPenaltyExpiryTimeOrThrow(1L))
                    .isInstanceOf(ErrorCodeException.class).extracting(e -> ((ErrorCodeException) e).getErrorCode())
                    .isEqualTo(ErrorCode.RESERVATION_RESTRICTION_UNAVAILABLE);
        }

        @Test
        @DisplayName("상태 표시용 getPenaltyExpiryTime은 실패 항목을 건너뛰고 예외를 던지지 않는다")
        void it_skips_failed_lookup_in_lenient_mode() {
            // Given
            User user = mock(User.class);
            when(user.getRoomNumber()).thenReturn("101");
            when(cooldownRedisRepository.findById(any())).thenThrow(new RuntimeException("redis down"));
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(cancellationBlockRedisRepository.findById("101"))
                    .thenReturn(Optional.of(CancellationBlockEntity.builder().roomNumber("101").ttl(3600L).build()));

            // When
            LocalDateTime result = penaltyRedisUtil.getPenaltyExpiryTime(1L);

            // Then
            assertThat(result).isAfter(DateTimeUtil.nowInKorea().plusSeconds(3500));
        }
    }

    @Nested
    @DisplayName("적용 계열은 Redis 저장에 실패하면")
    class Describe_apply_failure {

        @Test
        @DisplayName("applyBlock이 예외를 던지지 않고 false를 반환하며 운영 알림을 보고한다")
        void it_reports_and_returns_false_when_block_apply_fails() {
            // Given
            when(cancellationBlockRedisRepository.save(any(CancellationBlockEntity.class)))
                    .thenThrow(new RuntimeException("redis down"));

            // When
            boolean result = penaltyRedisUtil.applyBlock("101");

            // Then
            assertThat(result).isFalse();
            verify(discordErrorNotificationServiceProvider, times(1)).ifAvailable(any());
        }

        @Test
        @DisplayName("applyCooldown이 예외를 던지지 않고 운영 알림을 보고한다")
        void it_reports_when_cooldown_apply_fails() {
            // Given
            when(cooldownRedisRepository.save(any(CooldownEntity.class))).thenThrow(new RuntimeException("redis down"));

            // When
            penaltyRedisUtil.applyCooldown(1L, MachineType.WASHER);

            // Then
            verify(discordErrorNotificationServiceProvider, times(1)).ifAvailable(any());
        }

        @Test
        @DisplayName("운영 알림 전송까지 실패해도 applyBlock은 예외를 던지지 않고 false를 반환한다")
        void it_returns_false_when_notification_also_fails() {
            // Given
            when(cancellationBlockRedisRepository.save(any(CancellationBlockEntity.class)))
                    .thenThrow(new RuntimeException("redis down"));
            doThrow(new RuntimeException("discord down")).when(discordErrorNotificationServiceProvider)
                    .ifAvailable(any());

            // When
            boolean result = penaltyRedisUtil.applyBlock("101");

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("관리자 경로의 applyBlockOrThrow는 예외를 호출자에게 전파한다")
        void it_propagates_when_block_apply_or_throw_fails() {
            // Given
            when(cancellationBlockRedisRepository.save(any(CancellationBlockEntity.class)))
                    .thenThrow(new RuntimeException("redis down"));

            // When & Then
            assertThatThrownBy(() -> penaltyRedisUtil.applyBlockOrThrow("101")).isInstanceOf(RuntimeException.class)
                    .hasMessage("redis down");
            verify(discordErrorNotificationServiceProvider, never()).ifAvailable(any());
        }
    }
}
