package team.washer.server.v2.domain.reservation.entity;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
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
@NoArgsConstructor
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

    @NotNull(message = "시작 시간은 필수입니다")
    @Column(name = "start_time", nullable = false)
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

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "day_of_week")
    @Enumerated(EnumType.STRING)
    private DayOfWeek dayOfWeek;

    public boolean isExpired() {
        LocalDateTime now = LocalDateTime.now();

        return switch (this.status) {
            case RESERVED -> Duration.between(this.startTime, now).toMinutes() >= 5;
            case CONFIRMED -> this.confirmedAt != null && Duration.between(this.confirmedAt, now).toMinutes() >= 2;
            default -> false;
        };
    }

    public Duration getRemainingTimeUntilTimeout() {
        LocalDateTime now = LocalDateTime.now();

        return switch (this.status) {
            case RESERVED -> {
                long minutes = 5 - Duration.between(this.startTime, now).toMinutes();
                yield Duration.ofMinutes(Math.max(0, minutes));
            }
            case CONFIRMED -> {
                if (this.confirmedAt == null)
                    yield Duration.ZERO;
                long minutes = 2 - Duration.between(this.confirmedAt, now).toMinutes();
                yield Duration.ofMinutes(Math.max(0, minutes));
            }
            default -> Duration.ZERO;
        };
    }

    public void confirm() {
        if (this.status != ReservationStatus.RESERVED) {
            throw new IllegalStateException("예약 상태에서만 확인할 수 있습니다");
        }
        this.status = ReservationStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
    }

    public void start(LocalDateTime expectedCompletionTime) {
        if (this.status != ReservationStatus.CONFIRMED) {
            throw new IllegalStateException("확인된 예약만 시작할 수 있습니다");
        }
        this.status = ReservationStatus.RUNNING;
        this.expectedCompletionTime = expectedCompletionTime;
    }

    public void complete() {
        if (this.status != ReservationStatus.RUNNING) {
            throw new IllegalStateException("실행 중인 예약만 완료할 수 있습니다");
        }
        this.status = ReservationStatus.COMPLETED;
        this.actualCompletionTime = LocalDateTime.now();
    }

    public void cancel() {
        if (this.status == ReservationStatus.COMPLETED) {
            throw new IllegalStateException("완료된 예약은 취소할 수 없습니다");
        }
        this.status = ReservationStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return this.status == ReservationStatus.RESERVED || this.status == ReservationStatus.CONFIRMED
                || this.status == ReservationStatus.RUNNING;
    }

    public boolean isReserved() {
        return this.status == ReservationStatus.RESERVED;
    }

    public boolean isConfirmed() {
        return this.status == ReservationStatus.CONFIRMED;
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
