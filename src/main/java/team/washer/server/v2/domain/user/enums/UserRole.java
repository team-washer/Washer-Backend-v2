package team.washer.server.v2.domain.user.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum UserRole {
    USER("User", "일반 사용자"),
    DORMITORY_COUNCIL("DormitoryCouncil", "기숙사자치위원회"),
    ADMIN("Admin", "관리자");

    private final String code;
    private final String description;

    public boolean canBypassTimeRestrictions() {
        return this == DORMITORY_COUNCIL || this == ADMIN;
    }

    public boolean canManageSundayReservation() {
        return this == DORMITORY_COUNCIL || this == ADMIN;
    }

    public boolean isAdmin() {
        return this == ADMIN;
    }
}
