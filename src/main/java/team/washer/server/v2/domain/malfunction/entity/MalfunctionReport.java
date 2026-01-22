package team.washer.server.v2.domain.malfunction.entity;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.malfunction.enums.MalfunctionReportStatus;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.global.common.entity.BaseEntity;
import team.washer.server.v2.global.common.error.exception.ExpectedException;

@Entity
@Table(name = "malfunction_reports", indexes = {@Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_machine_id", columnList = "machine_id"),
        @Index(name = "idx_reporter_id", columnList = "reporter_id"),
        @Index(name = "idx_reported_at", columnList = "reported_at")})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MalfunctionReport extends BaseEntity {

    @NotNull(message = "기기는 필수입니다")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "machine_id", nullable = false, foreignKey = @ForeignKey(name = "fk_malfunction_machine"))
    private Machine machine;

    @NotNull(message = "신고자는 필수입니다")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false, foreignKey = @ForeignKey(name = "fk_malfunction_reporter"))
    private User reporter;

    @NotBlank(message = "신고 내용은 필수입니다")
    @Size(max = 200, message = "신고 내용은 200자를 초과할 수 없습니다")
    @Column(name = "description", nullable = false, length = 200)
    private String description;

    @NotNull(message = "신고 상태는 필수입니다")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private MalfunctionReportStatus status = MalfunctionReportStatus.PENDING;

    @NotNull(message = "신고 시간은 필수입니다")
    @Column(name = "reported_at", nullable = false)
    private LocalDateTime reportedAt;

    @Column(name = "processing_started_at")
    private LocalDateTime processingStartedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    public void startProcessing() {
        if (this.status != MalfunctionReportStatus.PENDING) {
            throw new ExpectedException("대기 중인 신고만 처리를 시작할 수 있습니다", HttpStatus.BAD_REQUEST);
        }
        this.status = MalfunctionReportStatus.IN_PROGRESS;
        this.processingStartedAt = LocalDateTime.now();
    }

    public void resolve() {
        if (this.status == MalfunctionReportStatus.RESOLVED) {
            throw new ExpectedException("이미 처리 완료된 신고입니다", HttpStatus.BAD_REQUEST);
        }
        this.status = MalfunctionReportStatus.RESOLVED;
        this.resolvedAt = LocalDateTime.now();

        this.machine.markAsNormal();
    }

    public void reopen() {
        if (this.status != MalfunctionReportStatus.RESOLVED) {
            throw new ExpectedException("처리 완료된 신고만 재개할 수 있습니다", HttpStatus.BAD_REQUEST);
        }
        this.status = MalfunctionReportStatus.IN_PROGRESS;
        this.resolvedAt = null;
    }

    public boolean isPending() {
        return this.status == MalfunctionReportStatus.PENDING;
    }

    public boolean isInProgress() {
        return this.status == MalfunctionReportStatus.IN_PROGRESS;
    }

    public boolean isResolved() {
        return this.status == MalfunctionReportStatus.RESOLVED;
    }
}
