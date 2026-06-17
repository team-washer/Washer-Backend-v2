package team.washer.server.v2.domain.admin.service;

import java.util.List;

import team.washer.server.v2.domain.admin.dto.response.WashingBanResDto;

public interface QueryAllWashingBansService {
    List<WashingBanResDto> execute();
}
