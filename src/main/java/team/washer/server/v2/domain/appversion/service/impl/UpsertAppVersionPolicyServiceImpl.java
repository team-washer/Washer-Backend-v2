package team.washer.server.v2.domain.appversion.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.appversion.dto.request.UpsertAppVersionPolicyReqDto;
import team.washer.server.v2.domain.appversion.dto.response.AppVersionPolicyResDto;
import team.washer.server.v2.domain.appversion.entity.AppVersionPolicy;
import team.washer.server.v2.domain.appversion.enums.AppPlatform;
import team.washer.server.v2.domain.appversion.repository.AppVersionPolicyRepository;
import team.washer.server.v2.domain.appversion.service.UpsertAppVersionPolicyService;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpsertAppVersionPolicyServiceImpl implements UpsertAppVersionPolicyService {

    private final AppVersionPolicyRepository appVersionPolicyRepository;

    @Override
    @Transactional
    public AppVersionPolicyResDto execute(final AppPlatform platform, final UpsertAppVersionPolicyReqDto reqDto) {
        validateVersionCodeRange(reqDto);

        final var policy = appVersionPolicyRepository.findByPlatform(platform)
                .orElseGet(() -> AppVersionPolicy.builder().platform(platform).build());

        policy.updatePolicy(reqDto.latestVersionName(),
                reqDto.latestVersionCode(),
                reqDto.minSupportedVersionName(),
                reqDto.minSupportedVersionCode(),
                reqDto.storeUrl(),
                reqDto.updateMessage());
        final var savedPolicy = appVersionPolicyRepository.save(policy);
        log.info("App version policy upserted platform={} latestVersionCode={} minSupportedVersionCode={}",
                platform,
                reqDto.latestVersionCode(),
                reqDto.minSupportedVersionCode());

        return mapToResDto(savedPolicy);
    }

    private void validateVersionCodeRange(final UpsertAppVersionPolicyReqDto reqDto) {
        if (reqDto.minSupportedVersionCode() > reqDto.latestVersionCode()) {
            throw new ExpectedException("최소 지원 버전 코드는 최신 버전 코드보다 클 수 없습니다", HttpStatus.BAD_REQUEST);
        }
    }

    private AppVersionPolicyResDto mapToResDto(final AppVersionPolicy policy) {
        return new AppVersionPolicyResDto(policy.getPlatform(),
                policy.getLatestVersionName(),
                policy.getLatestVersionCode(),
                policy.getMinSupportedVersionName(),
                policy.getMinSupportedVersionCode(),
                policy.getStoreUrl(),
                policy.getUpdateMessage());
    }
}
