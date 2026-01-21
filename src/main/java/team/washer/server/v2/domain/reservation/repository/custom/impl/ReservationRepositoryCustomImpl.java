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

        long total = jpaQueryFactory.selectFrom(reservation)
                .where(userId != null ? reservation.user.id.eq(userId) : null,
                        status != null ? reservation.status.eq(status) : null,
                        startDate != null ? reservation.startTime.goe(startDate) : null,
                        endDate != null ? reservation.startTime.loe(endDate) : null,
                        machineType != null ? reservation.machine.type.eq(machineType) : null)
                .fetchCount();

        return new PageImpl<>(results, pageable, total);
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
}
