package team.washer.server.v2.domain.malfunction.service;

import team.washer.server.v2.domain.malfunction.dto.request.UpdateMalfunctionReportStatusReqDto;
import team.washer.server.v2.domain.malfunction.dto.response.MalfunctionReportResDto;

public interface UpdateMalfunctionReportStatusService {
    MalfunctionReportResDto execute(Long reportId, UpdateMalfunctionReportStatusReqDto reqDto);
}
