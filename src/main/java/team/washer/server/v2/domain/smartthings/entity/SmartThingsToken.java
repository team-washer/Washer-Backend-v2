package team.washer.server.v2.domain.smartthings.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Entity
@Table(name = "smartthings_tokens")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmartThingsToken {

    public static final Long SINGLETON_ID = 1L;
    private static final int EXPIRY_BUFFER_MINUTES = 5;

    @Id
    @Builder.Default
    private Long id = SINGLETON_ID;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @NotBlank(message = "Access Token은 필수입니다")
    @Column(name = "access_token", nullable = false, length = 500)
    private String accessToken;

    @NotBlank(message = "Refresh Token은 필수입니다")
    @Column(name = "refresh_token", nullable = false, length = 500)
    private String refreshToken;

    @NotNull(message = "만료 시간은 필수입니다")
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    public void updateTokens(String accessToken, String refreshToken, LocalDateTime expiresAt) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresAt = expiresAt;
    }

    public boolean isExpiredOrExpiringSoon() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiryThreshold = now.plusMinutes(EXPIRY_BUFFER_MINUTES);
        return this.expiresAt.isBefore(expiryThreshold);
    }

    public boolean isValid() {
        return !isExpiredOrExpiringSoon() && this.accessToken != null && !this.accessToken.isBlank();
    }
}
