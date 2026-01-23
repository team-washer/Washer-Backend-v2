package team.washer.server.v2.domain.malfunction.repository.custom.impl;

import static team.washer.server.v2.domain.machine.entity.QMachine.machine;
import static team.washer.server.v2.domain.malfunction.entity.QMalfunctionReport.malfunctionReport;
import static team.washer.server.v2.domain.user.entity.QUser.user;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.malfunction.entity.MalfunctionReport;
import team.washer.server.v2.domain.malfunction.enums.MalfunctionReportStatus;
import team.washer.server.v2.domain.malfunction.repository.custom.MalfunctionReportRepositoryCustom;

@Repository
@RequiredArgsConstructor
public class MalfunctionReportRepositoryCustomImpl implements MalfunctionReportRepositoryCustom {

    private final JPAQueryFactory jpaQueryFactory;

    @Override
    public List<MalfunctionReport> findWithDetails(final MalfunctionReportStatus status) {
        return jpaQueryFactory.selectFrom(malfunctionReport).leftJoin(malfunctionReport.machine, machine).fetchJoin()
                .leftJoin(malfunctionReport.reporter, user).fetchJoin()
                .where(status != null ? malfunctionReport.status.eq(status) : null).fetch();
    }
}
