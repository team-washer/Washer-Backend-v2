package team.washer.server.v2.domain.smartthings.service;

import java.time.LocalDateTime;
import java.util.Optional;

public interface DetectMachineCompletionService {
    Optional<LocalDateTime> execute(String deviceId);
}
