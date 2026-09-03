package team.washer.server.v2.domain.reservation.entity;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.global.common.constants.ReservationConstants;
import team.washer.server.v2.global.common.entity.BaseEntity;
import team.washer.server.v2.global.util.DateTimeUtil;

@Entity
@Table(name = "reservations", indexes = {@Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_user_id", columnList = "user_id"),
        @Index(name = "idx_machine_id", columnList = "machine_id"),
        @Index(name = "idx_start_time", columnList = "start_time"),
        @Index(name = "idx_status_start_time", columnList = "status, start_time"),
        @Index(name = "idx_user_created_at", columnList = "user_id, created_at"),
        @Index(name = "idx_user_status_created_at", columnList = "user_id, status, created_at")})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Reservation extends BaseEntity {

    @NotNull(message = "사용자는 필수입니다")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_reservation_user"))
    private User user;

    @NotNull(message = "기기는 필수입니다")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "machine_id", nullable = false, foreignKey = @ForeignKey(name = "fk_reservation_machine"))
    private Machine machine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", foreignKey = @ForeignKey(name = "fk_reservation_created_by"))
    private User createdBy;

    @NotNull(message = "예약 시간은 필수입니다")
    @Column(name = "reserved_at", nullable = false)
    private LocalDateTime reservedAt;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "expected_completion_time")
    private LocalDateTime expectedCompletionTime;

    @Column(name = "actual_completion_time")
    private LocalDateTime actualCompletionTime;

    @NotNull(message = "예약 상태는 필수입니다")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ReservationStatus status = ReservationStatus.RESERVED;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "day_of_week")
    @Enumerated(EnumType.STRING)
    private DayOfWeek dayOfWeek;

    @Column(name = "paused_at")
    private LocalDateTime pausedAt;

    @Column(name = "interruption_count", nullable = false)
    @Builder.Default
    private int interruptionCount = 0;

    @Column(name = "completion_count", nullable = false)
    @Builder.Default
    private int completionCount = 0;

    /**
     * 기기 일시정지 시각을 기록합니다.
     */
    public void markAsPaused() {
        this.pausedAt = DateTimeUtil.nowInKorea();
    }

    /**
     * 일시정지 추적을 초기화합니다. 기기가 재개되거나 예약이 취소될 때 호출합니다.
     */
    public void clearPausedAt() {
        this.pausedAt = null;
    }

    /**
     * 비정상 중단 감지 횟수를 1 증가시킵니다. 사이클 단계 전환 중 순간적으로 보고되는 정지를 진짜 중단과 구분하기 위한 디바운스
     * 카운터입니다.
     */
    public void incrementInterruptionCount() {
        this.interruptionCount++;
    }

    /**
     * 비정상 중단 감지 추적을 초기화합니다. 기기가 정상 동작(진행 중·일시정지)으로 확인되면 호출합니다.
     */
    public void clearInterruptionCount() {
        this.interruptionCount = 0;
    }

    /**
     * 사이클 완료 감지 횟수를 1 증가시킵니다. 기기가 순간적으로 보고한 완료 신호를 진짜 완료와 구분하기 위한 디바운스 카운터입니다.
     */
    public void incrementCompletionCount() {
        this.completionCount++;
    }

    /**
     * 사이클 완료 감지 추적을 초기화합니다. 완료 신호가 사라지거나 가드에 의해 보류되면 호출합니다.
     */
    public void clearCompletionCount() {
        this.completionCount = 0;
    }

    /**
     * 예약 타임아웃 초과 여부를 반환합니다.
     *
     * @return 타임아웃 초과 여부
     */
    public boolean isExpired() {
        LocalDateTime now = DateTimeUtil.nowInKorea();

        return switch (this.status) {
            case RESERVED ->
                Duration.between(this.reservedAt, now).toMinutes() >= ReservationStatus.RESERVED.getTimeoutMinutes();
            default -> false;
        };
    }

    /**
     * 타임아웃까지 남은 시간을 반환합니다. 이미 타임아웃되었거나 해당 없는 상태면 {@link Duration#ZERO}를 반환합니다.
     *
     * @return 타임아웃까지 남은 시간
     */
    public Duration getRemainingTimeUntilTimeout() {
        LocalDateTime now = DateTimeUtil.nowInKorea();

        return switch (this.status) {
            case RESERVED -> {
                long minutes = ReservationStatus.RESERVED.getTimeoutMinutes()
                        - Duration.between(this.reservedAt, now).toMinutes();
                yield Duration.ofMinutes(Math.max(0, minutes));
            }
            default -> Duration.ZERO;
        };
    }

    /**
     * 예약을 실행 중 상태(RUNNING)로 전환합니다. RESERVED 상태가 아니면 예외를 발생시킵니다.
     *
     * @param expectedCompletionTime
     *            예상 완료 시각
     */
    public void start(LocalDateTime expectedCompletionTime) {
        if (this.status != ReservationStatus.RESERVED) {
            throw new ExpectedException("예약 중인 예약만 시작할 수 있습니다", HttpStatus.BAD_REQUEST);
        }
        this.status = ReservationStatus.RUNNING;
        this.startTime = DateTimeUtil.nowInKorea();
        this.expectedCompletionTime = isReasonableCompletionTime(expectedCompletionTime)
                ? expectedCompletionTime
                : null;
    }

    /**
     * 실행 중인 예약의 예상 완료 시각을 갱신합니다. RUNNING 상태가 아니면 예외를 발생시킵니다.
     *
     * <p>
     * 기기가 보고한 값이 합리적인 사이클 길이를 벗어나면 이상치로 보고 갱신하지 않습니다. 조기 완료 판정의 기준선이 오염되는 것을 막기
     * 위함입니다.
     *
     * @param expectedCompletionTime
     *            갱신할 예상 완료 시각
     * @return 갱신 여부. 이상치로 판단되어 무시되면 {@code false}
     */
    public boolean updateExpectedCompletionTime(LocalDateTime expectedCompletionTime) {
        if (this.status != ReservationStatus.RUNNING) {
            throw new ExpectedException("실행 중인 예약만 완료 시각을 갱신할 수 있습니다", HttpStatus.BAD_REQUEST);
        }
        if (!isReasonableCompletionTime(expectedCompletionTime)) {
            return false;
        }
        this.expectedCompletionTime = expectedCompletionTime;
        return true;
    }

    /**
     * 예상 완료 시각 후보가 합리적인 사이클 길이(최대 {@code MAX_REASONABLE_CYCLE_MINUTES}분) 안에 있는지
     * 판정합니다. 시작 시각이 아직 없으면 현재 시각을 기준으로 삼습니다.
     *
     * @param candidate
     *            검증할 예상 완료 시각
     * @return 합리적인 범위 안에 있으면 {@code true}
     */
    private boolean isReasonableCompletionTime(LocalDateTime candidate) {
        if (candidate == null) {
            return false;
        }
        var baseTime = this.startTime != null ? this.startTime : DateTimeUtil.nowInKorea();
        if (candidate.isBefore(baseTime)) {
            return false;
        }
        return Duration.between(baseTime, candidate).toMinutes() <= ReservationConstants.MAX_REASONABLE_CYCLE_MINUTES;
    }

    /**
     * 예약을 완료 상태(COMPLETED)로 전환합니다. RUNNING 상태가 아니면 예외를 발생시킵니다.
     */
    public void complete() {
        if (this.status != ReservationStatus.RUNNING) {
            throw new ExpectedException("실행 중인 예약만 완료할 수 있습니다", HttpStatus.BAD_REQUEST);
        }
        this.status = ReservationStatus.COMPLETED;
        this.actualCompletionTime = DateTimeUtil.nowInKorea();
    }

    /**
     * 예약을 취소 상태(CANCELLED)로 전환합니다. 이미 완료된 예약은 취소할 수 없습니다.
     */
    public void cancel() {
        if (this.status == ReservationStatus.COMPLETED) {
            throw new ExpectedException("완료된 예약은 취소할 수 없습니다", HttpStatus.BAD_REQUEST);
        }
        this.status = ReservationStatus.CANCELLED;
        this.cancelledAt = DateTimeUtil.nowInKorea();
    }

    /**
     * 활성 예약 여부를 반환합니다. RESERVED, RUNNING 상태가 활성으로 간주됩니다.
     *
     * @return 활성 여부
     */
    public boolean isActive() {
        return this.status == ReservationStatus.RESERVED || this.status == ReservationStatus.RUNNING;
    }

    /**
     * 만료되지 않은 현재 활성 예약인지 반환합니다.
     *
     * <p>
     * 활성 상태이더라도 타임아웃이 지난 RESERVED 예약은 활성으로 세지 않습니다. 스케줄러가 아직 정리하지 못한 만료 예약이 새 예약을
     * 막거나 조회 결과에 노출되는 것을 방지하기 위함입니다.
     *
     * @return 만료되지 않은 활성 예약 여부
     */
    public boolean isCurrentlyActive() {
        return isActive() && !isExpired();
    }

    /**
     * 관리자 대리 생성 예약 여부를 반환합니다.
     *
     * <p>
     * 대리 예약은 사용자 본인이 요청한 것이 아니므로 취소·타임아웃 시 패널티를 부여하지 않습니다.
     *
     * @return 대리 예약 여부
     */
    public boolean isProxyReservation() {
        return this.createdBy != null;
    }

    public boolean isReserved() {
        return this.status == ReservationStatus.RESERVED;
    }

    public boolean isRunning() {
        return this.status == ReservationStatus.RUNNING;
    }

    public boolean isCompleted() {
        return this.status == ReservationStatus.COMPLETED;
    }

    public boolean isCancelled() {
        return this.status == ReservationStatus.CANCELLED;
    }

}
