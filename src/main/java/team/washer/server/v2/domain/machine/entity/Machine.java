package team.washer.server.v2.domain.machine.entity;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import team.washer.server.v2.domain.machine.enums.*;
import team.washer.server.v2.domain.malfunction.entity.MalfunctionReport;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.global.common.entity.BaseEntity;
import team.washer.server.v2.global.util.DateTimeUtil;

@Entity
@Table(name = "machines", indexes = {@Index(name = "idx_device_id", columnList = "device_id"),
        @Index(name = "idx_type_floor", columnList = "type, floor"),
        @Index(name = "idx_status_availability", columnList = "status, availability")}, uniqueConstraints = {
                @UniqueConstraint(name = "uk_device_id", columnNames = "device_id"),
                @UniqueConstraint(name = "uk_machine_name", columnNames = "name"),
                @UniqueConstraint(name = "uk_machine_location", columnNames = {"type", "floor", "position", "number"})})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Machine extends BaseEntity {

    public static final Duration SHUTDOWN_CLAIM_TIMEOUT = Duration.ofSeconds(30);
    public static final Duration SHUTDOWN_COMMAND_RECOVERY_TIMEOUT = SHUTDOWN_CLAIM_TIMEOUT;

    @NotBlank(message = "기기명은 필수입니다")
    @Size(max = 50, message = "기기명은 50자를 초과할 수 없습니다")
    @Column(name = "name", nullable = false, unique = true, length = 50)
    private String name;

    @NotNull(message = "기기 유형은 필수입니다")
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private MachineType type;

    @NotBlank(message = "SmartThings Device ID는 필수입니다")
    @Size(max = 100, message = "Device ID는 100자를 초과할 수 없습니다")
    @Column(name = "device_id", nullable = false, unique = true, length = 100)
    private String deviceId;

    @NotNull(message = "층은 필수입니다")
    @Min(value = 1, message = "층은 1 이상이어야 합니다")
    @Column(name = "floor", nullable = false)
    private Integer floor;

    @NotNull(message = "위치는 필수입니다")
    @Enumerated(EnumType.STRING)
    @Column(name = "position", nullable = false, length = 5)
    private Position position;

    @NotNull(message = "번호는 필수입니다")
    @Min(value = 1, message = "번호는 1 이상이어야 합니다")
    @Column(name = "number", nullable = false)
    private Integer number;

    @NotNull(message = "기기 상태는 필수입니다")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private MachineStatus status = MachineStatus.NORMAL;

    @NotNull(message = "사용 가능 여부는 필수입니다")
    @Enumerated(EnumType.STRING)
    @Column(name = "availability", nullable = false, length = 20)
    @Builder.Default
    private MachineAvailability availability = MachineAvailability.AVAILABLE;

    /** 외부 전원 차단 작업이 진행 중인지 나타내는 내부 보호 상태입니다. */
    @Column(name = "shutdown_in_progress", nullable = false)
    @Builder.Default
    private boolean shutdownInProgress = false;

    /** 외부 전원 차단 작업의 세대 토큰입니다. API 응답에는 포함하지 않습니다. */
    @Column(name = "shutdown_claim_token", length = 36)
    private String shutdownClaimToken;

    /** 외부 전원 차단 작업을 시작한 시각입니다. */
    @Column(name = "shutdown_claimed_at")
    private LocalDateTime shutdownClaimedAt;

    /** 외부 전원 차단 명령을 시작한 시각이다. 이 시각 이후에는 명령 결과를 확인할 때까지 claim을 회수하지 않는다. */
    @Column(name = "shutdown_command_started_at")
    private LocalDateTime shutdownCommandStartedAt;

    // 연관관계
    @OneToMany(mappedBy = "machine", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Reservation> reservations = new ArrayList<>();

    @OneToMany(mappedBy = "machine", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MalfunctionReport> malfunctionReports = new ArrayList<>();

    /**
     * 기기 유형, 층, 위치, 번호를 조합하여 기기명을 생성합니다.
     *
     * @param type
     *            기기 유형
     * @param floor
     *            층
     * @param position
     *            위치
     * @param number
     *            번호
     * @return 생성된 기기명
     */
    public static String generateName(MachineType type, Integer floor, Position position, Integer number) {
        return String.format("%s-%dF-%s%d", type.getCode(), floor, position.getCode(), number);
    }

    /**
     * 현재 위치 정보를 기반으로 기기명을 재생성합니다.
     */
    public void updateName() {
        this.name = generateName(this.type, this.floor, this.position, this.number);
    }

    /**
     * 기기 사용 가능 상태로 변경합니다.
     */
    public void markAsAvailable() {
        this.availability = MachineAvailability.AVAILABLE;
    }

    /**
     * 예약 또는 사용 중으로 점유되어 있던 경우에만 기기를 사용 가능 상태로 해제합니다.
     *
     * <p>
     * 고장난 기기는 해제하지 않고 {@code UNAVAILABLE}로 되돌립니다. {@code status}가
     * {@code MALFUNCTION}이면 {@code availability}도 {@code UNAVAILABLE}이어야 한다는 불변식을
     * 예약 해제 경로에서 보장하기 위함입니다. 고장 처리 시점에 이미 진행 중이던 예약이 스케줄러에 의해 {@code IN_USE}로 전환되는
     * 등, 다른 경로에서 어긋난 상태로 들어오더라도 이 지점에서 불변식이 복원됩니다.
     *
     * <p>
     * 고장이 아닌데 {@code UNAVAILABLE}로 차단된 기기는 관리자가 의도적으로 내린 상태이므로 그대로 유지합니다.
     */
    public void releaseIfHeld() {
        if (this.status == MachineStatus.MALFUNCTION) {
            this.availability = MachineAvailability.UNAVAILABLE;
            return;
        }
        if (this.availability == MachineAvailability.RESERVED || this.availability == MachineAvailability.IN_USE) {
            this.availability = MachineAvailability.AVAILABLE;
        }
    }

    /**
     * 기기 사용 중 상태로 변경합니다.
     */
    public void markAsInUse() {
        this.availability = MachineAvailability.IN_USE;
    }

    /**
     * 기기 예약됨 상태로 변경합니다.
     */
    public void markAsReserved() {
        this.availability = MachineAvailability.RESERVED;
    }

    /**
     * 기기를 통세척 중 상태로 변경합니다.
     */
    public void markAsCleaning() {
        this.availability = MachineAvailability.CLEANING;
    }

    /**
     * 통세척 점유 중인지 반환합니다.
     *
     * @return 통세척 점유 여부
     */
    public boolean isCleaning() {
        return this.availability == MachineAvailability.CLEANING;
    }

    /**
     * 통세척 중인 기기를 정상 상태에 맞게 해제합니다.
     */
    public void finishCleaning() {
        if (this.availability != MachineAvailability.CLEANING) {
            return;
        }
        this.availability = this.status == MachineStatus.NORMAL
                ? MachineAvailability.AVAILABLE
                : MachineAvailability.UNAVAILABLE;
    }

    /**
     * 기기 사용 불가 상태로 변경합니다.
     */
    public void markAsUnavailable() {
        this.availability = MachineAvailability.UNAVAILABLE;
    }

    /**
     * 기기를 고장 상태로 변경하고 사용 불가 처리합니다.
     */
    public void markAsMalfunction() {
        this.status = MachineStatus.MALFUNCTION;
        this.availability = MachineAvailability.UNAVAILABLE;
    }

    /**
     * 기기를 정상 상태로 복구하고 사용 가능 처리합니다.
     */
    public void markAsNormal() {
        this.status = MachineStatus.NORMAL;
        this.availability = MachineAvailability.AVAILABLE;
    }

    /**
     * 기기가 정상 상태이며 사용 가능한지 반환합니다.
     *
     * @return 사용 가능 여부
     */
    public boolean isAvailable() {
        recoverExpiredShutdownClaim();
        return this.status == MachineStatus.NORMAL && this.availability == MachineAvailability.AVAILABLE
                && !this.shutdownInProgress;
    }

    /** 현재 유효한 전원 차단 선점이 있는지 상태를 변경하지 않고 판정합니다. */
    public boolean hasActiveShutdownClaim() {
        if (!this.shutdownInProgress) {
            return false;
        }
        if (this.shutdownCommandStartedAt != null) {
            return true;
        }
        return this.shutdownClaimedAt == null
                || DateTimeUtil.nowInKorea().isBefore(this.shutdownClaimedAt.plus(SHUTDOWN_CLAIM_TIMEOUT));
    }

    /** 주어진 토큰이 현재 유효한 전원 차단 선점의 소유자인지 판정합니다. */
    public boolean ownsActiveShutdownClaim(final String claimToken) {
        return hasActiveShutdownClaim() && this.shutdownClaimToken != null
                && this.shutdownClaimToken.equals(claimToken);
    }

    /** 외부 전원 차단 작업을 예약합니다. 유효한 기존 작업이 있으면 새 세대를 만들지 않습니다. */
    public Optional<String> claimShutdown() {
        recoverExpiredShutdownClaim();
        if (this.shutdownInProgress) {
            return Optional.empty();
        }
        this.shutdownInProgress = true;
        this.shutdownClaimToken = UUID.randomUUID().toString();
        this.shutdownClaimedAt = DateTimeUtil.nowInKorea();
        this.shutdownCommandStartedAt = null;
        return Optional.of(this.shutdownClaimToken);
    }

    /** 외부 전원 차단 명령을 시작해 claim 만료로부터 보호되는 상태로 전환한다. */
    public boolean beginShutdownCommand(final String claimToken) {
        if (!ownsActiveShutdownClaim(claimToken)
                || (this.shutdownCommandStartedAt != null && !isReclaimedShutdownCommandClaim())) {
            return false;
        }
        this.shutdownCommandStartedAt = DateTimeUtil.nowInKorea();
        return true;
    }

    /** 미확정 명령을 재점유한 claim인지 확인합니다. */
    private boolean isReclaimedShutdownCommandClaim() {
        return this.shutdownCommandStartedAt != null && this.shutdownClaimedAt != null
                && this.shutdownCommandStartedAt.isBefore(this.shutdownClaimedAt);
    }

    /** 현재 claim에 외부 결과가 아직 확인되지 않은 전원 차단 명령이 있는지 확인합니다. */
    public boolean hasUnresolvedShutdownCommand() {
        return this.shutdownInProgress && this.shutdownCommandStartedAt != null;
    }

    /** 외부 명령 결과가 확인되지 않은 claim을 다시 확인할 수 있는 시점인지 반환한다. */
    public boolean isShutdownCommandRecoveryReady() {
        if (this.shutdownCommandStartedAt == null) {
            return false;
        }
        var recoveryStartAt = isReclaimedShutdownCommandClaim()
                ? this.shutdownClaimedAt
                : this.shutdownCommandStartedAt;
        return !DateTimeUtil.nowInKorea().isBefore(recoveryStartAt.plus(SHUTDOWN_COMMAND_RECOVERY_TIMEOUT));
    }

    /** 외부 명령 결과가 확인되지 않은 claim을 다시 점유하되, 미확정 명령의 보호 상태는 유지합니다. */
    public boolean reclaimShutdownCommand() {
        if (!isShutdownCommandRecoveryReady()) {
            return false;
        }
        this.shutdownClaimToken = UUID.randomUUID().toString();
        this.shutdownClaimedAt = DateTimeUtil.nowInKorea();
        return true;
    }

    /** SmartThings 호출의 최대 시간보다 긴 보호 구간이 지난 작업을 복구합니다. */
    public void recoverExpiredShutdownClaim() {
        if (this.shutdownInProgress && !hasActiveShutdownClaim()) {
            clearShutdownClaim();
        }
    }

    /** 지정된 세대의 전원 차단 작업만 해제합니다. */
    public boolean releaseShutdown(final String claimToken) {
        if (!this.shutdownInProgress || this.shutdownClaimToken == null
                || !this.shutdownClaimToken.equals(claimToken)) {
            return false;
        }
        clearShutdownClaim();
        return true;
    }

    private void clearShutdownClaim() {
        this.shutdownInProgress = false;
        this.shutdownClaimToken = null;
        this.shutdownClaimedAt = null;
        this.shutdownCommandStartedAt = null;
    }

    /**
     * 세탁기 여부를 반환합니다.
     *
     * @return 세탁기 여부
     */
    public boolean isWasher() {
        return this.type == MachineType.WASHER;
    }

    /**
     * 건조기 여부를 반환합니다.
     *
     * @return 건조기 여부
     */
    public boolean isDryer() {
        return this.type == MachineType.DRYER;
    }

    /** DeviceId를 업데이트합니다. */
    public void updateDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    /** 위치 정보 및 기기 유형을 업데이트합니다. */
    public void updateLocation(MachineType type, Integer floor, Position position, Integer number) {
        this.type = type;
        this.floor = floor;
        this.position = position;
        this.number = number;
    }
}
