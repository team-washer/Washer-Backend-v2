package team.washer.server.v2.domain.admin.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.admin.dto.request.CreateWashingBanReqDto;
import team.washer.server.v2.domain.admin.entity.WashingBan;
import team.washer.server.v2.domain.admin.repository.WashingBanRepository;
import team.washer.server.v2.domain.admin.service.CreateWashingBanService;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateWashingBanServiceImpl implements CreateWashingBanService {

    private final WashingBanRepository washingBanRepository;

    @Override
    @Transactional
    public void execute(final CreateWashingBanReqDto reqDto) {
        if (washingBanRepository.existsByRoomNumber(reqDto.roomNumber())) {
            throw new ExpectedException("이미 금지된 호실입니다.", HttpStatus.CONFLICT);
        }

        final var ban = WashingBan.builder().roomNumber(reqDto.roomNumber()).build();
        washingBanRepository.save(ban);
        log.info("Created washing ban roomNumber={}", reqDto.roomNumber());
    }
}
