package team.washer.server.v2.domain.smartthings.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import team.themoment.sdk.exception.ExpectedException;
import team.themoment.sdk.response.CommonApiResponse;
import team.washer.server.v2.domain.smartthings.service.ExchangeSmartThingsTokenService;
import team.washer.server.v2.global.thirdparty.smartthings.SmartThingsOAuthStateStore;
import team.washer.server.v2.global.thirdparty.smartthings.config.SmartThingsEnvironment;

/**
 * SmartThings OAuth 인증 컨트롤러 (관리자용)
 *
 * <p>
 * SmartThings OAuth 2.0 Authorization Code Flow 진입점 및 콜백을 처리합니다.
 */
@RestController
@RequestMapping("/api/v2/admin/smartthings/oauth")
@RequiredArgsConstructor
@Tag(name = "Admin SmartThings OAuth", description = "SmartThings OAuth 인증 API (관리자용)")
public class AdminSmartThingsOAuthController {

    private static final String SCOPE = "r:devices:* w:devices:*";

    private final SmartThingsEnvironment smartThingsEnvironment;
    private final ExchangeSmartThingsTokenService exchangeSmartThingsTokenService;
    private final SmartThingsOAuthStateStore stateStore;

    /**
     * SmartThings OAuth 인증 URL 조회
     *
     * @return SmartThings 인증 페이지 URL
     */
    @GetMapping("/authorize")
    @Operation(summary = "SmartThings 인증 URL 조회", description = "SmartThings OAuth 인증을 시작하기 위한 인증 URL을 반환합니다. "
            + "반환된 URL을 브라우저에서 열어 SmartThings 계정으로 인증을 진행하세요.")
    public String getAuthorizationUrl() {
        var state = UUID.randomUUID().toString();
        stateStore.save(state);
        return UriComponentsBuilder.fromUriString(smartThingsEnvironment.authorizeUrl())
                .queryParam("response_type", "code").queryParam("client_id", smartThingsEnvironment.clientId())
                .queryParam("redirect_uri", smartThingsEnvironment.redirectUri()).queryParam("scope", SCOPE)
                .queryParam("state", state).toUriString();
    }

    /**
     * SmartThings OAuth 콜백 처리
     *
     * @param code
     *            SmartThings 인증 완료 후 전달받은 인증 코드
     * @param state
     *            CSRF 방지를 위한 state 값
     * @return 처리 결과
     */
    @GetMapping("/callback")
    @Operation(summary = "SmartThings OAuth 콜백 처리", description = "SmartThings 인증 완료 후 리다이렉트되는 콜백 엔드포인트입니다. "
            + "인증 코드를 액세스 토큰으로 교환하고 DB에 저장합니다.")
    public CommonApiResponse handleCallback(
            @Parameter(description = "SmartThings 인증 코드", required = true) @RequestParam String code,
            @Parameter(description = "CSRF 방지 state 값", required = true) @RequestParam String state) {
        if (!stateStore.validateAndRemove(state)) {
            throw new ExpectedException("유효하지 않은 state 값입니다. CSRF 공격이 의심됩니다.", HttpStatus.UNAUTHORIZED);
        }
        exchangeSmartThingsTokenService.execute(code, smartThingsEnvironment.redirectUri());
        return CommonApiResponse.success("SmartThings 토큰 교환에 성공했습니다.");
    }
}
