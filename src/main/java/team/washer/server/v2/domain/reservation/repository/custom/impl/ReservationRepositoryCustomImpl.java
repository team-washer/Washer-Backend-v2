package team.washer.server.v2.domain.reservation.repository.custom.impl;

import static team.washer.server.v2.domain.machine.entity.QMachine.*;
import static team.washer.server.v2.domain.reservation.entity.QReservation.*;
import static team.washer.server.v2.domain.user.entity.QUser.*;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.custom.ReservationRepositoryCustom;

@Repository
@RequiredArgsConstructor
public class ReservationRepositoryCustomImpl implements ReservationRepositoryCustom {

    private final JPAQueryFactory jpaQueryFactory;

    @Override
    public Page<Reservation> findReservationHistory(Long userId,
            ReservationStatus status,
            LocalDateTime startDate,
            LocalDateTime endDate,
            MachineType machineType,
            Pageable pageable) {

        List<Reservation> results = jpaQueryFactory.selectFrom(reservation).leftJoin(reservation.user, user).fetchJoin()
                .leftJoin(reservation.machine, machine).fetchJoin()
                .where(userId != null ? reservation.user.id.eq(userId) : null,
                        status != null ? reservation.status.eq(status) : null,
                        startDate != null ? reservation.startTime.goe(startDate) : null,
                        endDate != null ? reservation.startTime.loe(endDate) : null,
                        machineType != null ? reservation.machine.type.eq(machineType) : null)
                .orderBy(reservation.createdAt.desc()).offset(pageable.getOffset()).limit(pageable.getPageSize())
                .fetch();

        Long total = jpaQueryFactory.select(reservation.count()).from(reservation)
                .where(userId != null ? reservation.user.id.eq(userId) : null,
                        status != null ? reservation.status.eq(status) : null,
                        startDate != null ? reservation.startTime.goe(startDate) : null,
                        endDate != null ? reservation.startTime.loe(endDate) : null,
                        machineType != null ? reservation.machine.type.eq(machineType) : null)
                .fetchOne();

        long count = total != null ? total : 0L;

        return new PageImpl<>(results, pageable, count);
    }

    @Override
    public boolean existsConflictingReservation(Long machineId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Long excludeReservationId) {

        return jpaQueryFactory.selectFrom(reservation)
                .where(reservation.machine.id.eq(machineId),
                        reservation.status
                                .in(ReservationStatus.RESERVED, ReservationStatus.CONFIRMED, ReservationStatus.RUNNING),
                        reservation.startTime.lt(endTime),
                        reservation.expectedCompletionTime.gt(startTime),
                        excludeReservationId != null ? reservation.id.ne(excludeReservationId) : null)
                .fetchFirst() != null;
    }

    @Override
    public List<Reservation> findExpiredReservations(ReservationStatus status,
            LocalDateTime threshold,
            LocalDateTime recentCutoff) {

        return jpaQueryFactory.selectFrom(reservation)
                .where(reservation.status.eq(status),
                        reservation.createdAt.goe(recentCutoff),
                        status == ReservationStatus.RESERVED ? reservation.startTime.lt(threshold) : null,
                        status == ReservationStatus.CONFIRMED ? reservation.confirmedAt.lt(threshold) : null)
                .fetch();
    }

    @Override
    public Page<Reservation> findAllWithFilters(String userName,
            String machineName,
            ReservationStatus status,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable) {

        final var content = jpaQueryFactory.selectFrom(reservation).leftJoin(reservation.user, user).fetchJoin()
                .leftJoin(reservation.machine, machine).fetchJoin()
                .where(userNameContains(userName),
                        machineNameContains(machineName),
                        statusEquals(status),
                        startTimeAfter(startDate),
                        startTimeBefore(endDate))
                .orderBy(reservation.createdAt.desc()).offset(pageable.getOffset()).limit(pageable.getPageSize())
                .fetch();

        final var total = jpaQueryFactory.select(reservation.count()).from(reservation)
                .where(userNameContains(userName),
                        machineNameContains(machineName),
                        statusEquals(status),
                        startTimeAfter(startDate),
                        startTimeBefore(endDate))
                .fetchOne();

        final var count = total != null ? total : 0L;

        return new PageImpl<>(content, pageable, count);
    }

    private BooleanExpression userNameContains(String userName) {
        return StringUtils.hasText(userName) ? reservation.user.name.contains(userName) : null;
    }

    private BooleanExpression machineNameContains(String machineName) {
        return StringUtils.hasText(machineName) ? reservation.machine.name.contains(machineName) : null;
    }

    private BooleanExpression statusEquals(ReservationStatus status) {
        return status != null ? reservation.status.eq(status) : null;
    }

    private BooleanExpression startTimeAfter(LocalDateTime startDate) {
        return startDate != null ? reservation.startTime.goe(startDate) : null;
    }

    private BooleanExpression startTimeBefore(LocalDateTime endDate) {
        return endDate != null ? reservation.startTime.loe(endDate) : null;
    }
}
