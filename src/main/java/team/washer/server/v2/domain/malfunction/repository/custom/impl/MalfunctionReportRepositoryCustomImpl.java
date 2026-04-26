package team.washer.server.v2.domain.malfunction.repository.custom.impl;

import static team.washer.server.v2.domain.machine.entity.QMachine.machine;
import static team.washer.server.v2.domain.malfunction.entity.QMalfunctionReport.malfunctionReport;
import static team.washer.server.v2.domain.user.entity.QUser.user;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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
    public Page<MalfunctionReport> findWithDetails(final MalfunctionReportStatus status, final Pageable pageable) {
        final List<MalfunctionReport> content = jpaQueryFactory.selectFrom(malfunctionReport)
                .leftJoin(malfunctionReport.machine, machine).fetchJoin().leftJoin(malfunctionReport.reporter, user)
                .fetchJoin().where(status != null ? malfunctionReport.status.eq(status) : null)
                .orderBy(malfunctionReport.reportedAt.desc()).offset(pageable.getOffset()).limit(pageable.getPageSize())
                .fetch();

        final Long total = jpaQueryFactory.select(malfunctionReport.count()).from(malfunctionReport)
                .where(status != null ? malfunctionReport.status.eq(status) : null).fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }
}
