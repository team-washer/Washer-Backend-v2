package team.washer.server.v2.domain.machine.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.machine.dto.response.MachineDeviceStatusResDto;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.machine.service.QueryMachineDeviceStatusService;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.support.DeviceStatusQuerySupport;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.global.util.DateTimeUtil;

/**
 * 단일 기기의 SmartThings 실시간 상태를 서버가 대행 조회한다.
 *
 * <p>
 * 앱이 SmartThings 액세스 토큰 원문을 받아 직접 호출하던 흐름을 대체한다. 외부 HTTP 호출이 DB 커넥션을 점유하지 않도록
 * 트랜잭션으로 감싸지 않는다.
 */
@Service
@RequiredArgsConstructor
public class QueryMachineDeviceStatusServiceImpl implements QueryMachineDeviceStatusService {

    private final UserRepository userRepository;
    private final MachineRepository machineRepository;
    private final DeviceStatusQuerySupport deviceStatusQuerySupport;

    @Override
    public MachineDeviceStatusResDto execute(Long userId, Long machineId) {
        final var user = userRepository.findById(userId)
                .orElseThrow(() -> new ExpectedException("사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        user.validateFloorRestriction();

        final var machine = machineRepository.findById(machineId)
                .orElseThrow(() -> new ExpectedException("기기를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));

        final var deviceStatus = deviceStatusQuerySupport.queryDeviceStatus(machine.getDeviceId());
        return mapToDto(machine, deviceStatus);
    }

    private MachineDeviceStatusResDto mapToDto(Machine machine, SmartThingsDeviceStatusResDto deviceStatus) {
        final var isWasher = machine.isWasher();
        final var expectedCompletionTime = parseCompletionTime(deviceStatus.getCompletionTime(isWasher));
        final var remainingMinutes = expectedCompletionTime != null
                ? Math.max(0, Duration.between(DateTimeUtil.nowInKorea(), expectedCompletionTime).toMinutes())
                : null;

        return new MachineDeviceStatusResDto(machine.getId(),
                deviceStatus.getOperatingState(isWasher),
                deviceStatus.getJobState(isWasher),
                deviceStatus.getSwitchStatus(),
                expectedCompletionTime,
                remainingMinutes);
    }

    private LocalDateTime parseCompletionTime(String completionTime) {
        if (completionTime == null || completionTime.isBlank()) {
            return null;
        }
        return DateTimeUtil.parseAndConvertToKoreaTime(completionTime);
    }
}
