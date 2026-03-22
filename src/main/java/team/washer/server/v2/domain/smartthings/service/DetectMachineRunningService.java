package team.washer.server.v2.domain.smartthings.service;

public interface DetectMachineRunningService {
    boolean execute(String deviceId);
}
