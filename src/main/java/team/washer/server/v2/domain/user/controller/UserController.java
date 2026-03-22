package team.washer.server.v2.domain.user.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.user.dto.response.MyInfoResDto;
import team.washer.server.v2.domain.user.service.QueryMyInfoService;

@RestController
@RequestMapping("/api/v2/users")
@RequiredArgsConstructor
@Tag(name = "User", description = "사용자 API")
public class UserController {

    private final QueryMyInfoService queryMyInfoService;

    @GetMapping("/my")
    @Operation(summary = "내 정보 조회", description = "현재 로그인된 사용자의 정보와 예약 가능 여부를 조회합니다.")
    public MyInfoResDto getMyInfo() {
        return queryMyInfoService.execute();
    }
}
