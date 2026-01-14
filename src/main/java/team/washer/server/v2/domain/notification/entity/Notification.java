package team.washer.server.v2.domain.notification.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import team.washer.server.v2.global.common.entity.BaseEntity;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.notification.enums.NotificationType;
import team.washer.server.v2.domain.user.entity.User;

@Entity
@Table(name = "notifications", indexes = {@Index(name = "idx_user_id", columnList = "user_id"),
        @Index(name = "idx_is_read", columnList = "is_read"), @Index(name = "idx_type", columnList = "type"),
        @Index(name = "idx_user_read", columnList = "user_id, is_read"),
        @Index(name = "idx_created_at", columnList = "created_at")})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification extends BaseEntity {

    @NotNull(message = "사용자는 필수입니다")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_notification_user"))
    private User user;

    @NotNull(message = "알림 유형은 필수입니다")
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private NotificationType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "machine_id", foreignKey = @ForeignKey(name = "fk_notification_machine"))
    private Machine machine;

    @NotBlank(message = "메시지는 필수입니다")
    @Size(max = 500, message = "메시지는 500자를 초과할 수 없습니다")
    @Column(name = "message", nullable = false, length = 500)
    private String message;

    @NotNull(message = "읽음 여부는 필수입니다")
    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    // Note: createdAt from BaseEntity serves as the notification creation time

    // Business Methods

    /**
     * Factory method to create completion notification
     */
    public static Notification createCompletionNotification(User user, Machine machine) {
        String message = NotificationType.COMPLETION.getMessageTemplate().replace("{machineName}", machine.getName());

        return Notification.builder().user(user).machine(machine).type(NotificationType.COMPLETION).message(message)
                .isRead(false).build();
    }

    /**
     * Factory method to create malfunction notification
     */
    public static Notification createMalfunctionNotification(User user, Machine machine) {
        String message = NotificationType.MALFUNCTION.getMessageTemplate().replace("{machineName}", machine.getName());

        return Notification.builder().user(user).machine(machine).type(NotificationType.MALFUNCTION).message(message)
                .isRead(false).build();
    }

    /**
     * Factory method to create warning notification
     */
    public static Notification createWarningNotification(User user, Machine machine, String reason) {
        String message = NotificationType.WARNING.getMessageTemplate().replace("{machineName}", machine.getName())
                .replace("{reason}", reason);

        return Notification.builder().user(user).machine(machine).type(NotificationType.WARNING).message(message)
                .isRead(false).build();
    }

    /**
     * Mark notification as read
     */
    public void markAsRead() {
        this.isRead = true;
    }

    /**
     * Mark notification as unread
     */
    public void markAsUnread() {
        this.isRead = false;
    }
}
