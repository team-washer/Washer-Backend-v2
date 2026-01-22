package team.washer.server.v2.domain.malfunction.service;

import team.washer.server.v2.domain.malfunction.dto.request.CreateMalfunctionReportReqDto;
import team.washer.server.v2.domain.malfunction.dto.response.MalfunctionReportResDto;

public interface CreateMalfunctionReportService {
    MalfunctionReportResDto execute(Long userId, CreateMalfunctionReportReqDto reqDto);
}
