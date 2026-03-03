package team.washer.server.v2.domain.smartthings.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.smartthings.service.ExchangeSmartThingsTokenService;
import team.washer.server.v2.global.common.response.data.response.CommonApiResDto;
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

    /**
     * SmartThings OAuth 인증 URL 조회
     *
     * @return SmartThings 인증 페이지 URL
     */
    @GetMapping("/authorize")
    @Operation(summary = "SmartThings 인증 URL 조회", description = "SmartThings OAuth 인증을 시작하기 위한 인증 URL을 반환합니다. "
            + "반환된 URL을 브라우저에서 열어 SmartThings 계정으로 인증을 진행하세요.")
    public CommonApiResDto<String> getAuthorizationUrl() {
        var authUrl = smartThingsEnvironment.authorizeUrl() + "?response_type=code" + "&client_id="
                + smartThingsEnvironment.clientId() + "&redirect_uri=" + smartThingsEnvironment.redirectUri()
                + "&scope=" + SCOPE;
        return CommonApiResDto.success("SmartThings 인증 URL 조회에 성공했습니다.", authUrl);
    }

    /**
     * SmartThings OAuth 콜백 처리
     *
     * @param code
     *            SmartThings 인증 완료 후 전달받은 인증 코드
     * @return 처리 결과
     */
    @GetMapping("/callback")
    @Operation(summary = "SmartThings OAuth 콜백 처리", description = "SmartThings 인증 완료 후 리다이렉트되는 콜백 엔드포인트입니다. "
            + "인증 코드를 액세스 토큰으로 교환하고 DB에 저장합니다.")
    public CommonApiResDto<?> handleCallback(
            @Parameter(description = "SmartThings 인증 코드", required = true) @RequestParam String code) {
        exchangeSmartThingsTokenService.execute(code, smartThingsEnvironment.redirectUri());
        return CommonApiResDto.success("SmartThings 토큰 교환에 성공했습니다.");
    }
}
