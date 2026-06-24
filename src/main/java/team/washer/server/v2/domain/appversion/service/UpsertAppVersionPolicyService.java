package team.washer.server.v2.domain.appversion.service;

import team.washer.server.v2.domain.appversion.dto.request.UpsertAppVersionPolicyReqDto;
import team.washer.server.v2.domain.appversion.dto.response.AppVersionPolicyResDto;
import team.washer.server.v2.domain.appversion.enums.AppPlatform;

public interface UpsertAppVersionPolicyService {
    AppVersionPolicyResDto execute(AppPlatform platform, UpsertAppVersionPolicyReqDto reqDto);
}
