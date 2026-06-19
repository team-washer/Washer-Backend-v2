package team.washer.server.v2.domain.appversion.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.appversion.dto.request.AppVersionStatusReqDto;
import team.washer.server.v2.domain.appversion.entity.AppVersionPolicy;
import team.washer.server.v2.domain.appversion.enums.AppPlatform;
import team.washer.server.v2.domain.appversion.enums.AppUpdateStatus;
import team.washer.server.v2.domain.appversion.repository.AppVersionPolicyRepository;
import team.washer.server.v2.domain.appversion.service.impl.QueryAppVersionStatusServiceImpl;

@ExtendWith(MockitoExtension.class)
@DisplayName("QueryAppVersionStatusServiceImpl 클래스는")
class QueryAppVersionStatusServiceTest {

    @InjectMocks
    private QueryAppVersionStatusServiceImpl queryAppVersionStatusService;

    @Mock
    private AppVersionPolicyRepository appVersionPolicyRepository;

    private AppVersionPolicy createPolicy() {
        return AppVersionPolicy.builder().platform(AppPlatform.ANDROID).latestVersionName("1.4.0").latestVersionCode(18)
                .minSupportedVersionName("1.3.0").minSupportedVersionCode(15)
                .storeUrl("https://play.google.com/store/apps/details?id=team.washer").updateMessage("앱 업데이트가 필요합니다.")
                .build();
    }

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Test
        @DisplayName("최소 지원 버전보다 낮으면 강제 업데이트 상태를 반환해야 한다")
        void it_returns_required_update_status() {
            // Given
            final var reqDto = new AppVersionStatusReqDto(AppPlatform.ANDROID, 14, "1.2.0");
            given(appVersionPolicyRepository.findByPlatform(AppPlatform.ANDROID))
                    .willReturn(Optional.of(createPolicy()));

            // When
            final var result = queryAppVersionStatusService.execute(reqDto);

            // Then
            assertThat(result.updateStatus()).isEqualTo(AppUpdateStatus.UPDATE_REQUIRED);
            assertThat(result.updateRequired()).isTrue();
            assertThat(result.updateAvailable()).isTrue();
            assertThat(result.currentVersionName()).isEqualTo("1.2.0");
            assertThat(result.currentVersionCode()).isEqualTo(14);
        }

        @Test
        @DisplayName("최신 버전보다 낮고 최소 지원 버전 이상이면 선택 업데이트 상태를 반환해야 한다")
        void it_returns_available_update_status() {
            // Given
            final var reqDto = new AppVersionStatusReqDto(AppPlatform.ANDROID, 16, "1.3.1");
            given(appVersionPolicyRepository.findByPlatform(AppPlatform.ANDROID))
                    .willReturn(Optional.of(createPolicy()));

            // When
            final var result = queryAppVersionStatusService.execute(reqDto);

            // Then
            assertThat(result.updateStatus()).isEqualTo(AppUpdateStatus.UPDATE_AVAILABLE);
            assertThat(result.updateRequired()).isFalse();
            assertThat(result.updateAvailable()).isTrue();
        }

        @Test
        @DisplayName("최신 버전 이상이면 지원 상태를 반환해야 한다")
        void it_returns_supported_status() {
            // Given
            final var reqDto = new AppVersionStatusReqDto(AppPlatform.ANDROID, 18, "1.4.0");
            given(appVersionPolicyRepository.findByPlatform(AppPlatform.ANDROID))
                    .willReturn(Optional.of(createPolicy()));

            // When
            final var result = queryAppVersionStatusService.execute(reqDto);

            // Then
            assertThat(result.updateStatus()).isEqualTo(AppUpdateStatus.SUPPORTED);
            assertThat(result.updateRequired()).isFalse();
            assertThat(result.updateAvailable()).isFalse();
        }

        @Test
        @DisplayName("클라이언트 버전이 기존 최신 버전보다 높으면 최신 버전을 갱신하고 지원 상태를 반환해야 한다")
        void it_registers_new_latest_version_when_client_version_is_higher() {
            // Given
            final var policy = createPolicy();
            final var reqDto = new AppVersionStatusReqDto(AppPlatform.ANDROID, 20, "1.5.0");
            given(appVersionPolicyRepository.findByPlatform(AppPlatform.ANDROID)).willReturn(Optional.of(policy));

            // When
            final var result = queryAppVersionStatusService.execute(reqDto);

            // Then
            assertThat(policy.getLatestVersionCode()).isEqualTo(20);
            assertThat(policy.getLatestVersionName()).isEqualTo("1.5.0");
            assertThat(result.latestVersionCode()).isEqualTo(20);
            assertThat(result.latestVersionName()).isEqualTo("1.5.0");
            assertThat(result.updateStatus()).isEqualTo(AppUpdateStatus.SUPPORTED);
            assertThat(result.updateRequired()).isFalse();
            assertThat(result.updateAvailable()).isFalse();
        }

        @Test
        @DisplayName("클라이언트 버전이 기존 최신 버전보다 높지만 버전 이름이 없으면 버전 코드만 갱신해야 한다")
        void it_registers_only_version_code_when_version_name_is_missing() {
            // Given
            final var policy = createPolicy();
            final var reqDto = new AppVersionStatusReqDto(AppPlatform.ANDROID, 20, null);
            given(appVersionPolicyRepository.findByPlatform(AppPlatform.ANDROID)).willReturn(Optional.of(policy));

            // When
            queryAppVersionStatusService.execute(reqDto);

            // Then
            assertThat(policy.getLatestVersionCode()).isEqualTo(20);
            assertThat(policy.getLatestVersionName()).isEqualTo("1.4.0");
        }

        @Test
        @DisplayName("클라이언트 버전이 기존 최신 버전과 같으면 최신 버전을 갱신하지 않아야 한다")
        void it_keeps_latest_version_when_client_version_is_equal() {
            // Given
            final var policy = createPolicy();
            final var reqDto = new AppVersionStatusReqDto(AppPlatform.ANDROID, 18, "1.4.0");
            given(appVersionPolicyRepository.findByPlatform(AppPlatform.ANDROID)).willReturn(Optional.of(policy));

            // When
            final var result = queryAppVersionStatusService.execute(reqDto);

            // Then
            assertThat(policy.getLatestVersionCode()).isEqualTo(18);
            assertThat(result.updateStatus()).isEqualTo(AppUpdateStatus.SUPPORTED);
        }

        @Test
        @DisplayName("플랫폼 정책이 없으면 ExpectedException을 던져야 한다")
        void it_throws_expected_exception_when_policy_not_found() {
            // Given
            final var reqDto = new AppVersionStatusReqDto(AppPlatform.IOS, 1, "1.0.0");
            given(appVersionPolicyRepository.findByPlatform(AppPlatform.IOS)).willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> queryAppVersionStatusService.execute(reqDto)).isInstanceOf(ExpectedException.class)
                    .hasMessage("앱 버전 정책을 찾을 수 없습니다").hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
        }
    }
}
