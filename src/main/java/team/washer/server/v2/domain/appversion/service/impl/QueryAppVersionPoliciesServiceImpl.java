package team.washer.server.v2.domain.appversion.service.impl;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.appversion.dto.response.AppVersionPolicyResDto;
import team.washer.server.v2.domain.appversion.entity.AppVersionPolicy;
import team.washer.server.v2.domain.appversion.repository.AppVersionPolicyRepository;
import team.washer.server.v2.domain.appversion.service.QueryAppVersionPoliciesService;

@Service
@RequiredArgsConstructor
public class QueryAppVersionPoliciesServiceImpl implements QueryAppVersionPoliciesService {

    private final AppVersionPolicyRepository appVersionPolicyRepository;

    @Override
    @Transactional(readOnly = true)
    public List<AppVersionPolicyResDto> execute() {
        return appVersionPolicyRepository.findAll(Sort.by(Sort.Direction.ASC, "platform")).stream()
                .map(this::mapToResDto).toList();
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
