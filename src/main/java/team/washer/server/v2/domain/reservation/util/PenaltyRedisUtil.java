package team.washer.server.v2.domain.reservation.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import team.washer.server.v2.global.common.error.ErrorCode;
import team.washer.server.v2.global.common.error.exception.ErrorCodeException;
import team.washer.server.v2.global.thirdparty.discord.service.DiscordErrorNotificationService;
import team.washer.server.v2.global.util.DateTimeUtil;

@Slf4j
@Component
@RequiredArgsConstructor
public class PenaltyRedisUtil {

    private static final String PENALTY_TYPE_COOLDOWN = "COOLDOWN";
    private static final String PENALTY_TYPE_WARNING = "TIMEOUT_WARNING";
    private static final String PENALTY_TYPE_CANCELLATION_HISTORY = "CANCELLATION_HISTORY";
    private static final String PENALTY_TYPE_BLOCK = "CANCELLATION_BLOCK";

    private final CooldownRedisRepository cooldownRedisRepository;
    private final TimeoutWarningRedisRepository timeoutWarningRedisRepository;
    private final CancellationBlockRedisRepository cancellationBlockRedisRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final UserRepository userRepository;
    private final ObjectProvider<DiscordErrorNotificationService> discordErrorNotificationServiceProvider;

    // ===== 예약 제한 만료 시각 (쿨다운 + 호실 블록) =====

    /**
     * 현재 적용 중인 예약 제한의 만료 시각을 반환합니다(상태 표시용).
     * <p>
     * 기기 유형별 5분 쿨다운(세탁기/건조기)과 호실 단위 48시간 블록 중 가장 늦게 풀리는 시각을 반환하며, 제한이 없으면
     * {@code null}을 반환합니다. 실제 예약 가부는 유형별로 판정되므로 이 값은 요약 표시 용도입니다.
     * </p>
     * <p>
     * Redis 조회에 실패한 항목은 건너뜁니다. 조회 실패를 제한 없음과 구분해야 하는 호출자는
     * {@link #getPenaltyExpiryTimeOrThrow(Long)}을 사용해야 합니다.
     * </p>
     */
    public LocalDateTime getPenaltyExpiryTime(final Long userId) {
        return resolvePenaltyExpiryTime(userId, false);
    }

    /**
     * 현재 적용 중인 예약 제한의 만료 시각을 반환하고, Redis 조회에 실패하면 예외를 던집니다.
     * <p>
     * 예약 가능 여부처럼 조회 실패를 제한 없음으로 오인하면 안 되는 경로에서 사용합니다.
     * </p>
     *
     * @throws ErrorCodeException
     *             Redis 조회에 실패한 경우
     *             ({@link ErrorCode#RESERVATION_RESTRICTION_UNAVAILABLE})
     */
    public LocalDateTime getPenaltyExpiryTimeOrThrow(final Long userId) {
        return resolvePenaltyExpiryTime(userId, true);
    }

    private LocalDateTime resolvePenaltyExpiryTime(final Long userId, final boolean failOnLookupError) {
        LocalDateTime latestExpiry = null;
        for (final MachineType machineType : MachineType.values()) {
            latestExpiry = later(latestExpiry,
                    expiryFromTtl(cooldownRemainingTtlSeconds(userId, machineType, failOnLookupError)));
        }

        final User user = userRepository.findById(userId).orElse(null);
        if (user != null && user.getRoomNumber() != null) {
            latestExpiry = later(latestExpiry,
                    expiryFromTtl(blockRemainingTtlSeconds(user.getRoomNumber(), failOnLookupError)));
        }
        return latestExpiry;
    }

    private static LocalDateTime later(final LocalDateTime current, final LocalDateTime candidate) {
        if (candidate == null) {
            return current;
        }
        return current == null || candidate.isAfter(current) ? candidate : current;
    }

    private LocalDateTime expiryFromTtl(final Long remainingSeconds) {
        if (remainingSeconds == null || remainingSeconds <= 0) {
            return null;
        }
        return DateTimeUtil.nowInKorea().plusSeconds(remainingSeconds);
    }

    private Long cooldownRemainingTtlSeconds(final Long userId,
            final MachineType machineType,
            final boolean failOnLookupError) {
        try {
            return cooldownRedisRepository.findById(cooldownKey(userId, machineType)).map(CooldownEntity::getTtl)
                    .orElse(null);
        } catch (Exception e) {
            log.error("penalty lookup failed event=penalty_lookup_failed penaltyType={} userId={} machineType={}",
                    PENALTY_TYPE_COOLDOWN,
                    userId,
                    machineType,
                    e);
            if (failOnLookupError) {
                throw new ErrorCodeException(ErrorCode.RESERVATION_RESTRICTION_UNAVAILABLE, e);
            }
            return null;
        }
    }

    private static String cooldownKey(final Long userId, final MachineType machineType) {
        return userId + ":" + machineType.name();
    }

    private Long blockRemainingTtlSeconds(final String roomNumber, final boolean failOnLookupError) {
        try {
            return cancellationBlockRedisRepository.findById(roomNumber).map(CancellationBlockEntity::getTtl)
                    .orElse(null);
        } catch (Exception e) {
            log.error("penalty lookup failed event=penalty_lookup_failed penaltyType={} roomNumber={}",
                    PENALTY_TYPE_BLOCK,
                    roomNumber,
                    e);
            if (failOnLookupError) {
                throw new ErrorCodeException(ErrorCode.RESERVATION_RESTRICTION_UNAVAILABLE, e);
            }
            return null;
        }
    }

    // ===== 5분 쿨다운 =====

    /**
     * 취소 직후 해당 기기 유형에 5분 재예약 쿨다운을 적용합니다. 세탁기 취소는 세탁기 쿨다운만 적용되며 건조기 예약에는 영향을 주지
     * 않습니다.
     * <p>
     * 실패해도 예외를 던지지 않고 운영 알림으로 보고합니다. 자동 판정 경로의 예약 처리를 Redis 장애로 롤백하지 않기 위함입니다.
     * </p>
     */
    public void applyCooldown(final Long userId, final MachineType machineType) {
        try {
            final long ttlSeconds = PenaltyConstants.COOLDOWN_DURATION_MINUTES * 60L;
            cooldownRedisRepository
                    .save(CooldownEntity.builder().id(cooldownKey(userId, machineType)).ttl(ttlSeconds).build());
            log.info("cooldown applied userId={} machineType={} expiresInMinutes={}",
                    userId,
                    machineType,
                    PenaltyConstants.COOLDOWN_DURATION_MINUTES);
        } catch (Exception e) {
            reportPenaltyApplyFailure(PENALTY_TYPE_COOLDOWN, cooldownKey(userId, machineType), e);
        }
    }

    /**
     * 해당 기기 유형의 쿨다운 적용 여부를 조회합니다.
     * <p>
     * Redis 조회에 실패하면 {@link RestrictionStatus#UNAVAILABLE}을 반환하며, 허용·거부 판단은 호출자가
     * 합니다.
     * </p>
     */
    public RestrictionStatus checkCooldown(final Long userId, final MachineType machineType) {
        try {
            return cooldownRedisRepository.existsById(cooldownKey(userId, machineType))
                    ? RestrictionStatus.RESTRICTED
                    : RestrictionStatus.NONE;
        } catch (Exception e) {
            log.error("penalty lookup failed event=penalty_lookup_failed penaltyType={} userId={} machineType={}",
                    PENALTY_TYPE_COOLDOWN,
                    userId,
                    machineType,
                    e);
            return RestrictionStatus.UNAVAILABLE;
        }
    }

    // ===== 타임아웃 경고 (첫 번째) =====

    /**
     * 첫 번째 타임아웃 경고를 기록합니다 (TTL 7일). 실패해도 예외를 던지지 않고 운영 알림으로 보고합니다.
     */
    public void applyWarning(final Long userId) {
        try {
            final long ttlSeconds = PenaltyConstants.WARNING_DURATION_DAYS * 24L * 3600L;
            timeoutWarningRedisRepository.save(TimeoutWarningEntity.builder().userId(userId).ttl(ttlSeconds).build());
            log.info("timeout warning applied userId={}", userId);
        } catch (Exception e) {
            reportPenaltyApplyFailure(PENALTY_TYPE_WARNING, String.valueOf(userId), e);
        }
    }

    /**
     * 이미 타임아웃 경고가 있는지 여부를 반환합니다.
     */
    public boolean hasWarning(final Long userId) {
        try {
            return timeoutWarningRedisRepository.existsById(userId);
        } catch (Exception e) {
            log.warn("failed to check warning userId={}", userId, e);
            return false;
        }
    }

    // ===== 48시간 취소 횟수 (슬라이딩 윈도우) =====

    /**
     * 취소 이력에 현재 시각을 기록하고 48시간 이전 항목을 제거합니다. 실패해도 예외를 던지지 않고 운영 알림으로 보고합니다.
     */
    public void recordCancellation(final Long userId) {
        try {
            var key = PenaltyConstants.CANCEL_HISTORY_KEY_PREFIX + userId;
            long nowMillis = Instant.now().toEpochMilli();
            long windowStart = nowMillis - PenaltyConstants.CANCELLATION_WINDOW_HOURS * 3600L * 1000L;

            stringRedisTemplate.opsForZSet().add(key, String.valueOf(nowMillis), nowMillis);
            stringRedisTemplate.opsForZSet().removeRangeByScore(key, Double.NEGATIVE_INFINITY, windowStart);
            // TTL: 윈도우 크기 + 여유 1시간
            stringRedisTemplate.expire(key, java.time.Duration.ofHours(PenaltyConstants.CANCELLATION_WINDOW_HOURS + 1));
            log.info("cancellation recorded userId={}", userId);
        } catch (Exception e) {
            reportPenaltyApplyFailure(PENALTY_TYPE_CANCELLATION_HISTORY, String.valueOf(userId), e);
        }
    }

    /**
     * 최근 48시간 내 취소 횟수를 반환합니다.
     */
    public long getCancellationCount(final Long userId) {
        try {
            var key = PenaltyConstants.CANCEL_HISTORY_KEY_PREFIX + userId;
            long windowStart = Instant.now().toEpochMilli()
                    - PenaltyConstants.CANCELLATION_WINDOW_HOURS * 3600L * 1000L;
            Long count = stringRedisTemplate.opsForZSet().count(key, windowStart, Double.POSITIVE_INFINITY);
            return count != null ? count : 0L;
        } catch (Exception e) {
            log.warn("failed to get cancellation count userId={}", userId, e);
            return 0L;
        }
    }

    // ===== 48시간 예약 차단 (호실 단위) =====

    /**
     * 사용자 ID 기준으로 48시간 호실 차단의 만료 시각을 반환합니다.
     * <p>
     * 차단이 없거나 호실 정보가 없으면 {@code null}을 반환합니다.
     * </p>
     */
    public LocalDateTime getBlockExpiryTime(final Long userId) {
        final User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getRoomNumber() == null) {
            return null;
        }
        return expiryFromTtl(blockRemainingTtlSeconds(user.getRoomNumber(), false));
    }

    /**
     * 호실 단위 예약 차단 기간을 지정한 일수만큼 연장합니다.
     *
     * @param roomNumber
     *            연장 대상 호실 번호
     * @param additionalDays
     *            추가 연장 일수 (1 이상)
     * @return 연장 후 만료 시각
     * @throws team.themoment.sdk.exception.ExpectedException
     *             활성 차단이 없는 경우
     */
    public LocalDateTime extendBlock(final String roomNumber, final long additionalDays) {
        final CancellationBlockEntity existing = cancellationBlockRedisRepository.findById(roomNumber)
                .orElseThrow(() -> new team.themoment.sdk.exception.ExpectedException("활성화된 예약 차단이 없습니다.",
                        org.springframework.http.HttpStatus.BAD_REQUEST));

        final long currentTtl = existing.getTtl() != null ? existing.getTtl() : 0L;
        final long additionalSeconds = additionalDays * 24L * 3600L;
        final long newTtl = currentTtl + additionalSeconds;

        cancellationBlockRedisRepository
                .save(CancellationBlockEntity.builder().roomNumber(roomNumber).ttl(newTtl).build());
        log.info("block extended roomNumber={} additionalDays={} newTtlSeconds={}", roomNumber, additionalDays, newTtl);

        return DateTimeUtil.nowInKorea().plusSeconds(newTtl);
    }

    /**
     * 48시간 예약 차단을 호실 단위로 적용합니다.
     * <p>
     * 실패해도 예외를 던지지 않습니다. 자동 판정 경로(예약 취소·타임아웃)에서 Redis 장애 때문에 예약 처리 트랜잭션 전체가 실패하는 것을
     * 막기 위함입니다. 대신 실패를 구조화 로그와 운영 알림으로 보고하고 {@code false}를 반환합니다. 부과 실패를 예외로 받아야 하는
     * 호출자는 {@link #applyBlockOrThrow(String)}을 사용해야 합니다.
     * </p>
     *
     * @return 차단 저장에 성공하면 {@code true}
     */
    public boolean applyBlock(final String roomNumber) {
        try {
            applyBlockOrThrow(roomNumber);
            return true;
        } catch (Exception e) {
            reportPenaltyApplyFailure(PENALTY_TYPE_BLOCK, roomNumber, e);
            return false;
        }
    }

    /**
     * 48시간 예약 차단을 호실 단위로 적용하고, 실패 시 예외를 그대로 전파합니다.
     * <p>
     * 관리자 수동 부과처럼 집행 성공 여부를 호출자가 반드시 알아야 하는 경로에서 사용합니다.
     * {@link #checkBlock(String)}으로 성공을 판정하면 이미 차단 중인 호실에서 TTL 갱신 실패를 성공으로 오인하므로,
     * 저장 실패는 예외로 알려야 합니다.
     * </p>
     *
     * @param roomNumber
     *            차단 대상 호실 번호
     */
    public void applyBlockOrThrow(final String roomNumber) {
        final long ttlSeconds = PenaltyConstants.CANCELLATION_WINDOW_HOURS * 3600L;
        cancellationBlockRedisRepository
                .save(CancellationBlockEntity.builder().roomNumber(roomNumber).ttl(ttlSeconds).build());
        log.info("48h block applied roomNumber={}", roomNumber);
    }

    /**
     * 호실의 48시간 예약 차단 적용 여부를 조회합니다.
     * <p>
     * Redis 조회에 실패하면 {@link RestrictionStatus#UNAVAILABLE}을 반환하며, 허용·거부 판단은 호출자가
     * 합니다.
     * </p>
     */
    public RestrictionStatus checkBlock(final String roomNumber) {
        try {
            return cancellationBlockRedisRepository.existsById(roomNumber)
                    ? RestrictionStatus.RESTRICTED
                    : RestrictionStatus.NONE;
        } catch (Exception e) {
            log.error("penalty lookup failed event=penalty_lookup_failed penaltyType={} roomNumber={}",
                    PENALTY_TYPE_BLOCK,
                    roomNumber,
                    e);
            return RestrictionStatus.UNAVAILABLE;
        }
    }

    public void clearAllRestrictions(final Long userId) {
        try {
            for (final MachineType machineType : MachineType.values()) {
                cooldownRedisRepository.deleteById(cooldownKey(userId, machineType));
            }
            timeoutWarningRedisRepository.deleteById(userId);
            stringRedisTemplate.delete(PenaltyConstants.CANCEL_HISTORY_KEY_PREFIX + userId);
        } catch (Exception e) {
            log.error("failed to clear restrictions userId={}", userId, e);
        }

        final User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            try {
                cancellationBlockRedisRepository.deleteById(user.getRoomNumber());
            } catch (Exception e) {
                log.error("failed to clear block roomNumber={}", user.getRoomNumber(), e);
            }
            user.clearLastCancellationTime();
            userRepository.save(user);
        }
    }

    /**
     * 자동 패널티 저장 실패를 구조화 로그와 운영 알림으로 보고합니다.
     * <p>
     * {@code event=penalty_apply_failed} 로그는 CloudWatch 메트릭 필터로 집계할 수 있도록 고정된 키를
     * 사용합니다.
     * </p>
     */
    private void reportPenaltyApplyFailure(final String penaltyType, final String target, final Exception e) {
        log.error("penalty apply failed event=penalty_apply_failed penaltyType={} target={}", penaltyType, target, e);
        discordErrorNotificationServiceProvider.ifAvailable(service -> service
                .notifyError(e, "자동 패널티 저장 실패", Map.of("Penalty Type", penaltyType, "Target", target)));
    }
}
