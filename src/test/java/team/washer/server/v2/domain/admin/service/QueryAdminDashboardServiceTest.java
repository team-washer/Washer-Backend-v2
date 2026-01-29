package team.washer.server.v2.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import team.washer.server.v2.domain.admin.service.impl.QueryAdminDashboardServiceImpl;
import team.washer.server.v2.domain.malfunction.enums.MalfunctionReportStatus;
import team.washer.server.v2.domain.malfunction.repository.MalfunctionReportRepository;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;

@ExtendWith(MockitoExtension.class)
class QueryAdminDashboardServiceTest {

    @InjectMocks
    private QueryAdminDashboardServiceImpl queryAdminDashboardService;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private MalfunctionReportRepository malfunctionReportRepository;

    @Nested
    @DisplayName("관리자 대시보드 통계 조회")
    class ExecuteTest {

        @Test
        @DisplayName("활성 예약, 고장 신고 통계를 성공적으로 조회한다")
        void execute_ShouldReturnDashboardStatistics_WhenDataExists() {
            // Given
            when(reservationRepository.countActiveReservations()).thenReturn(5L);
            when(malfunctionReportRepository.countByStatus(MalfunctionReportStatus.PENDING)).thenReturn(3L);
            when(malfunctionReportRepository.countByStatus(MalfunctionReportStatus.IN_PROGRESS)).thenReturn(2L);
            when(malfunctionReportRepository.countByStatus(MalfunctionReportStatus.RESOLVED)).thenReturn(10L);

            // When
            var result = queryAdminDashboardService.execute();

            // Then
            assertThat(result).isNotNull();
            assertThat(result.activeReservations()).isEqualTo(5L);
            assertThat(result.pendingMalfunctionReports()).isEqualTo(3L);
            assertThat(result.processingMalfunctionReports()).isEqualTo(2L);
            assertThat(result.completedMalfunctionReports()).isEqualTo(10L);
        }

        @Test
        @DisplayName("데이터가 없으면 모든 통계가 0으로 반환된다")
        void execute_ShouldReturnZeroStatistics_WhenNoDataExists() {
            // Given
            when(reservationRepository.countActiveReservations()).thenReturn(0L);
            when(malfunctionReportRepository.countByStatus(MalfunctionReportStatus.PENDING)).thenReturn(0L);
            when(malfunctionReportRepository.countByStatus(MalfunctionReportStatus.IN_PROGRESS)).thenReturn(0L);
            when(malfunctionReportRepository.countByStatus(MalfunctionReportStatus.RESOLVED)).thenReturn(0L);

            // When
            var result = queryAdminDashboardService.execute();

            // Then
            assertThat(result).isNotNull();
            assertThat(result.activeReservations()).isZero();
            assertThat(result.pendingMalfunctionReports()).isZero();
            assertThat(result.processingMalfunctionReports()).isZero();
            assertThat(result.completedMalfunctionReports()).isZero();
        }
    }
}
