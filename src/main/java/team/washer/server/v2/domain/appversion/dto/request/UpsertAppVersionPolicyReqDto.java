package team.washer.server.v2.domain.appversion.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@Schema(description = "앱 버전 정책 등록/수정 요청 DTO")
public record UpsertAppVersionPolicyReqDto(
        @Schema(description = "최신 앱 버전 이름", example = "1.4.0") @NotBlank(message = "최신 버전 이름은 필수입니다.") @Size(max = 50, message = "최신 버전 이름은 50자를 초과할 수 없습니다.") String latestVersionName,
        @Schema(description = "최신 앱 버전 코드", example = "18") @NotNull(message = "최신 버전 코드는 필수입니다.") @PositiveOrZero(message = "최신 버전 코드는 0 이상이어야 합니다.") Integer latestVersionCode,
        @Schema(description = "최소 지원 앱 버전 이름", example = "1.3.0") @NotBlank(message = "최소 지원 버전 이름은 필수입니다.") @Size(max = 50, message = "최소 지원 버전 이름은 50자를 초과할 수 없습니다.") String minSupportedVersionName,
        @Schema(description = "최소 지원 앱 버전 코드", example = "15") @NotNull(message = "최소 지원 버전 코드는 필수입니다.") @PositiveOrZero(message = "최소 지원 버전 코드는 0 이상이어야 합니다.") Integer minSupportedVersionCode,
        @Schema(description = "스토어 URL", example = "https://play.google.com/store/apps/details?id=team.washer") @NotBlank(message = "스토어 URL은 필수입니다.") @Size(max = 500, message = "스토어 URL은 500자를 초과할 수 없습니다.") String storeUrl,
        @Schema(description = "업데이트 안내 메시지", example = "앱 업데이트가 필요합니다.") @NotBlank(message = "업데이트 안내 메시지는 필수입니다.") @Size(max = 255, message = "업데이트 안내 메시지는 255자를 초과할 수 없습니다.") String updateMessage) {
}
