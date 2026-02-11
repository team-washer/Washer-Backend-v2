package team.washer.server.v2.domain.auth.service;

import team.washer.server.v2.domain.auth.dto.request.TokenReqDto;
import team.washer.server.v2.domain.auth.dto.response.TokenResDto;

public interface SignInService {
    TokenResDto execute(TokenReqDto reqDto);
}
