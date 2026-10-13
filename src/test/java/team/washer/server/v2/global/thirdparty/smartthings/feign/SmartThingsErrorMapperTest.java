package team.washer.server.v2.global.thirdparty.smartthings.feign;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;

@DisplayName("SmartThingsErrorMapper 클래스의")
class SmartThingsErrorMapperTest {

    @ParameterizedTest
    @DisplayName("OAuth 응답 상태를 기존 계약에 맞게 변환한다")
    @CsvSource({"399, BAD_GATEWAY", "400, BAD_REQUEST", "408, BAD_GATEWAY", "429, BAD_GATEWAY", "499, BAD_REQUEST",
            "500, BAD_GATEWAY"})
    void it_maps_oauth_status_using_existing_contract(final int externalStatus, final HttpStatus expectedStatus) {
        assertThat(SmartThingsErrorMapper.toOAuthStatus(externalStatus)).isEqualTo(expectedStatus);
    }
}
