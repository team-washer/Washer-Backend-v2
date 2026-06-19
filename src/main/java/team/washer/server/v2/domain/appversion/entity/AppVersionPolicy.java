package team.washer.server.v2.domain.appversion.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import team.washer.server.v2.domain.appversion.enums.AppPlatform;
import team.washer.server.v2.domain.appversion.enums.AppUpdateStatus;
import team.washer.server.v2.global.common.entity.BaseEntity;

@Entity
@Table(name = "app_version_policies", uniqueConstraints = @UniqueConstraint(name = "uk_app_version_policy_platform", columnNames = "platform"))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class AppVersionPolicy extends BaseEntity {

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 20)
    private AppPlatform platform;

    @NotBlank
    @Size(max = 50)
    @Column(name = "latest_version_name", nullable = false, length = 50)
    private String latestVersionName;

    @NotNull
    @PositiveOrZero
    @Column(name = "latest_version_code", nullable = false)
    private Integer latestVersionCode;

    @NotBlank
    @Size(max = 50)
    @Column(name = "min_supported_version_name", nullable = false, length = 50)
    private String minSupportedVersionName;

    @NotNull
    @PositiveOrZero
    @Column(name = "min_supported_version_code", nullable = false)
    private Integer minSupportedVersionCode;

    @NotBlank
    @Size(max = 500)
    @Column(name = "store_url", nullable = false, length = 500)
    private String storeUrl;

    @NotBlank
    @Size(max = 255)
    @Column(name = "update_message", nullable = false, length = 255)
    private String updateMessage;

    /**
     * 앱 버전 정책을 최신 운영 기준으로 갱신합니다.
     */
    public void updatePolicy(final String latestVersionName,
            final Integer latestVersionCode,
            final String minSupportedVersionName,
            final Integer minSupportedVersionCode,
            final String storeUrl,
            final String updateMessage) {
        this.latestVersionName = latestVersionName;
        this.latestVersionCode = latestVersionCode;
        this.minSupportedVersionName = minSupportedVersionName;
        this.minSupportedVersionCode = minSupportedVersionCode;
        this.storeUrl = storeUrl;
        this.updateMessage = updateMessage;
    }

    /**
     * 클라이언트가 보고한 버전이 기존 최신 버전보다 높으면 해당 버전을 최신 버전으로 갱신합니다.
     *
     * @param versionCode
     *            클라이언트 앱 버전 코드
     * @param versionName
     *            클라이언트 앱 버전 이름
     */
    public void registerIfHigherVersion(final int versionCode, final String versionName) {
        if (versionCode <= this.latestVersionCode) {
            return;
        }
        this.latestVersionCode = versionCode;
        if (versionName != null && !versionName.isBlank()) {
            this.latestVersionName = versionName;
        }
    }

    /**
     * 현재 앱 버전의 업데이트 상태를 계산합니다.
     *
     * @param versionCode
     *            현재 앱 버전 코드
     * @return 업데이트 상태
     */
    public AppUpdateStatus resolveUpdateStatus(final int versionCode) {
        if (versionCode < minSupportedVersionCode) {
            return AppUpdateStatus.UPDATE_REQUIRED;
        }
        if (versionCode < latestVersionCode) {
            return AppUpdateStatus.UPDATE_AVAILABLE;
        }
        return AppUpdateStatus.SUPPORTED;
    }
}
