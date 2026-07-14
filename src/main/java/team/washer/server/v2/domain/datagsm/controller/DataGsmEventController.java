package team.washer.server.v2.domain.datagsm.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import team.themoment.sdk.exception.ExpectedException;
import team.themoment.sdk.response.CommonApiResponse;
import team.washer.server.v2.domain.datagsm.service.HandleDataGsmEventService;
import team.washer.server.v2.domain.datagsm.support.DataGsmEventSignatureVerifier;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/events/datagsm")
@Tag(name = "DataGSM Event", description = "DataGSM 이벤트 수신 API")
public class DataGsmEventController {

    private static final String SIGNATURE_HEADER = "X-DataGSM-Signature";

    private final DataGsmEventSignatureVerifier signatureVerifier;
    private final HandleDataGsmEventService handleDataGsmEventService;

    @PostMapping
    @Operation(summary = "DataGSM 이벤트 수신", description = "DataGSM에서 전달하는 이벤트를 수신하고 서명을 검증한 뒤 처리합니다.")
    public CommonApiResponse receiveEvent(
            @RequestHeader(value = SIGNATURE_HEADER, required = false) final String signature,
            @RequestBody final byte[] rawBody) {
        if (!signatureVerifier.verify(signature, rawBody)) {
            throw new ExpectedException("DataGSM 이벤트 서명이 유효하지 않습니다.", HttpStatus.UNAUTHORIZED);
        }

        handleDataGsmEventService.execute(rawBody);
        return CommonApiResponse.success("DataGSM 이벤트가 처리되었습니다.");
    }
}
