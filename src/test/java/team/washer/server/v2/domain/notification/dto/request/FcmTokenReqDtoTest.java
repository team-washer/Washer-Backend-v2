package team.washer.server.v2.domain.notification.dto.request;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import team.washer.server.v2.global.common.constants.NotificationConstants;

@DisplayName("FcmTokenReqDto의")
class FcmTokenReqDtoTest {

    @Test
    @DisplayName("FCM 토큰은 4096자까지 허용한다")
    void token_allows_max_length() {
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var validator = validatorFactory.getValidator();
            var request = new FcmTokenReqDto("a".repeat(NotificationConstants.FCM_TOKEN_MAX_LENGTH));

            var violations = validator.validate(request);

            assertThat(violations).isEmpty();
        }
    }

    @Test
    @DisplayName("FCM 토큰이 4096자를 초과하면 검증에 실패한다")
    void token_rejects_over_max_length() {
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var validator = validatorFactory.getValidator();
            var request = new FcmTokenReqDto("a".repeat(NotificationConstants.FCM_TOKEN_MAX_LENGTH + 1));

            var violations = validator.validate(request);

            assertThat(violations).extracting(violation -> violation.getMessage())
                    .contains("FCM 토큰은 4096자를 초과할 수 없습니다");
        }
    }
}
