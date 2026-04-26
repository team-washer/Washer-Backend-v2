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
import team.washer.server.v2.global.common.entity.BaseEntity;

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

    /**
     * 기기 일시정지 시각을 기록합니다.
     */
    public void markAsPaused() {
        this.pausedAt = LocalDateTime.now();
    }

    /**
     * 일시정지 추적을 초기화합니다. 기기가 재개되거나 예약이 취소될 때 호출합니다.
     */
    public void clearPausedAt() {
        this.pausedAt = null;
    }

    /**
     * 예약 타임아웃 초과 여부를 반환합니다.
     *
     * @return 타임아웃 초과 여부
     */
    public boolean isExpired() {
        LocalDateTime now = LocalDateTime.now();

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
        LocalDateTime now = LocalDateTime.now();

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
        this.startTime = LocalDateTime.now();
        this.expectedCompletionTime = expectedCompletionTime;
    }

    /**
     * 실행 중인 예약의 예상 완료 시각을 갱신합니다. RUNNING 상태가 아니면 예외를 발생시킵니다.
     *
     * @param expectedCompletionTime
     *            갱신할 예상 완료 시각
     */
    public void updateExpectedCompletionTime(LocalDateTime expectedCompletionTime) {
        if (this.status != ReservationStatus.RUNNING) {
            throw new ExpectedException("실행 중인 예약만 완료 시각을 갱신할 수 있습니다", HttpStatus.BAD_REQUEST);
        }
        this.expectedCompletionTime = expectedCompletionTime;
    }

    /**
     * 예약을 완료 상태(COMPLETED)로 전환합니다. RUNNING 상태가 아니면 예외를 발생시킵니다.
     */
    public void complete() {
        if (this.status != ReservationStatus.RUNNING) {
            throw new ExpectedException("실행 중인 예약만 완료할 수 있습니다", HttpStatus.BAD_REQUEST);
        }
        this.status = ReservationStatus.COMPLETED;
        this.actualCompletionTime = LocalDateTime.now();
    }

    /**
     * 예약을 취소 상태(CANCELLED)로 전환합니다. 이미 완료된 예약은 취소할 수 없습니다.
     */
    public void cancel() {
        if (this.status == ReservationStatus.COMPLETED) {
            throw new ExpectedException("완료된 예약은 취소할 수 없습니다", HttpStatus.BAD_REQUEST);
        }
        this.status = ReservationStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
    }

    /**
     * 활성 예약 여부를 반환합니다. RESERVED, RUNNING 상태가 활성으로 간주됩니다.
     *
     * @return 활성 여부
     */
    public boolean isActive() {
        return this.status == ReservationStatus.RESERVED || this.status == ReservationStatus.RUNNING;
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
