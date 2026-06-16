package team.washer.server.v2.domain.admin.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import team.washer.server.v2.global.common.entity.BaseEntity;

@Entity
@Table(name = "washing_bans", uniqueConstraints = @UniqueConstraint(name = "uk_washing_ban_room_number", columnNames = "room_number"))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class WashingBan extends BaseEntity {

    @NotBlank
    @Pattern(regexp = "^\\d{3,4}$")
    @Column(name = "room_number", nullable = false, length = 4)
    private String roomNumber;
}
