package team.washer.server.v2.domain.auth.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import team.washer.server.v2.domain.auth.dto.request.RefreshTokenReqDto;
import team.washer.server.v2.domain.auth.dto.request.TokenReqDto;
import team.washer.server.v2.domain.auth.dto.response.TokenResDto;
import team.washer.server.v2.domain.auth.service.RefreshTokenService;
import team.washer.server.v2.domain.auth.service.SignInService;
import team.washer.server.v2.global.common.response.data.response.CommonApiResDto;

@RestController
@AllArgsConstructor
@RequestMapping("/api/v2/auth")
@Tag(name = "Auth", description = "인증 API")
@Validated
public class AuthController {
    private final SignInService signInService;
    private final RefreshTokenService refreshTokenService;

    @PostMapping("/login")
    @Operation(summary = "로그인", description = "DataGSM OAuth 인증 코드로 로그인합니다.")
    public CommonApiResDto<TokenResDto> login(@Valid @RequestBody final TokenReqDto reqDto) {
        final var result = signInService.execute(reqDto);
        return CommonApiResDto.success("로그인에 성공했습니다.", result);
    }

    @PostMapping("/refresh")
    @Operation(summary = "토큰 갱신", description = "Refresh Token으로 새로운 Access Token을 발급받습니다.")
    public CommonApiResDto<TokenResDto> refresh(@Valid @RequestBody final RefreshTokenReqDto reqDto) {
        final var result = refreshTokenService.execute(reqDto);
        return CommonApiResDto.success("토큰 갱신에 성공했습니다.", result);
    }
}
