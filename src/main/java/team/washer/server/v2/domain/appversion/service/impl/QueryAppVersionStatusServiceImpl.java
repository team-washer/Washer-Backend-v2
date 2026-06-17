package team.washer.server.v2.domain.appversion.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.appversion.dto.request.AppVersionStatusReqDto;
import team.washer.server.v2.domain.appversion.dto.response.AppVersionStatusResDto;
import team.washer.server.v2.domain.appversion.entity.AppVersionPolicy;
import team.washer.server.v2.domain.appversion.enums.AppUpdateStatus;
import team.washer.server.v2.domain.appversion.repository.AppVersionPolicyRepository;
import team.washer.server.v2.domain.appversion.service.QueryAppVersionStatusService;

@Service
@RequiredArgsConstructor
public class QueryAppVersionStatusServiceImpl implements QueryAppVersionStatusService {

    private final AppVersionPolicyRepository appVersionPolicyRepository;

    @Override
    @Transactional(readOnly = true)
    public AppVersionStatusResDto execute(final AppVersionStatusReqDto reqDto) {
        final var policy = appVersionPolicyRepository.findByPlatform(reqDto.platform())
                .orElseThrow(() -> new ExpectedException("앱 버전 정책을 찾을 수 없습니다", HttpStatus.NOT_FOUND));
        final var updateStatus = policy.resolveUpdateStatus(reqDto.versionCode());

        return mapToStatusResDto(policy, reqDto, updateStatus);
    }

    private AppVersionStatusResDto mapToStatusResDto(final AppVersionPolicy policy,
            final AppVersionStatusReqDto reqDto,
            final AppUpdateStatus updateStatus) {
        return new AppVersionStatusResDto(policy.getPlatform(),
                reqDto.versionName(),
                reqDto.versionCode(),
                policy.getLatestVersionName(),
                policy.getLatestVersionCode(),
                policy.getMinSupportedVersionName(),
                policy.getMinSupportedVersionCode(),
                updateStatus == AppUpdateStatus.UPDATE_REQUIRED,
                updateStatus != AppUpdateStatus.SUPPORTED,
                updateStatus,
                policy.getStoreUrl(),
                policy.getUpdateMessage());
    }
}
