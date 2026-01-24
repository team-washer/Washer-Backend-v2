package team.washer.server.v2.domain.malfunction.service.impl;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.malfunction.dto.request.CreateMalfunctionReportReqDto;
import team.washer.server.v2.domain.malfunction.dto.response.MalfunctionReportResDto;
import team.washer.server.v2.domain.malfunction.entity.MalfunctionReport;
import team.washer.server.v2.domain.malfunction.repository.MalfunctionReportRepository;
import team.washer.server.v2.domain.malfunction.service.CreateMalfunctionReportService;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.global.common.error.exception.ExpectedException;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateMalfunctionReportServiceImpl implements CreateMalfunctionReportService {

    private final MalfunctionReportRepository malfunctionReportRepository;
    private final UserRepository userRepository;
    private final MachineRepository machineRepository;

    @Override
    @Transactional
    public MalfunctionReportResDto execute(final Long userId, final CreateMalfunctionReportReqDto reqDto) {
        final User user = userRepository.findById(userId)
                .orElseThrow(() -> new ExpectedException("사용자를 찾을 수 없습니다", HttpStatus.NOT_FOUND));

        final Machine machine = machineRepository.findById(reqDto.machineId())
                .orElseThrow(() -> new ExpectedException("기기를 찾을 수 없습니다", HttpStatus.NOT_FOUND));

        final MalfunctionReport report = MalfunctionReport.builder().machine(machine).reporter(user)
                .description(reqDto.description()).reportedAt(LocalDateTime.now()).build();

        final MalfunctionReport saved = malfunctionReportRepository.save(report);
        log.info("고장 신고 생성 완료: reportId={}, machineId={}, userId={}", saved.getId(), machine.getId(), userId);

        return toResDto(saved);
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
