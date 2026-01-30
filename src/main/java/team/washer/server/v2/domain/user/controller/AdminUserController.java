package team.washer.server.v2.domain.user.controller;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.user.dto.request.UpdateUserReqDto;
import team.washer.server.v2.domain.user.dto.response.UserListResDto;
import team.washer.server.v2.domain.user.dto.response.UserResDto;
import team.washer.server.v2.domain.user.dto.response.UserUpdateResDto;
import team.washer.server.v2.domain.user.service.DeleteUserService;
import team.washer.server.v2.domain.user.service.QueryUserByIdService;
import team.washer.server.v2.domain.user.service.SearchUserService;
import team.washer.server.v2.domain.user.service.UpdateUserInfoService;

@RestController
@RequestMapping("/api/v2/admin/users")
@RequiredArgsConstructor
@Validated
@Tag(name = "Admin User Management", description = "사용자 관리 API (관리자용)")
public class AdminUserController {

    private final SearchUserService searchUserService;
    private final QueryUserByIdService queryUserByIdService;
    private final UpdateUserInfoService updateUserInfoService;
    private final DeleteUserService deleteUserService;

    @GetMapping
    @Operation(summary = "전체 사용자 조회", description = "필터링 옵션으로 사용자 목록을 조회합니다")
    public UserListResDto getUsers(@Parameter(description = "이름 (부분 검색)") @RequestParam(required = false) String name,
            @Parameter(description = "호실") @RequestParam(required = false) String roomNumber,
            @Parameter(description = "학년") @RequestParam(required = false) Integer grade,
            @Parameter(description = "층") @RequestParam(required = false) Integer floor) {
        return searchUserService.getUsersByFilter(name, roomNumber, grade, floor);
    }

    @GetMapping("/{id}")
    @Operation(summary = "사용자 상세 조회", description = "ID로 특정 사용자를 조회합니다")
    public UserResDto getUserById(@Parameter(description = "사용자 ID") @PathVariable @NotNull Long id) {
        return queryUserByIdService.getUserById(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "사용자 정보 수정", description = "사용자의 호실, 학년, 층 정보를 수정합니다")
    public UserUpdateResDto updateUser(@Parameter(description = "사용자 ID") @PathVariable @NotNull Long id,
            @Valid @RequestBody UpdateUserReqDto request) {
        return updateUserInfoService.execute(id, request.roomNumber(), request.grade(), request.floor());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "사용자 삭제", description = "사용자를 삭제합니다 (활성 예약이 없는 경우에만 가능)")
    public void deleteUser(@Parameter(description = "사용자 ID") @PathVariable @NotNull Long id) {
        deleteUserService.execute(id);
    }
}
