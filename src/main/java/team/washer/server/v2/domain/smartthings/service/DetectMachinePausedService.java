package team.washer.server.v2.domain.smartthings.service;

public interface DetectMachinePausedService {
    boolean execute(String deviceId);
}
