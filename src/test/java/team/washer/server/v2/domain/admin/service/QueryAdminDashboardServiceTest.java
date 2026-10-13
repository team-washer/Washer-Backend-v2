package team.washer.server.v2.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import team.washer.server.v2.domain.admin.repository.WashingBanRepository;
import team.washer.server.v2.domain.admin.service.impl.QueryAdminDashboardServiceImpl;
import team.washer.server.v2.domain.machine.enums.MachineStatus;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.malfunction.enums.MalfunctionReportStatus;
import team.washer.server.v2.domain.malfunction.repository.MalfunctionReportRepository;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.util.PenaltyRedisUtil;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.global.common.error.code.ErrorCode;
import team.washer.server.v2.global.common.error.exception.ErrorCodeException;

@ExtendWith(MockitoExtension.class)
class QueryAdminDashboardServiceTest {

    @InjectMocks
    private QueryAdminDashboardServiceImpl queryAdminDashboardService;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private MalfunctionReportRepository malfunctionReportRepository;

    @Mock
    private MachineRepository machineRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WashingBanRepository washingBanRepository;

    @Mock
    private PenaltyRedisUtil penaltyRedisUtil;

    @Nested
    @DisplayName("관리자 대시보드 통계 조회")
    class ExecuteTest {

        @Test
        @DisplayName("활성 예약, 고장 신고, 기기, 세탁정지 학생 통계를 성공적으로 조회한다")
        void execute_ShouldReturnDashboardStatistics_WhenDataExists() {
            // Given
            when(reservationRepository.countCurrentlyActive()).thenReturn(5L);
            when(malfunctionReportRepository.countByStatus(MalfunctionReportStatus.PENDING)).thenReturn(3L);
            when(malfunctionReportRepository.countByStatus(MalfunctionReportStatus.IN_PROGRESS)).thenReturn(2L);
            when(malfunctionReportRepository.countByStatus(MalfunctionReportStatus.RESOLVED)).thenReturn(10L);
            when(machineRepository.count()).thenReturn(8L);
            when(machineRepository.countByStatus(MachineStatus.MALFUNCTION)).thenReturn(2L);
            when(penaltyRedisUtil.findCooldownUserIdsOrThrow()).thenReturn(Set.of(1L));
            when(penaltyRedisUtil.findBlockedRoomNumbersOrThrow()).thenReturn(Set.of());
            when(washingBanRepository.findAllRoomNumbers()).thenReturn(List.of());
            when(userRepository.countByIdInOrRoomNumberIn(Set.of(1L), Set.of())).thenReturn(1L);

            // When
            var result = queryAdminDashboardService.execute();

            // Then
            assertThat(result).isNotNull();
            assertThat(result.activeReservations()).isEqualTo(5L);
            assertThat(result.pendingMalfunctionReports()).isEqualTo(3L);
            assertThat(result.processingMalfunctionReports()).isEqualTo(2L);
            assertThat(result.completedMalfunctionReports()).isEqualTo(10L);
            assertThat(result.totalMachines()).isEqualTo(8L);
            assertThat(result.malfunctionMachines()).isEqualTo(2L);
            assertThat(result.suspendedStudents()).isEqualTo(1L);
        }

        @Test
        @DisplayName("데이터가 없으면 모든 통계가 0으로 반환된다")
        void execute_ShouldReturnZeroStatistics_WhenNoDataExists() {
            // Given
            when(reservationRepository.countCurrentlyActive()).thenReturn(0L);
            when(malfunctionReportRepository.countByStatus(MalfunctionReportStatus.PENDING)).thenReturn(0L);
            when(malfunctionReportRepository.countByStatus(MalfunctionReportStatus.IN_PROGRESS)).thenReturn(0L);
            when(malfunctionReportRepository.countByStatus(MalfunctionReportStatus.RESOLVED)).thenReturn(0L);
            when(machineRepository.count()).thenReturn(0L);
            when(machineRepository.countByStatus(MachineStatus.MALFUNCTION)).thenReturn(0L);
            when(penaltyRedisUtil.findCooldownUserIdsOrThrow()).thenReturn(Set.of());
            when(penaltyRedisUtil.findBlockedRoomNumbersOrThrow()).thenReturn(Set.of());
            when(washingBanRepository.findAllRoomNumbers()).thenReturn(List.of());

            // When
            var result = queryAdminDashboardService.execute();

            // Then
            assertThat(result).isNotNull();
            assertThat(result.activeReservations()).isZero();
            assertThat(result.pendingMalfunctionReports()).isZero();
            assertThat(result.processingMalfunctionReports()).isZero();
            assertThat(result.completedMalfunctionReports()).isZero();
            assertThat(result.totalMachines()).isZero();
            assertThat(result.malfunctionMachines()).isZero();
            assertThat(result.suspendedStudents()).isZero();
            verify(userRepository, never()).countByIdInOrRoomNumberIn(any(), any());
        }
    }

    @Nested
    @DisplayName("세탁 정지 학생 수 집계")
    class SuspendedStudentsTest {

        @Test
        @DisplayName("쿨다운 사용자와 Redis 차단·세탁 금지 호실을 합쳐 사용자 수를 센다")
        void it_counts_union_of_cooldown_users_and_restricted_rooms() {
            // Given
            when(penaltyRedisUtil.findCooldownUserIdsOrThrow()).thenReturn(Set.of(1L, 2L));
            when(penaltyRedisUtil.findBlockedRoomNumbersOrThrow()).thenReturn(Set.of("301", "302"));
            when(washingBanRepository.findAllRoomNumbers()).thenReturn(List.of("302", "401"));
            when(userRepository.countByIdInOrRoomNumberIn(Set.of(1L, 2L), Set.of("301", "302", "401"))).thenReturn(7L);

            // When
            var result = queryAdminDashboardService.execute();

            // Then
            assertThat(result.suspendedStudents()).isEqualTo(7L);
        }

        @Test
        @DisplayName("세탁 금지 호실만 있어도 소속 사용자를 센다")
        void it_counts_users_in_washing_banned_rooms() {
            // Given
            when(penaltyRedisUtil.findCooldownUserIdsOrThrow()).thenReturn(Set.of());
            when(penaltyRedisUtil.findBlockedRoomNumbersOrThrow()).thenReturn(Set.of());
            when(washingBanRepository.findAllRoomNumbers()).thenReturn(List.of("401"));
            when(userRepository.countByIdInOrRoomNumberIn(Set.of(), Set.of("401"))).thenReturn(3L);

            // When
            var result = queryAdminDashboardService.execute();

            // Then
            assertThat(result.suspendedStudents()).isEqualTo(3L);
        }

        @Test
        @DisplayName("쿨다운 조회에 실패하면 503 오류 코드 예외를 던진다")
        void it_throws_when_cooldown_lookup_fails() {
            // Given
            when(penaltyRedisUtil.findCooldownUserIdsOrThrow())
                    .thenThrow(new ErrorCodeException(ErrorCode.RESERVATION_RESTRICTION_UNAVAILABLE));

            // When & Then
            assertThatThrownBy(() -> queryAdminDashboardService.execute()).isInstanceOf(ErrorCodeException.class)
                    .satisfies(e -> {
                        final ErrorCodeException exception = (ErrorCodeException) e;
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RESERVATION_RESTRICTION_UNAVAILABLE);
                        assertThat(exception.getErrorCode().getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    });
            verify(userRepository, never()).countByIdInOrRoomNumberIn(any(), any());
        }

        @Test
        @DisplayName("호실 차단 조회에 실패하면 503 오류 코드 예외를 던진다")
        void it_throws_when_block_lookup_fails() {
            // Given
            when(penaltyRedisUtil.findCooldownUserIdsOrThrow()).thenReturn(Set.of(1L));
            when(penaltyRedisUtil.findBlockedRoomNumbersOrThrow())
                    .thenThrow(new ErrorCodeException(ErrorCode.RESERVATION_RESTRICTION_UNAVAILABLE));

            // When & Then
            assertThatThrownBy(() -> queryAdminDashboardService.execute()).isInstanceOf(ErrorCodeException.class)
                    .satisfies(e -> assertThat(((ErrorCodeException) e).getErrorCode())
                            .isEqualTo(ErrorCode.RESERVATION_RESTRICTION_UNAVAILABLE));
            verify(userRepository, never()).countByIdInOrRoomNumberIn(any(), any());
        }
    }
}
