package team.washer.server.v2.domain.admin.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import team.themoment.sdk.response.CommonApiResponse;
import team.washer.server.v2.domain.admin.dto.request.CreateWashingBanReqDto;
import team.washer.server.v2.domain.admin.dto.response.WashingBanResDto;
import team.washer.server.v2.domain.admin.service.CreateWashingBanService;
import team.washer.server.v2.domain.admin.service.DeleteWashingBanService;
import team.washer.server.v2.domain.admin.service.QueryAllWashingBansService;

@RestController
@RequestMapping("/api/v2/admin/washing-bans")
@RequiredArgsConstructor
@Tag(name = "Admin Washing Ban", description = "관리자 세탁 강제 금지 API")
public class AdminWashingBanController {

    private final CreateWashingBanService createWashingBanService;
    private final DeleteWashingBanService deleteWashingBanService;
    private final QueryAllWashingBansService queryAllWashingBansService;

    @PostMapping
    @Operation(summary = "호실 세탁 금지 등록", description = "특정 호실의 세탁기/건조기 신규 예약을 무기한 금지합니다.")
    public CommonApiResponse createWashingBan(@RequestBody @Valid final CreateWashingBanReqDto reqDto) {
        createWashingBanService.execute(reqDto);
        return CommonApiResponse.success("호실 세탁 금지가 등록되었습니다.");
    }

    @DeleteMapping("/{roomNumber}")
    @Operation(summary = "호실 세탁 금지 해제", description = "특정 호실의 세탁 금지를 해제합니다.")
    public CommonApiResponse deleteWashingBan(@PathVariable final String roomNumber) {
        deleteWashingBanService.execute(roomNumber);
        return CommonApiResponse.success("호실 세탁 금지가 해제되었습니다.");
    }

    @GetMapping
    @Operation(summary = "금지 호실 목록 조회", description = "현재 세탁이 금지된 전체 호실 목록을 조회합니다.")
    public List<WashingBanResDto> queryAllWashingBans() {
        return queryAllWashingBansService.execute();
    }
}
