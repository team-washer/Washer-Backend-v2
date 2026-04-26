package team.washer.server.v2.domain.user.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import team.themoment.sdk.response.CommonApiResponse;
import team.washer.server.v2.domain.user.dto.response.MyInfoResDto;
import team.washer.server.v2.domain.user.service.QueryMyInfoService;
import team.washer.server.v2.domain.user.service.WithdrawUserService;

@RestController
@RequestMapping("/api/v2/users")
@RequiredArgsConstructor
@Tag(name = "User", description = "사용자 API")
public class UserController {

    private final QueryMyInfoService queryMyInfoService;
    private final WithdrawUserService withdrawUserService;

    @GetMapping("/my")
    @Operation(summary = "내 정보 조회", description = "현재 로그인된 사용자의 정보와 예약 가능 여부를 조회합니다.")
    public MyInfoResDto getMyInfo() {
        return queryMyInfoService.execute();
    }

    @DeleteMapping("/me")
    @Operation(summary = "회원탈퇴", description = "현재 로그인된 사용자의 계정을 삭제합니다. 활성 예약은 자동으로 취소되며, 탈퇴 후 30일간 재가입이 제한됩니다.")
    public CommonApiResponse withdrawUser() {
        withdrawUserService.execute();
        return CommonApiResponse.success("회원탈퇴가 완료되었습니다.");
    }
}
