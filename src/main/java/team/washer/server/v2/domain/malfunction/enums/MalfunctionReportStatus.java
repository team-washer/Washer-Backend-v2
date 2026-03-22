package team.washer.server.v2.domain.malfunction.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MalfunctionReportStatus {
    PENDING("대기"), IN_PROGRESS("처리중"), RESOLVED("처리완료");

    private final String description;
}
