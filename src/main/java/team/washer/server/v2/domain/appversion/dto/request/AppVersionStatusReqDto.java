package team.washer.server.v2.domain.appversion.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import team.washer.server.v2.domain.appversion.enums.AppPlatform;

@Schema(description = "앱 버전 상태 조회 요청 DTO")
public record AppVersionStatusReqDto(
        @Schema(description = "앱 플랫폼", example = "ANDROID") @NotNull(message = "플랫폼은 필수입니다.") AppPlatform platform,
        @Schema(description = "현재 앱 버전 코드", example = "12") @NotNull(message = "버전 코드는 필수입니다.") @PositiveOrZero(message = "버전 코드는 0 이상이어야 합니다.") Integer versionCode,
        @Schema(description = "현재 앱 버전 이름", example = "1.2.3") @Size(max = 50, message = "버전 이름은 50자를 초과할 수 없습니다.") String versionName) {
}
