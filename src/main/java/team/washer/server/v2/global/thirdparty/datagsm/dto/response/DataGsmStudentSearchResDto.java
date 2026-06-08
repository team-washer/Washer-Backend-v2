package team.washer.server.v2.global.thirdparty.datagsm.dto.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DataGSM OpenAPI 학생 목록 조회 응답 DTO
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DataGsmStudentSearchResDto(@JsonProperty("status") String status, @JsonProperty("code") Integer code,
        @JsonProperty("message") String message, @JsonProperty("data") Data data) {

    /**
     * 페이지네이션 메타데이터와 학생 목록을 담는 데이터 항목
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(@JsonProperty("totalPages") Integer totalPages,
            @JsonProperty("totalElements") Long totalElements, @JsonProperty("students") List<Student> students) {
    }

    /**
     * 승격 판단에 필요한 학생 정보(학번, 역할)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Student(@JsonProperty("studentNumber") Integer studentNumber, @JsonProperty("role") String role) {
    }
}
