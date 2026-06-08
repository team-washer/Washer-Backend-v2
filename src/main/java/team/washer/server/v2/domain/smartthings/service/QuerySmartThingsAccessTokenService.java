package team.washer.server.v2.domain.smartthings.service;

import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsAccessTokenResDto;

/**
 * 현재 유효한 SmartThings 액세스 토큰을 조회하는 서비스
 */
public interface QuerySmartThingsAccessTokenService {

    SmartThingsAccessTokenResDto execute();
}
