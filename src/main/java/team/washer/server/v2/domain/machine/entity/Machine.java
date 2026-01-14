package team.washer.server.v2.domain.machine.entity;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import team.washer.server.v2.global.common.entity.BaseEntity;
import team.washer.server.v2.domain.machine.enums.*;
import team.washer.server.v2.domain.malfunction.entity.MalfunctionReport;
import team.washer.server.v2.domain.reservation.entity.Reservation;

@Entity
@Table(name = "machines", indexes = {@Index(name = "idx_device_id", columnList = "device_id"),
        @Index(name = "idx_type_floor", columnList = "type, floor"),
        @Index(name = "idx_status_availability", columnList = "status, availability")}, uniqueConstraints = {
                @UniqueConstraint(name = "uk_device_id", columnNames = "device_id"),
                @UniqueConstraint(name = "uk_machine_name", columnNames = "name"),
                @UniqueConstraint(name = "uk_machine_location", columnNames = {"type", "floor", "position", "number"})})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Machine extends BaseEntity {

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

    // Relationships
    @OneToMany(mappedBy = "machine", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Reservation> reservations = new ArrayList<>();

    @OneToMany(mappedBy = "machine", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MalfunctionReport> malfunctionReports = new ArrayList<>();

    // Business Methods

    /**
     * Generate machine name from components Format:
     * {Type}-{Floor}F-{Position}{Number} Example: Washer-3F-L1, Dryer-4F-R2
     */
    public static String generateName(MachineType type, Integer floor, Position position, Integer number) {
        return String.format("%s-%dF-%s%d", type.getCode(), floor, position.getCode(), number);
    }

    /**
     * Update machine name based on current attributes
     */
    public void updateName() {
        this.name = generateName(this.type, this.floor, this.position, this.number);
    }

    public void markAsAvailable() {
        this.availability = MachineAvailability.AVAILABLE;
    }

    public void markAsInUse() {
        this.availability = MachineAvailability.IN_USE;
    }

    public void markAsReserved() {
        this.availability = MachineAvailability.RESERVED;
    }

    public void markAsUnavailable() {
        this.availability = MachineAvailability.UNAVAILABLE;
    }

    public void markAsMalfunction() {
        this.status = MachineStatus.MALFUNCTION;
        this.availability = MachineAvailability.UNAVAILABLE;
    }

    public void markAsNormal() {
        this.status = MachineStatus.NORMAL;
        this.availability = MachineAvailability.AVAILABLE;
    }

    public boolean isAvailable() {
        return this.status == MachineStatus.NORMAL && this.availability == MachineAvailability.AVAILABLE;
    }

    public boolean isWasher() {
        return this.type == MachineType.WASHER;
    }

    public boolean isDryer() {
        return this.type == MachineType.DRYER;
    }
}
