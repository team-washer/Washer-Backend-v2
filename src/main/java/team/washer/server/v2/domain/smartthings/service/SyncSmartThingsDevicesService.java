package team.washer.server.v2.domain.smartthings.service;

/**
 * SmartThings 기기 목록을 조회하여 Machine DB와 동기화하는 서비스
 */
public interface SyncSmartThingsDevicesService {

    void execute();
}
