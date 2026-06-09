package team.washer.server.v2.domain.smartthings.support;

import java.util.concurrent.CompletableFuture;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.smartthings.dto.request.SmartThingsCommandReqDto;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.service.SendDeviceCommandService;

/**
 * [임시 검증 코드] 실기기 대상 "전원 ON → 안전 종료" 1회성 사이클 테스트 실행기.
 *
 * <p>
 * 애플리케이션 부팅이 완료되면 백그라운드 스레드에서 자동으로 1회 실행됩니다. 원격 제어(Smart Control) 활성화 여부와
 * {@code setMachineState(stop)} 동작을 실측하기 위한 일회성 코드입니다. 검증이 끝나면 이 클래스를 반드시
 * 삭제하세요.
 *
 * <p>
 * ⚠️ 주의: 이 코드는 부팅할 때마다 실제 기기에 명령을 전송합니다. switch on은 전원만 켜며 실제 세탁 사이클이 물리적으로
 * 시작되지 않을 수 있습니다. 핵심 목적은 명령 전달 및 종료 명령 적용 여부 확인입니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TestDeviceCycleRunner implements ApplicationRunner {

    private static final int TARGET_DEVICE_COUNT = 3;
    private static final long AFTER_POWER_ON_WAIT_MS = 15_000L;
    private static final long AFTER_SHUTDOWN_WAIT_MS = 10_000L;

    private final MachineRepository machineRepository;
    private final DeviceStatusQuerySupport deviceStatusQuerySupport;
    private final SendDeviceCommandService sendDeviceCommandService;
    private final DeviceShutdownSupport deviceShutdownSupport;

    @Override
    public void run(ApplicationArguments args) {
        log.info("🚀 [기기 사이클 테스트] 부팅 완료 감지 - 백그라운드에서 테스트를 시작합니다.");
        CompletableFuture.runAsync(this::runCycleTest);
    }

    public void runCycleTest() {
        var machines = machineRepository.findAll().stream()
                .filter(machine -> machine.getDeviceId() != null && !machine.getDeviceId().isBlank())
                .limit(TARGET_DEVICE_COUNT).toList();

        log.info("════════════════════════════════════════════════════════");
        log.info("🔧 [기기 사이클 테스트] 시작합니다. 대상 기기: {}대", machines.size());
        log.info("════════════════════════════════════════════════════════");

        if (machines.isEmpty()) {
            log.warn("⚠️  [기기 사이클 테스트] deviceId가 등록된 기기가 없어 테스트를 중단합니다.");
            return;
        }

        for (var machine : machines) {
            runSingleDevice(machine);
        }

        log.info("════════════════════════════════════════════════════════");
        log.info("✅ [기기 사이클 테스트] 모든 대상 기기 처리를 완료했습니다.");
        log.info("════════════════════════════════════════════════════════");
    }

    private void runSingleDevice(Machine machine) {
        var name = machine.getName();
        var deviceId = machine.getDeviceId();
        log.info("────────────────────────────────────────────────────────");
        log.info("▶️  [{}] 테스트 시작 (deviceId={}, type={})", name, deviceId, machine.getType());

        try {
            logCurrentState(machine, "1️⃣ 시작 전 상태");

            log.info("⚡ [{}] 전원 ON 명령(switch/on)을 전송합니다...", name);
            sendDeviceCommandService.execute(deviceId, SmartThingsCommandReqDto.powerOn());
            log.info("⚡ [{}] 전원 ON 명령 전송 완료. {}초 대기합니다.", name, AFTER_POWER_ON_WAIT_MS / 1000);
            sleep(AFTER_POWER_ON_WAIT_MS);

            var statusBeforeShutdown = logCurrentState(machine, "2️⃣ 작동 시도 후 상태");

            log.info("🛑 [{}] 안전 종료를 시도합니다 (작동 중이면 setMachineState stop, 유휴면 switch off)...", name);
            var result = deviceShutdownSupport.shutdown(machine, statusBeforeShutdown);
            log.info("🛑 [{}] 안전 종료 결과: {}", name, describeResult(result));
            sleep(AFTER_SHUTDOWN_WAIT_MS);

            logCurrentState(machine, "3️⃣ 종료 후 상태");
            log.info("✔️  [{}] 테스트 완료", name);
        } catch (Exception e) {
            log.error("❌ [{}] 테스트 중 오류 발생: {}", name, e.getMessage(), e);
        }
    }

    private SmartThingsDeviceStatusResDto logCurrentState(Machine machine, String phase) {
        try {
            var status = deviceStatusQuerySupport.queryDeviceStatus(machine.getDeviceId());
            var machineState = machine.isWasher() ? status.getWasherOperatingState() : status.getDryerOperatingState();
            var jobState = machine.isWasher() ? status.getWasherJobState() : status.getDryerJobState();
            log.info("📊 [{}] {} → machineState={}, jobState={}, switch={}, 원격제어={}",
                    machine.getName(),
                    phase,
                    machineState,
                    jobState,
                    status.getSwitchStatus(),
                    status.isRemoteControlEnabled() ? "활성(ON)" : "비활성(OFF)");
            return status;
        } catch (Exception e) {
            log.warn("📊 [{}] {} → 상태 조회 실패: {}", machine.getName(), phase, e.getMessage());
            return null;
        }
    }

    private String describeResult(DeviceShutdownSupport.ShutdownResult result) {
        return switch (result) {
            case STOPPED -> "작동 중 → 안전 정지 명령 전송됨 (STOPPED)";
            case POWERED_OFF -> "유휴 상태 → 전원 차단됨 (POWERED_OFF)";
            case SKIPPED_REMOTE_DISABLED -> "작동 중이나 원격 제어 비활성 → 종료 못함 (SKIPPED_REMOTE_DISABLED)";
            case SKIPPED_UNKNOWN -> "상태 불명 → 종료하지 않음 (SKIPPED_UNKNOWN)";
        };
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
