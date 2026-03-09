package team.washer.server.v2.domain.auth.service;

import team.washer.server.v2.domain.auth.dto.request.RefreshTokenReqDto;
import team.washer.server.v2.domain.auth.dto.response.TokenResDto;

public interface RefreshTokenService {
    TokenResDto execute(RefreshTokenReqDto reqDto);
}
