package team.washer.server.v2.domain.smartthings.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import team.washer.server.v2.global.common.entity.BaseEntity;

@Entity
@Table(name = "smartthings_tokens")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmartThingsToken extends BaseEntity {

    public static final Long SINGLETON_ID = 1L;

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
        LocalDateTime expiryThreshold = now.plusMinutes(5);
        return this.expiresAt.isBefore(expiryThreshold);
    }

    public boolean isValid() {
        return !isExpiredOrExpiringSoon() && this.accessToken != null && !this.accessToken.isBlank();
    }
}
