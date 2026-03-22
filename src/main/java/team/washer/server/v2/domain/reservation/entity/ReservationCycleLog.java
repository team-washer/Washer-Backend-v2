package team.washer.server.v2.domain.reservation.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import team.washer.server.v2.domain.reservation.enums.CycleAction;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.global.common.entity.BaseEntity;

@Entity
@Table(name = "reservation_cycle_logs", indexes = {@Index(name = "idx_created_at", columnList = "created_at"),
        @Index(name = "idx_action", columnList = "action"),
        @Index(name = "idx_performed_by", columnList = "performed_by_id")})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationCycleLog extends BaseEntity {

    @NotNull(message = "활성화 상태는 필수입니다")
    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @NotNull(message = "액션 타입은 필수입니다")
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private CycleAction action;

    @NotNull(message = "수행자는 필수입니다")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "performed_by_id", nullable = false, foreignKey = @ForeignKey(name = "fk_cycle_log_user"))
    private User performedBy;

    @Column(name = "notes", length = 500)
    private String notes;
}
