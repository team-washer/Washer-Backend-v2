package team.washer.server.v2.domain.appversion.service;

import java.util.List;

import team.washer.server.v2.domain.appversion.dto.response.AppVersionPolicyResDto;

public interface QueryAppVersionPoliciesService {
    List<AppVersionPolicyResDto> execute();
}
