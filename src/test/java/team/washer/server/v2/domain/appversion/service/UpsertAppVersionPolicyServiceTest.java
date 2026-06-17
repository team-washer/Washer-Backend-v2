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
import team.washer.server.v2.domain.appversion.dto.request.UpsertAppVersionPolicyReqDto;
import team.washer.server.v2.domain.appversion.entity.AppVersionPolicy;
import team.washer.server.v2.domain.appversion.enums.AppPlatform;
import team.washer.server.v2.domain.appversion.repository.AppVersionPolicyRepository;
import team.washer.server.v2.domain.appversion.service.impl.UpsertAppVersionPolicyServiceImpl;

@ExtendWith(MockitoExtension.class)
@DisplayName("UpsertAppVersionPolicyServiceImpl 클래스는")
class UpsertAppVersionPolicyServiceTest {

    @InjectMocks
    private UpsertAppVersionPolicyServiceImpl upsertAppVersionPolicyService;

    @Mock
    private AppVersionPolicyRepository appVersionPolicyRepository;

    private UpsertAppVersionPolicyReqDto createReqDto() {
        return new UpsertAppVersionPolicyReqDto("1.4.0",
                18,
                "1.3.0",
                15,
                "https://play.google.com/store/apps/details?id=team.washer",
                "앱 업데이트가 필요합니다.");
    }

    private AppVersionPolicy createPolicy() {
        return AppVersionPolicy.builder().platform(AppPlatform.ANDROID).latestVersionName("1.3.0").latestVersionCode(15)
                .minSupportedVersionName("1.2.0").minSupportedVersionCode(12)
                .storeUrl("https://play.google.com/store/apps/details?id=team.washer").updateMessage("이전 메시지").build();
    }

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Test
        @DisplayName("기존 정책이 없으면 새 정책을 저장해야 한다")
        void it_creates_policy_when_not_exists() {
            // Given
            final var reqDto = createReqDto();
            given(appVersionPolicyRepository.findByPlatform(AppPlatform.ANDROID)).willReturn(Optional.empty());
            given(appVersionPolicyRepository.save(any(AppVersionPolicy.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            // When
            final var result = upsertAppVersionPolicyService.execute(AppPlatform.ANDROID, reqDto);

            // Then
            assertThat(result.platform()).isEqualTo(AppPlatform.ANDROID);
            assertThat(result.latestVersionCode()).isEqualTo(18);
            assertThat(result.minSupportedVersionCode()).isEqualTo(15);
            then(appVersionPolicyRepository).should(times(1)).save(any(AppVersionPolicy.class));
        }

        @Test
        @DisplayName("기존 정책이 있으면 정책 값을 갱신해야 한다")
        void it_updates_policy_when_exists() {
            // Given
            final var reqDto = createReqDto();
            final var policy = createPolicy();
            given(appVersionPolicyRepository.findByPlatform(AppPlatform.ANDROID)).willReturn(Optional.of(policy));
            given(appVersionPolicyRepository.save(policy)).willReturn(policy);

            // When
            final var result = upsertAppVersionPolicyService.execute(AppPlatform.ANDROID, reqDto);

            // Then
            assertThat(result.latestVersionName()).isEqualTo("1.4.0");
            assertThat(result.latestVersionCode()).isEqualTo(18);
            assertThat(result.minSupportedVersionName()).isEqualTo("1.3.0");
            assertThat(result.minSupportedVersionCode()).isEqualTo(15);
            assertThat(result.updateMessage()).isEqualTo("앱 업데이트가 필요합니다.");
        }

        @Test
        @DisplayName("최소 지원 버전 코드가 최신 버전 코드보다 크면 ExpectedException을 던져야 한다")
        void it_throws_expected_exception_when_min_version_code_is_greater_than_latest() {
            // Given
            final var reqDto = new UpsertAppVersionPolicyReqDto("1.4.0",
                    18,
                    "1.5.0",
                    19,
                    "https://play.google.com/store/apps/details?id=team.washer",
                    "앱 업데이트가 필요합니다.");

            // When & Then
            assertThatThrownBy(() -> upsertAppVersionPolicyService.execute(AppPlatform.ANDROID, reqDto))
                    .isInstanceOf(ExpectedException.class).hasMessage("최소 지원 버전 코드는 최신 버전 코드보다 클 수 없습니다")
                    .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);
            then(appVersionPolicyRepository).should(never()).save(any(AppVersionPolicy.class));
        }
    }
}
