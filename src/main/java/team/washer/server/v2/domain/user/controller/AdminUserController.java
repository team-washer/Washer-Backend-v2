package team.washer.server.v2.domain.user.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.user.dto.UserListResponseDto;
import team.washer.server.v2.domain.user.dto.UserResponseDto;
import team.washer.server.v2.domain.user.service.UserService;
import team.washer.server.v2.global.common.response.data.response.CommonApiResDto;

@RestController
@RequestMapping("/api/v2/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin User Management", description = "사용자 관리 API (관리자용)")
public class AdminUserController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "전체 사용자 조회", description = "필터링 옵션으로 사용자 목록을 조회합니다")
    public CommonApiResDto<UserListResponseDto> getUsers(
            @Parameter(description = "이름 (부분 검색)") @RequestParam(required = false) String name,
            @Parameter(description = "호실") @RequestParam(required = false) String roomNumber,
            @Parameter(description = "학년") @RequestParam(required = false) Integer grade,
            @Parameter(description = "층") @RequestParam(required = false) Integer floor) {
        UserListResponseDto response = userService.getUsersByFilter(name, roomNumber, grade, floor);
        return CommonApiResDto.success("사용자 목록 조회 성공", response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "사용자 상세 조회", description = "ID로 특정 사용자를 조회합니다")
    public CommonApiResDto<UserResponseDto> getUserById(@Parameter(description = "사용자 ID") @PathVariable Long id) {

        UserResponseDto response = userService.getUserById(id);
        return CommonApiResDto.success("사용자 조회 성공", response);
    }
}
