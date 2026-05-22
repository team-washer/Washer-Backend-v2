package team.washer.server.v2.domain.auth.service;

import team.washer.server.v2.domain.auth.dto.request.RefreshTokenReqDto;
import team.washer.server.v2.domain.auth.dto.response.TokenStatusResDto;

public interface CheckTokenStatusService {
    TokenStatusResDto execute(RefreshTokenReqDto reqDto);
}
