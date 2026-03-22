package team.washer.server.v2.domain.smartthings.service;

public interface ExchangeSmartThingsTokenService {
    void execute(String code, String redirectUri);
}
