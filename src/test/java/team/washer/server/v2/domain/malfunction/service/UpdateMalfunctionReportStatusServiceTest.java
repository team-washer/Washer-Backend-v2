package team.washer.server.v2.domain.malfunction.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.MachineAvailability;
import team.washer.server.v2.domain.machine.enums.MachineStatus;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.machine.enums.Position;
import team.washer.server.v2.domain.malfunction.dto.request.UpdateMalfunctionReportStatusReqDto;
import team.washer.server.v2.domain.malfunction.dto.response.MalfunctionReportResDto;
import team.washer.server.v2.domain.malfunction.entity.MalfunctionReport;
import team.washer.server.v2.domain.malfunction.enums.MalfunctionReportStatus;
import team.washer.server.v2.domain.malfunction.repository.MalfunctionReportRepository;
import team.washer.server.v2.domain.malfunction.service.impl.UpdateMalfunctionReportStatusServiceImpl;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.global.common.error.exception.ExpectedException;

@ExtendWith(MockitoExtension.class)
@DisplayName("UpdateMalfunctionReportStatusServiceImpl 클래스의")
class UpdateMalfunctionReportStatusServiceTest {

    @InjectMocks
    private UpdateMalfunctionReportStatusServiceImpl updateMalfunctionReportStatusService;

    @Mock
    private MalfunctionReportRepository malfunctionReportRepository;

    private User createTestUser() {
        return User.builder().name("김철수").studentId("20210001").roomNumber("301").grade(3).floor(3).penaltyCount(0)
                .build();
    }

    private Machine createTestMachine() {
        return Machine.builder().name("WASHER-3F-L1").type(MachineType.WASHER).deviceId("device-123").floor(3)
                .position(Position.LEFT).number(1).status(MachineStatus.NORMAL)
                .availability(MachineAvailability.AVAILABLE).build();
    }

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Nested
        @DisplayName("IN_PROGRESS 상태로 변경할 때")
        class Context_update_to_in_progress {

            @Test
            @DisplayName("신고 처리가 시작되고 기기가 고장 상태로 변경되어야 한다")
            void it_starts_processing_and_marks_machine_as_malfunction() {
                // Given
                Long reportId = 1L;
                UpdateMalfunctionReportStatusReqDto reqDto = new UpdateMalfunctionReportStatusReqDto(
                        MalfunctionReportStatus.IN_PROGRESS);

                User user = createTestUser();
                Machine machine = createTestMachine();
                MalfunctionReport report = MalfunctionReport.builder().machine(machine).reporter(user)
                        .description("테스트 고장 신고").reportedAt(LocalDateTime.now()).build();

                given(malfunctionReportRepository.findById(reportId)).willReturn(Optional.of(report));

                // When
                MalfunctionReportResDto result = updateMalfunctionReportStatusService.execute(reportId, reqDto);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.status()).isEqualTo(MalfunctionReportStatus.IN_PROGRESS);
                assertThat(machine.getStatus()).isEqualTo(MachineStatus.MALFUNCTION);
                assertThat(machine.getAvailability()).isEqualTo(MachineAvailability.UNAVAILABLE);

                then(malfunctionReportRepository).should(times(1)).findById(reportId);
            }
        }

        @Nested
        @DisplayName("RESOLVED 상태로 변경할 때")
        class Context_update_to_resolved {

            @Test
            @DisplayName("신고가 해결되고 기기가 정상 상태로 복구되어야 한다")
            void it_resolves_report_and_marks_machine_as_normal() {
                // Given
                Long reportId = 1L;
                UpdateMalfunctionReportStatusReqDto reqDto = new UpdateMalfunctionReportStatusReqDto(
                        MalfunctionReportStatus.RESOLVED);

                User user = createTestUser();
                Machine machine = createTestMachine();
                machine.markAsMalfunction();

                MalfunctionReport report = MalfunctionReport.builder().machine(machine).reporter(user)
                        .description("테스트 고장 신고").reportedAt(LocalDateTime.now()).build();
                report.startProcessing();

                given(malfunctionReportRepository.findById(reportId)).willReturn(Optional.of(report));

                // When
                MalfunctionReportResDto result = updateMalfunctionReportStatusService.execute(reportId, reqDto);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.status()).isEqualTo(MalfunctionReportStatus.RESOLVED);
                assertThat(machine.getStatus()).isEqualTo(MachineStatus.NORMAL);
                assertThat(machine.getAvailability()).isEqualTo(MachineAvailability.AVAILABLE);

                then(malfunctionReportRepository).should(times(1)).findById(reportId);
            }
        }

        @Nested
        @DisplayName("PENDING 상태로 변경하려고 할 때")
        class Context_update_to_pending {

            @Test
            @DisplayName("ExpectedException이 발생해야 한다")
            void it_throws_expected_exception() {
                // Given
                Long reportId = 1L;
                UpdateMalfunctionReportStatusReqDto reqDto = new UpdateMalfunctionReportStatusReqDto(
                        MalfunctionReportStatus.PENDING);

                User user = createTestUser();
                Machine machine = createTestMachine();
                MalfunctionReport report = MalfunctionReport.builder().machine(machine).reporter(user)
                        .description("테스트 고장 신고").reportedAt(LocalDateTime.now()).build();

                given(malfunctionReportRepository.findById(reportId)).willReturn(Optional.of(report));

                // When & Then
                assertThatThrownBy(() -> updateMalfunctionReportStatusService.execute(reportId, reqDto))
                        .isInstanceOf(ExpectedException.class).hasMessage("대기 상태로는 변경할 수 없습니다").satisfies(exception -> {
                            ExpectedException expectedException = (ExpectedException) exception;
                            assertThat(expectedException.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                        });

                then(malfunctionReportRepository).should(times(1)).findById(reportId);
            }
        }

        @Nested
        @DisplayName("존재하지 않는 신고 ID로 변경할 때")
        class Context_with_invalid_report_id {

            @Test
            @DisplayName("ExpectedException이 발생해야 한다")
            void it_throws_expected_exception() {
                // Given
                Long invalidReportId = 999L;
                UpdateMalfunctionReportStatusReqDto reqDto = new UpdateMalfunctionReportStatusReqDto(
                        MalfunctionReportStatus.IN_PROGRESS);

                given(malfunctionReportRepository.findById(invalidReportId)).willReturn(Optional.empty());

                // When & Then
                assertThatThrownBy(() -> updateMalfunctionReportStatusService.execute(invalidReportId, reqDto))
                        .isInstanceOf(ExpectedException.class).hasMessage("고장 신고를 찾을 수 없습니다").satisfies(exception -> {
                            ExpectedException expectedException = (ExpectedException) exception;
                            assertThat(expectedException.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                        });

                then(malfunctionReportRepository).should(times(1)).findById(invalidReportId);
            }
        }
    }
}
