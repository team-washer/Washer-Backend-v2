package team.washer.server.v2.domain.auth.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import team.washer.server.v2.global.common.entity.BaseEntity;

@Entity
@Table(name = "withdrawn_students", uniqueConstraints = {
        @UniqueConstraint(name = "uk_withdrawn_student_id", columnNames = "student_id")})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class WithdrawnStudent extends BaseEntity {

    @Column(name = "student_id", nullable = false, length = 10)
    private String studentId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /**
     * 재탈퇴 시 30일 제한 만료 시각을 갱신합니다.
     *
     * @param expiresAt
     *            새 제한 만료 시각
     */
    public void renew(final LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}
