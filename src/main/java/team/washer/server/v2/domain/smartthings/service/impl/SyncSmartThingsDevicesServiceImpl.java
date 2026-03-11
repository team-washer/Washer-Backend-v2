package team.washer.server.v2.domain.smartthings.service.impl;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.MachineAvailability;
import team.washer.server.v2.domain.machine.enums.MachineStatus;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.machine.enums.Position;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceListResDto.DeviceItem;
import team.washer.server.v2.domain.smartthings.repository.SmartThingsTokenRepository;
import team.washer.server.v2.domain.smartthings.service.SyncSmartThingsDevicesService;
import team.washer.server.v2.global.thirdparty.smartthings.feign.SmartThingsFeignClient;

/**
 * SmartThings 기기 목록 동기화 서비스 구현체
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SyncSmartThingsDevicesServiceImpl implements SyncSmartThingsDevicesService {

    private static final Pattern LABEL_PATTERN = Pattern.compile("^(Washer|Dryer)-(\\d+)F-(L|R)(\\d+)$");

    private final SmartThingsFeignClient feignClient;
    private final SmartThingsTokenRepository tokenRepository;
    private final MachineRepository machineRepository;

    @Override
    @Transactional
    public void execute() {
        var token = tokenRepository.findSingletonToken()
                .orElseThrow(() -> new ExpectedException("SmartThings 토큰이 존재하지 않습니다", HttpStatus.NOT_FOUND));

        if (!token.isValid()) {
            throw new ExpectedException("SmartThings 토큰이 만료되었거나 유효하지 않습니다", HttpStatus.UNAUTHORIZED);
        }

        var authorization = "Bearer " + token.getAccessToken();
        var items = feignClient.getDeviceList(authorization).items();

        // SmartThings에서 조회된 deviceId 집합
        var smartThingsDeviceIds = items.stream().map(DeviceItem::deviceId).collect(Collectors.toSet());

        // 각 기기 항목 처리 (생성 또는 갱신)
        for (var item : items) {
            processDeviceItem(item);
        }

        // API 오류로 빈 목록이 반환된 경우 전체 비활성화 방지
        if (smartThingsDeviceIds.isEmpty()) {
            log.warn("SmartThings 기기 목록이 비어있습니다. 비활성화 처리를 건너뜁니다.");
            return;
        }

        // SmartThings에서 사라진 기기 비활성화
        deactivateMissingDevices(smartThingsDeviceIds);
    }

    /**
     * 개별 SmartThings 기기 항목을 처리합니다. label을 파싱하여 Machine을 생성하거나 갱신합니다.
     */
    private void processDeviceItem(DeviceItem item) {
        var parsedLabelOpt = parseLabel(item.label());
        if (parsedLabelOpt.isEmpty()) {
            log.warn("SmartThings 기기 label 형식 불일치: {}", item.label());
            return;
        }

        var parsedLabel = parsedLabelOpt.get();
        var machineName = Machine
                .generateName(parsedLabel.type(), parsedLabel.floor(), parsedLabel.position(), parsedLabel.number());

        var existingMachine = machineRepository.findByName(machineName);
        if (existingMachine.isPresent()) {
            var machine = existingMachine.get();
            if (!item.deviceId().equals(machine.getDeviceId())) {
                machine.updateDeviceId(item.deviceId());
                log.info("Machine deviceId 갱신: name={}, deviceId={}", machineName, item.deviceId());
            }
        } else {
            var newMachine = Machine.builder().deviceId(item.deviceId()).name(machineName).type(parsedLabel.type())
                    .floor(parsedLabel.floor()).position(parsedLabel.position()).number(parsedLabel.number())
                    .status(MachineStatus.NORMAL).availability(MachineAvailability.AVAILABLE).build();
            machineRepository.save(newMachine);
            log.info("신규 Machine 생성: name={}, deviceId={}", machineName, item.deviceId());
        }
    }

    /**
     * SmartThings에 더 이상 존재하지 않는 deviceId를 가진 Machine을 비활성화합니다.
     */
    private void deactivateMissingDevices(Set<String> smartThingsDeviceIds) {
        var allMachines = machineRepository.findAll();
        for (var machine : allMachines) {
            if (!smartThingsDeviceIds.contains(machine.getDeviceId())) {
                machine.markAsUnavailable();
                log.info("SmartThings에서 제거된 기기 비활성화: name={}, deviceId={}", machine.getName(), machine.getDeviceId());
            }
        }
    }

    /**
     * SmartThings device label을 파싱합니다. 패턴:
     * {@code ^(Washer|Dryer)-(\d+)F-(L|R)(\d+)$}
     *
     * @param label
     *            SmartThings device label (예: {@code Washer-1F-L1})
     * @return 파싱 결과, 형식 불일치 시 {@code Optional.empty()}
     */
    private Optional<ParsedLabel> parseLabel(String label) {
        if (label == null) {
            return Optional.empty();
        }
        var matcher = LABEL_PATTERN.matcher(label);
        if (!matcher.matches()) {
            return Optional.empty();
        }

        var type = matcher.group(1).equals("Washer") ? MachineType.WASHER : MachineType.DRYER;
        var floor = Integer.parseInt(matcher.group(2));
        var position = matcher.group(3).equals("L") ? Position.LEFT : Position.RIGHT;
        var number = Integer.parseInt(matcher.group(4));

        return Optional.of(new ParsedLabel(type, floor, position, number));
    }

    private record ParsedLabel(MachineType type, int floor, Position position, int number) {
    }
}
