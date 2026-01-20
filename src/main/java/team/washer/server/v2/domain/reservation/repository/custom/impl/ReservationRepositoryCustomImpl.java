package team.washer.server.v2.domain.reservation.repository.custom.impl;

import static team.washer.server.v2.domain.reservation.entity.QReservation.*;
import static team.washer.server.v2.domain.user.entity.QUser.*;
import static team.washer.server.v2.domain.machine.entity.QMachine.*;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.custom.ReservationRepositoryCustom;

@Repository
@RequiredArgsConstructor
public class ReservationRepositoryCustomImpl implements ReservationRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Reservation> findReservationHistory(Long userId, ReservationStatus status, LocalDateTime startDate,
            LocalDateTime endDate, MachineType machineType, Pageable pageable) {

        BooleanBuilder builder = new BooleanBuilder();

        if (userId != null) {
            builder.and(reservation.user.id.eq(userId));
        }
        if (status != null) {
            builder.and(reservation.status.eq(status));
        }
        if (startDate != null) {
            builder.and(reservation.startTime.goe(startDate));
        }
        if (endDate != null) {
            builder.and(reservation.startTime.loe(endDate));
        }
        if (machineType != null) {
            builder.and(reservation.machine.type.eq(machineType));
        }

        List<Reservation> results = queryFactory.selectFrom(reservation)
                .leftJoin(reservation.user, user)
                .fetchJoin()
                .leftJoin(reservation.machine, machine)
                .fetchJoin()
                .where(builder)
                .orderBy(reservation.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        long total = queryFactory.selectFrom(reservation).where(builder).fetchCount();

        return new PageImpl<>(results, pageable, total);
    }

    @Override
    public boolean existsConflictingReservation(Long machineId, LocalDateTime startTime, LocalDateTime endTime,
            Long excludeReservationId) {

        BooleanBuilder builder = new BooleanBuilder();
        builder.and(reservation.machine.id.eq(machineId));
        builder.and(reservation.status.in(ReservationStatus.RESERVED, ReservationStatus.CONFIRMED,
                ReservationStatus.RUNNING));

        // 시간 겹침 체크
        builder.and(reservation.startTime.lt(endTime).and(reservation.expectedCompletionTime.gt(startTime)));

        if (excludeReservationId != null) {
            builder.and(reservation.id.ne(excludeReservationId));
        }

        return queryFactory.selectFrom(reservation).where(builder).fetchFirst() != null;
    }

    @Override
    public List<Reservation> findExpiredReservations(ReservationStatus status, LocalDateTime threshold,
            LocalDateTime recentCutoff) {

        BooleanBuilder builder = new BooleanBuilder();
        builder.and(reservation.status.eq(status));
        builder.and(reservation.createdAt.goe(recentCutoff)); // 성능 최적화

        if (status == ReservationStatus.RESERVED) {
            builder.and(reservation.startTime.lt(threshold));
        } else if (status == ReservationStatus.CONFIRMED) {
            builder.and(reservation.confirmedAt.lt(threshold));
        }

        return queryFactory.selectFrom(reservation).where(builder).fetch();
    }
}
