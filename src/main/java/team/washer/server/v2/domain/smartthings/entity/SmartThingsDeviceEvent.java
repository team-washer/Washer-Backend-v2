package team.washer.server.v2.domain.smartthings.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;
import team.washer.server.v2.domain.smartthings.enums.SmartThingsDeviceEventStatus;
import team.washer.server.v2.global.common.entity.BaseEntity;

/**
 * SmartThings webhook으로 수신한 기기 attribute event inbox
 *
 * <p>
 * webhook 요청 스레드는 검증 후 이 테이블에 저장만 하고 바로 응답한다. 원본 payload는 저장하지 않고 lifecycle 판정에
 * 필요한 필드만 남긴다. {@code event_id} unique 제약으로 재전송과 다중 인스턴스 중복 수신을 제거한다.
 * </p>
 */
@Entity
@Table(name = "smartthings_device_events", indexes = {
        @Index(name = "idx_device_attribute_event_time", columnList = "device_id, capability, attribute, event_time"),
        @Index(name = "idx_status_event_time", columnList = "status, event_time"),
        @Index(name = "idx_created_at", columnList = "created_at")}, uniqueConstraints = {
                @UniqueConstraint(name = "uk_event_id", columnNames = "event_id")})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SmartThingsDeviceEvent extends BaseEntity {

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;

    @Column(name = "component_id", nullable = false, length = 64)
    private String componentId;

    @Column(name = "capability", nullable = false, length = 64)
    private String capability;

    @Column(name = "attribute", nullable = false, length = 64)
    private String attribute;

    @Column(name = "event_value", length = 64)
    private String value;

    @Column(name = "event_time", nullable = false)
    private LocalDateTime eventTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private SmartThingsDeviceEventStatus status = SmartThingsDeviceEventStatus.RECEIVED;
}
