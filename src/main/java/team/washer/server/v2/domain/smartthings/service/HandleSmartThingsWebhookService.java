package team.washer.server.v2.domain.smartthings.service;

public interface HandleSmartThingsWebhookService {
    void execute(byte[] rawBody);
}
