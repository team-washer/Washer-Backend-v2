package team.washer.server.v2.domain.admin.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.admin.repository.WashingBanRepository;
import team.washer.server.v2.domain.admin.service.DeleteWashingBanService;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeleteWashingBanServiceImpl implements DeleteWashingBanService {

    private final WashingBanRepository washingBanRepository;

    @Override
    @Transactional
    public void execute(final String roomNumber) {
        final var ban = washingBanRepository.findByRoomNumber(roomNumber)
                .orElseThrow(() -> new ExpectedException("금지된 호실이 아닙니다.", HttpStatus.NOT_FOUND));

        washingBanRepository.delete(ban);
        log.info("Deleted washing ban roomNumber={}", roomNumber);
    }
}
