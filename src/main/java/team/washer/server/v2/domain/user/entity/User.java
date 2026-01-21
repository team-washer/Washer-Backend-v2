package team.washer.server.v2.domain.user.entity;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import team.washer.server.v2.domain.malfunction.entity.MalfunctionReport;
import team.washer.server.v2.domain.notification.entity.Notification;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.user.enums.UserRole;
import team.washer.server.v2.global.common.entity.BaseEntity;

@Entity
@Table(name = "users", indexes = {@Index(name = "idx_student_id", columnList = "student_id"),
        @Index(name = "idx_room_number", columnList = "room_number"),
        @Index(name = "idx_floor_grade", columnList = "floor, grade"),
        @Index(name = "idx_role", columnList = "role")}, uniqueConstraints = {
                @UniqueConstraint(name = "uk_student_id", columnNames = "student_id")})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User extends BaseEntity {

    @NotBlank(message = "이름은 필수입니다")
    @Size(max = 50, message = "이름은 50자를 초과할 수 없습니다")
    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @NotBlank(message = "학번은 필수입니다")
    @Pattern(regexp = "^\\d{4,10}$", message = "학번은 4-10자리 숫자여야 합니다")
    @Column(name = "student_id", nullable = false, unique = true, length = 10)
    private String studentId;

    @NotBlank(message = "호실은 필수입니다")
    @Pattern(regexp = "^\\d{3,4}$", message = "호실은 3-4자리 숫자여야 합니다")
    @Column(name = "room_number", nullable = false, length = 4)
    private String roomNumber;

    @NotNull(message = "학년은 필수입니다")
    @Min(value = 1, message = "학년은 1 이상이어야 합니다")
    @Max(value = 4, message = "학년은 4 이하여야 합니다")
    @Column(name = "grade", nullable = false)
    private Integer grade;

    @NotNull(message = "층은 필수입니다")
    @Min(value = 1, message = "층은 1 이상이어야 합니다")
    @Column(name = "floor", nullable = false)
    private Integer floor;

    @Min(value = 0, message = "패널티 횟수는 0 이상이어야 합니다")
    @Column(name = "penalty_count", nullable = false)
    @Builder.Default
    private Integer penaltyCount = 0;

    @NotNull(message = "사용자 권한은 필수입니다")
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    @Builder.Default
    private UserRole role = UserRole.USER;

    @Column(name = "last_cancellation_at")
    private LocalDateTime lastCancellationAt;

    // Relationships
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Reservation> reservations = new ArrayList<>();

    @OneToMany(mappedBy = "reporter", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MalfunctionReport> malfunctionReports = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Notification> notifications = new ArrayList<>();

    public void incrementPenalty() {
        this.penaltyCount++;
    }

    public void decrementPenalty() {
        if (this.penaltyCount > 0) {
            this.penaltyCount--;
        }
    }

    public void resetPenalty() {
        this.penaltyCount = 0;
    }

    public boolean canBypassTimeRestrictions() {
        return this.role.canBypassTimeRestrictions();
    }

    public void updateLastCancellationTime() {
        this.lastCancellationAt = LocalDateTime.now();
    }

    public void clearLastCancellationTime() {
        this.lastCancellationAt = null;
    }

    public boolean hasRecentCancellation(int penaltyMinutes) {
        if (this.lastCancellationAt == null) {
            return false;
        }
        LocalDateTime penaltyExpiry = this.lastCancellationAt.plusMinutes(penaltyMinutes);
        return LocalDateTime.now().isBefore(penaltyExpiry);
    }

    /**
     * 예약 시간 제한 검증
     *
     * <p>
     * 사용자의 권한에 따라 시간 제한을 우회할 수 있다. 일반 사용자의 경우 월요일부터 목요일까지는 21:10 이후에만 예약할 수 있으며,
     * 일요일은 활성화된 경우에만 예약 가능하다.
     * </p>
     *
     * @param startTime
     *            예약 시작 시간
     * @param isSundayActive
     *            일요일 예약 활성화 여부
     * @throws IllegalArgumentException
     *             시간 제한에 위배되는 경우
     */
    public void validateTimeRestriction(final LocalDateTime startTime, final boolean isSundayActive) {
        if (this.canBypassTimeRestrictions()) {
            return;
        }

        final DayOfWeek dayOfWeek = startTime.getDayOfWeek();
        final LocalTime time = startTime.toLocalTime();

        switch (dayOfWeek) {
            case MONDAY, TUESDAY, WEDNESDAY, THURSDAY :
                if (time.isBefore(LocalTime.of(21, 10))) {
                    throw new IllegalArgumentException("월요일부터 목요일까지는 21:10 이후에만 예약할 수 있습니다");
                }
                break;
            case SUNDAY :
                if (!isSundayActive) {
                    throw new IllegalArgumentException("일요일 예약은 현재 비활성화되어 있습니다");
                }
                break;
            default :
                break;
        }
    }

    /**
     * 패널티 상태 검증
     *
     * <p>
     * 사용자에게 패널티가 적용되어 있는지 확인한다. 패널티가 활성 상태인 경우 예외를 발생시킨다.
     * </p>
     *
     * @param penaltyExpiresAt
     *            패널티 만료 시간
     * @throws IllegalStateException
     *             패널티가 활성 상태인 경우
     */
    public void validateNotPenalized(final LocalDateTime penaltyExpiresAt) {
        if (penaltyExpiresAt != null && LocalDateTime.now().isBefore(penaltyExpiresAt)) {
            final long remainingMinutes = Duration.between(LocalDateTime.now(), penaltyExpiresAt).toMinutes();
            throw new IllegalStateException(String.format("현재 예약이 제한되어 있습니다. 제한 해제까지 %d분 남았습니다.", remainingMinutes));
        }
    }
}
