package team.washer.server.v2.domain.user.entity;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.malfunction.entity.MalfunctionReport;
import team.washer.server.v2.domain.notification.entity.Notification;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.user.enums.UserRole;
import team.washer.server.v2.global.common.constants.TimeRestrictionConstants;
import team.washer.server.v2.global.common.entity.BaseEntity;

@Entity
@Table(name = "users", indexes = {@Index(name = "idx_student_id", columnList = "student_id"),
        @Index(name = "idx_room_number", columnList = "room_number"),
        @Index(name = "idx_floor_grade", columnList = "floor, grade"),
        @Index(name = "idx_role", columnList = "role")}, uniqueConstraints = {
                @UniqueConstraint(name = "uk_student_id", columnNames = "student_id")})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

    @Column(name = "fcm_token", length = 255)
    private String fcmToken;

    // 연관관계
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Reservation> reservations = new ArrayList<>();

    @OneToMany(mappedBy = "reporter", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MalfunctionReport> malfunctionReports = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Notification> notifications = new ArrayList<>();

    /**
     * 패널티 횟수를 1 증가시킵니다.
     */
    public void incrementPenalty() {
        this.penaltyCount++;
    }

    /**
     * 패널티 횟수를 1 감소시킵니다. 이미 0이면 변경하지 않습니다.
     */
    public void decrementPenalty() {
        if (this.penaltyCount > 0) {
            this.penaltyCount--;
        }
    }

    /**
     * 패널티 횟수를 0으로 초기화합니다.
     */
    public void resetPenalty() {
        this.penaltyCount = 0;
    }

    /**
     * 시간 제한 규칙 우회 권한 여부를 반환합니다.
     *
     * @return 시간 제한 우회 가능 여부
     */
    public boolean canBypassTimeRestrictions() {
        return this.role.canBypassTimeRestrictions();
    }

    /**
     * 마지막 취소 시각을 현재 시각으로 갱신합니다.
     */
    public void updateLastCancellationTime() {
        this.lastCancellationAt = LocalDateTime.now();
    }

    /**
     * 마지막 취소 시각을 초기화합니다.
     */
    public void clearLastCancellationTime() {
        this.lastCancellationAt = null;
    }

    /**
     * FCM 토큰을 등록하거나 갱신합니다.
     *
     * @param token
     *            FCM 토큰
     */
    public void updateFcmToken(final String token) {
        this.fcmToken = token;
    }

    /**
     * FCM 토큰을 삭제합니다.
     */
    public void clearFcmToken() {
        this.fcmToken = null;
    }

    /**
     * 최근 취소로 인한 패널티 적용 여부를 반환합니다.
     *
     * @param penaltyMinutes
     *            패널티 지속 시간 (분)
     * @return 패널티 기간 내 취소 이력 존재 여부
     */
    public boolean hasRecentCancellation(int penaltyMinutes) {
        if (this.lastCancellationAt == null) {
            return false;
        }
        LocalDateTime penaltyExpiry = this.lastCancellationAt.plusMinutes(penaltyMinutes);
        return LocalDateTime.now().isBefore(penaltyExpiry);
    }

    /**
     * 예약 시간에 대한 시간 제한 규칙을 검증합니다. 규칙을 위반하면 예외를 발생시킵니다.
     *
     * @param startTime
     *            예약 시작 시간
     * @param isSundayActive
     *            일요일 예약 활성화 여부
     */
    public void validateTimeRestriction(final LocalDateTime startTime, final boolean isSundayActive) {
        if (this.canBypassTimeRestrictions()) {
            return;
        }

        final DayOfWeek dayOfWeek = startTime.getDayOfWeek();
        final LocalTime time = startTime.toLocalTime();

        switch (dayOfWeek) {
            case MONDAY, TUESDAY, WEDNESDAY, THURSDAY :
                if (time.isBefore(TimeRestrictionConstants.WEEKDAY_START_TIME)) {
                    throw new ExpectedException("월요일부터 목요일까지는 21:10 이후에만 예약할 수 있습니다", HttpStatus.BAD_REQUEST);
                }
                break;
            case SUNDAY :
                if (!isSundayActive) {
                    throw new ExpectedException("일요일 예약은 현재 비활성화되어 있습니다", HttpStatus.BAD_REQUEST);
                }
                break;
            default :
                break;
        }
    }

    /**
     * 패널티 상태를 검증합니다. 패널티 기간이 유효하면 예외를 발생시킵니다.
     *
     * @param penaltyExpiresAt
     *            패널티 만료 시각 (null이면 패널티 없음)
     */
    public void validateNotPenalized(final LocalDateTime penaltyExpiresAt) {
        if (penaltyExpiresAt != null && LocalDateTime.now().isBefore(penaltyExpiresAt)) {
            final long remainingMinutes = Duration.between(LocalDateTime.now(), penaltyExpiresAt).toMinutes();
            throw new ExpectedException(String.format("현재 예약이 제한되어 있습니다. 제한 해제까지 %d분 남았습니다.", remainingMinutes),
                    HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 사용자 정보 수정
     *
     * @param roomNumber
     *            호실 (null이면 유지)
     * @param grade
     *            학년 (null이면 유지)
     * @param floor
     *            층 (null이면 유지)
     */
    public void updateInfo(String roomNumber, Integer grade, Integer floor) {
        if (roomNumber != null) {
            this.roomNumber = roomNumber;
        }
        if (grade != null) {
            this.grade = grade;
        }
        if (floor != null) {
            this.floor = floor;
        }
    }
}
