package team.washer.server.v2.domain.reservation.dto.request;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;

@DisplayName("ApplyUserPenaltyReqDto의")
class ApplyUserPenaltyReqDtoTest {

    private static final int REASON_MAX_LENGTH = 200;

    @Test
    @DisplayName("부과 사유는 200자까지 허용한다")
    void reason_allows_max_length() {
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var validator = validatorFactory.getValidator();
            var request = new ApplyUserPenaltyReqDto("가".repeat(REASON_MAX_LENGTH));

            var violations = validator.validate(request);

            assertThat(violations).isEmpty();
        }
    }

    @Test
    @DisplayName("부과 사유가 200자를 초과하면 검증에 실패한다")
    void reason_rejects_over_max_length() {
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var validator = validatorFactory.getValidator();
            var request = new ApplyUserPenaltyReqDto("가".repeat(REASON_MAX_LENGTH + 1));

            var violations = validator.validate(request);

            assertThat(violations).extracting(violation -> violation.getMessage()).contains("부과 사유는 200자를 초과할 수 없습니다");
        }
    }

    @Test
    @DisplayName("부과 사유가 공백이면 검증에 실패한다")
    void reason_rejects_blank() {
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var validator = validatorFactory.getValidator();
            var request = new ApplyUserPenaltyReqDto("   ");

            var violations = validator.validate(request);

            assertThat(violations).extracting(violation -> violation.getMessage()).contains("부과 사유는 필수입니다");
        }
    }

    @Test
    @DisplayName("부과 사유가 null이면 검증에 실패한다")
    void reason_rejects_null() {
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var validator = validatorFactory.getValidator();
            var request = new ApplyUserPenaltyReqDto(null);

            var violations = validator.validate(request);

            assertThat(violations).extracting(violation -> violation.getMessage()).contains("부과 사유는 필수입니다");
        }
    }
}
