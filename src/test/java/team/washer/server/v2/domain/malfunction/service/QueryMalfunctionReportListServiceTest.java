package team.washer.server.v2.domain.malfunction.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.MachineAvailability;
import team.washer.server.v2.domain.machine.enums.MachineStatus;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.machine.enums.Position;
import team.washer.server.v2.domain.malfunction.dto.response.MalfunctionReportListResDto;
import team.washer.server.v2.domain.malfunction.entity.MalfunctionReport;
import team.washer.server.v2.domain.malfunction.enums.MalfunctionReportStatus;
import team.washer.server.v2.domain.malfunction.repository.MalfunctionReportRepository;
import team.washer.server.v2.domain.malfunction.service.impl.QueryMalfunctionReportListServiceImpl;
import team.washer.server.v2.domain.user.entity.User;

@ExtendWith(MockitoExtension.class)
@DisplayName("QueryMalfunctionReportListServiceImpl 클래스의")
class QueryMalfunctionReportListServiceTest {

    @InjectMocks
    private QueryMalfunctionReportListServiceImpl queryMalfunctionReportListService;

    @Mock
    private MalfunctionReportRepository malfunctionReportRepository;

    private User createTestUser() {
        return User.builder().name("김철수").studentId("20210001").roomNumber("301").grade(3).floor(3).penaltyCount(0)
                .build();
    }

    private Machine createTestMachine(String name) {
        return Machine.builder().name(name).type(MachineType.WASHER).deviceId("device-123").floor(3)
                .position(Position.LEFT).number(1).status(MachineStatus.NORMAL)
                .availability(MachineAvailability.AVAILABLE).build();
    }

    private MalfunctionReport createTestReport(Machine machine, User user, MalfunctionReportStatus status) {
        return MalfunctionReport.builder().machine(machine).reporter(user).description("테스트 고장 신고")
                .reportedAt(LocalDateTime.now()).build();
    }

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Nested
        @DisplayName("상태 필터 없이 조회할 때")
        class Context_without_status_filter {

            @Test
            @DisplayName("전체 신고 목록을 반환해야 한다")
            void it_returns_all_reports() {
                // Given
                User user = createTestUser();
                Machine machine1 = createTestMachine("WASHER-3F-L1");
                Machine machine2 = createTestMachine("WASHER-3F-L2");

                MalfunctionReport report1 = createTestReport(machine1, user, MalfunctionReportStatus.PENDING);
                MalfunctionReport report2 = createTestReport(machine2, user, MalfunctionReportStatus.IN_PROGRESS);

                List<MalfunctionReport> allReports = List.of(report1, report2);
                given(malfunctionReportRepository.findAll()).willReturn(allReports);

                // When
                MalfunctionReportListResDto result = queryMalfunctionReportListService.execute(null);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.totalCount()).isEqualTo(2);
                assertThat(result.reports()).hasSize(2);

                then(malfunctionReportRepository).should(times(1)).findAll();
                then(malfunctionReportRepository).should(never()).findByStatus(any());
            }
        }

        @Nested
        @DisplayName("특정 상태로 필터링하여 조회할 때")
        class Context_with_status_filter {

            @Test
            @DisplayName("해당 상태의 신고 목록만 반환해야 한다")
            void it_returns_filtered_reports() {
                // Given
                User user = createTestUser();
                Machine machine = createTestMachine("WASHER-3F-L1");
                MalfunctionReport pendingReport = createTestReport(machine, user, MalfunctionReportStatus.PENDING);

                List<MalfunctionReport> pendingReports = List.of(pendingReport);
                given(malfunctionReportRepository.findByStatus(MalfunctionReportStatus.PENDING))
                        .willReturn(pendingReports);

                // When
                MalfunctionReportListResDto result = queryMalfunctionReportListService
                        .execute(MalfunctionReportStatus.PENDING);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.totalCount()).isEqualTo(1);
                assertThat(result.reports()).hasSize(1);

                then(malfunctionReportRepository).should(times(1)).findByStatus(MalfunctionReportStatus.PENDING);
                then(malfunctionReportRepository).should(never()).findAll();
            }
        }

        @Nested
        @DisplayName("신고가 없을 때")
        class Context_with_no_reports {

            @Test
            @DisplayName("빈 목록을 반환해야 한다")
            void it_returns_empty_list() {
                // Given
                given(malfunctionReportRepository.findAll()).willReturn(List.of());

                // When
                MalfunctionReportListResDto result = queryMalfunctionReportListService.execute(null);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.totalCount()).isEqualTo(0);
                assertThat(result.reports()).isEmpty();

                then(malfunctionReportRepository).should(times(1)).findAll();
            }
        }
    }
}
