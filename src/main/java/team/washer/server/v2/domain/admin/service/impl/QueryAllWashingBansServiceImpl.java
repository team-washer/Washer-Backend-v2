package team.washer.server.v2.domain.admin.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.admin.dto.response.WashingBanResDto;
import team.washer.server.v2.domain.admin.repository.WashingBanRepository;
import team.washer.server.v2.domain.admin.service.QueryAllWashingBansService;

@Service
@RequiredArgsConstructor
public class QueryAllWashingBansServiceImpl implements QueryAllWashingBansService {

    private final WashingBanRepository washingBanRepository;

    @Override
    @Transactional(readOnly = true)
    public List<WashingBanResDto> execute() {
        return washingBanRepository.findAll().stream()
                .map(ban -> new WashingBanResDto(ban.getId(), ban.getRoomNumber(), ban.getCreatedAt())).toList();
    }
}
