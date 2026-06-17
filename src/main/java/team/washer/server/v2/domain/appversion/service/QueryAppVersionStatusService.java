package team.washer.server.v2.domain.appversion.service;

import team.washer.server.v2.domain.appversion.dto.request.AppVersionStatusReqDto;
import team.washer.server.v2.domain.appversion.dto.response.AppVersionStatusResDto;

public interface QueryAppVersionStatusService {
    AppVersionStatusResDto execute(AppVersionStatusReqDto reqDto);
}
