package team.washer.server.v2.domain.smartthings.enums;

/**
 * SmartThings webhook으로 수신한 기기 event의 처리 상태
 */
public enum SmartThingsDeviceEventStatus {
    /** 수신해 저장했고 아직 lifecycle 판정에 반영하지 않은 상태 */
    RECEIVED,
    /** lifecycle 판정에 반영한 상태 */
    PROCESSED
}
