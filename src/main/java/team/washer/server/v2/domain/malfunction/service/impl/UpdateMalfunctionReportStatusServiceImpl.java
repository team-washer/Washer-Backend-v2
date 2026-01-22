package team.washer.server.v2.domain.malfunction.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.malfunction.dto.request.UpdateMalfunctionReportStatusReqDto;
import team.washer.server.v2.domain.malfunction.dto.response.MalfunctionReportResDto;
import team.washer.server.v2.domain.malfunction.entity.MalfunctionReport;
import team.washer.server.v2.domain.malfunction.enums.MalfunctionReportStatus;
import team.washer.server.v2.domain.malfunction.repository.MalfunctionReportRepository;
import team.washer.server.v2.domain.malfunction.service.UpdateMalfunctionReportStatusService;
import team.washer.server.v2.global.common.error.exception.ExpectedException;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateMalfunctionReportStatusServiceImpl implements UpdateMalfunctionReportStatusService {

    private final MalfunctionReportRepository malfunctionReportRepository;

    @Override
    @Transactional
    public MalfunctionReportResDto execute(final Long reportId, final UpdateMalfunctionReportStatusReqDto reqDto) {
        final MalfunctionReport report = malfunctionReportRepository.findById(reportId)
                .orElseThrow(() -> new ExpectedException("고장 신고를 찾을 수 없습니다", HttpStatus.NOT_FOUND));

        final MalfunctionReportStatus newStatus = reqDto.status();

        switch (newStatus) {
            case PENDING -> throw new ExpectedException("대기 상태로는 변경할 수 없습니다", HttpStatus.BAD_REQUEST);
            case IN_PROGRESS -> {
                report.startProcessing();
                report.getMachine().markAsMalfunction();
                log.info("고장 신고 처리 시작: reportId={}, machineId={}", reportId, report.getMachine().getId());
            }
            case RESOLVED -> {
                report.resolve();
                log.info("고장 신고 처리 완료: reportId={}, machineId={}", reportId, report.getMachine().getId());
            }
        }

        return toResDto(report);
    }

    private MalfunctionReportResDto toResDto(final MalfunctionReport report) {
        return new MalfunctionReportResDto(report.getId(),
                report.getMachine().getId(),
                report.getMachine().getName(),
                report.getReporter().getId(),
                report.getReporter().getName(),
                report.getDescription(),
                report.getStatus(),
                report.getReportedAt(),
                report.getProcessingStartedAt(),
                report.getResolvedAt(),
                report.getCreatedAt(),
                report.getUpdatedAt());
    }
}
