package team.washer.server.v2.domain.datagsm.service;

public interface HandleDataGsmEventService {
    void execute(byte[] rawBody);
}
