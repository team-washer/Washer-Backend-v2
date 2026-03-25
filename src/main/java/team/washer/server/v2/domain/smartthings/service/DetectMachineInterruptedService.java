package team.washer.server.v2.domain.smartthings.service;

public interface DetectMachineInterruptedService {
    boolean execute(String deviceId);
}
