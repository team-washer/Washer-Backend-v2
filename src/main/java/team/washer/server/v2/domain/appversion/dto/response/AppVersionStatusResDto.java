package team.washer.server.v2.domain.appversion.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import team.washer.server.v2.domain.appversion.enums.AppPlatform;
import team.washer.server.v2.domain.appversion.enums.AppUpdateStatus;

@Schema(description = "앱 버전 상태 응답 DTO")
public record AppVersionStatusResDto(@Schema(description = "앱 플랫폼", example = "ANDROID") AppPlatform platform,
        @Schema(description = "현재 앱 버전 이름", example = "1.2.3") String currentVersionName,
        @Schema(description = "현재 앱 버전 코드", example = "12") Integer currentVersionCode,
        @Schema(description = "최신 앱 버전 이름", example = "1.4.0") String latestVersionName,
        @Schema(description = "최신 앱 버전 코드", example = "18") Integer latestVersionCode,
        @Schema(description = "최소 지원 앱 버전 이름", example = "1.3.0") String minSupportedVersionName,
        @Schema(description = "최소 지원 앱 버전 코드", example = "15") Integer minSupportedVersionCode,
        @Schema(description = "강제 업데이트 필요 여부", example = "true") boolean updateRequired,
        @Schema(description = "업데이트 가능 여부", example = "true") boolean updateAvailable,
        @Schema(description = "업데이트 상태", example = "UPDATE_REQUIRED") AppUpdateStatus updateStatus,
        @Schema(description = "스토어 URL", example = "https://play.google.com/store/apps/details?id=team.washer") String storeUrl,
        @Schema(description = "업데이트 안내 메시지", example = "앱 업데이트가 필요합니다.") String updateMessage) {
}
