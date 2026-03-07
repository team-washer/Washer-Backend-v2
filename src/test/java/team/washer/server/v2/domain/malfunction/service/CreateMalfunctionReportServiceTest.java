package team.washer.server.v2.domain.malfunction.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
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

import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.MachineAvailability;
import team.washer.server.v2.domain.machine.enums.MachineStatus;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.machine.enums.Position;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.malfunction.dto.request.CreateMalfunctionReportReqDto;
import team.washer.server.v2.domain.malfunction.dto.response.MalfunctionReportResDto;
import team.washer.server.v2.domain.malfunction.entity.MalfunctionReport;
import team.washer.server.v2.domain.malfunction.enums.MalfunctionReportStatus;
import team.washer.server.v2.domain.malfunction.repository.MalfunctionReportRepository;
import team.washer.server.v2.domain.malfunction.service.impl.CreateMalfunctionReportServiceImpl;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreateMalfunctionReportServiceImpl 클래스의")
class CreateMalfunctionReportServiceTest {

    @InjectMocks
    private CreateMalfunctionReportServiceImpl createMalfunctionReportService;

    @Mock
    private MalfunctionReportRepository malfunctionReportRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MachineRepository machineRepository;

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Nested
        @DisplayName("유효한 사용자와 기기로 신고할 때")
        class Context_with_valid_user_and_machine {

            @Test
            @DisplayName("고장 신고가 생성되어야 한다")
            void it_creates_malfunction_report() {
                // Given
                Long userId = 1L;
                Long machineId = 1L;
                String description = "세탁기 작동이 되지 않습니다";
                CreateMalfunctionReportReqDto reqDto = new CreateMalfunctionReportReqDto(machineId, description);

                User user = User.builder().name("김철수").studentId("20210001").roomNumber("301").grade(3).floor(3)
                        .penaltyCount(0).build();

                Machine machine = Machine.builder().name("WASHER-3F-L1").type(MachineType.WASHER).deviceId("device-123")
                        .floor(3).position(Position.LEFT).number(1).status(MachineStatus.NORMAL)
                        .availability(MachineAvailability.AVAILABLE).build();

                MalfunctionReport savedReport = MalfunctionReport.builder().machine(machine).reporter(user)
                        .description(description).reportedAt(LocalDateTime.now()).build();

                given(userRepository.findById(userId)).willReturn(Optional.of(user));
                given(machineRepository.findById(machineId)).willReturn(Optional.of(machine));
                given(malfunctionReportRepository.save(any(MalfunctionReport.class))).willReturn(savedReport);

                // When
                MalfunctionReportResDto result = createMalfunctionReportService.execute(userId, reqDto);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.description()).isEqualTo(description);
                assertThat(result.status()).isEqualTo(MalfunctionReportStatus.PENDING);
                assertThat(result.machineName()).isEqualTo("WASHER-3F-L1");
                assertThat(result.reporterName()).isEqualTo("김철수");

                then(malfunctionReportRepository).should(times(1)).save(any(MalfunctionReport.class));
            }
        }

        @Nested
        @DisplayName("존재하지 않는 사용자 ID로 신고할 때")
        class Context_with_invalid_user_id {

            @Test
            @DisplayName("ExpectedException이 발생해야 한다")
            void it_throws_expected_exception() {
                // Given
                Long invalidUserId = 999L;
                Long machineId = 1L;
                CreateMalfunctionReportReqDto reqDto = new CreateMalfunctionReportReqDto(machineId, "고장 신고");

                given(userRepository.findById(invalidUserId)).willReturn(Optional.empty());

                // When & Then
                assertThatThrownBy(() -> createMalfunctionReportService.execute(invalidUserId, reqDto))
                        .isInstanceOf(ExpectedException.class).hasMessage("사용자를 찾을 수 없습니다").satisfies(exception -> {
                            ExpectedException expectedException = (ExpectedException) exception;
                            assertThat(expectedException.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                        });

                then(userRepository).should(times(1)).findById(invalidUserId);
                then(machineRepository).shouldHaveNoInteractions();
                then(malfunctionReportRepository).shouldHaveNoInteractions();
            }
        }

        @Nested
        @DisplayName("존재하지 않는 기기 ID로 신고할 때")
        class Context_with_invalid_machine_id {

            @Test
            @DisplayName("ExpectedException이 발생해야 한다")
            void it_throws_expected_exception() {
                // Given
                Long userId = 1L;
                Long invalidMachineId = 999L;
                CreateMalfunctionReportReqDto reqDto = new CreateMalfunctionReportReqDto(invalidMachineId, "고장 신고");

                User user = User.builder().name("김철수").studentId("20210001").roomNumber("301").grade(3).floor(3)
                        .penaltyCount(0).build();

                given(userRepository.findById(userId)).willReturn(Optional.of(user));
                given(machineRepository.findById(invalidMachineId)).willReturn(Optional.empty());

                // When & Then
                assertThatThrownBy(() -> createMalfunctionReportService.execute(userId, reqDto))
                        .isInstanceOf(ExpectedException.class).hasMessage("기기를 찾을 수 없습니다").satisfies(exception -> {
                            ExpectedException expectedException = (ExpectedException) exception;
                            assertThat(expectedException.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                        });

                then(userRepository).should(times(1)).findById(userId);
                then(machineRepository).should(times(1)).findById(invalidMachineId);
                then(malfunctionReportRepository).shouldHaveNoInteractions();
            }
        }
    }
}
