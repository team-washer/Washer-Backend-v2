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
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.reservation.entity.QReservation;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.custom.ReservationRepositoryCustom;
import team.washer.server.v2.domain.user.entity.QUser;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.global.util.DateTimeUtil;

@Repository
@RequiredArgsConstructor
public class ReservationRepositoryCustomImpl implements ReservationRepositoryCustom {

    private static final QReservation latestReservation = new QReservation("latestReservation");

    // QUser 기본 별칭(user)은 reservation.user 조인에 이미 사용되므로 대리 예약 생성자용 별칭을 따로 둔다
    private static final QUser createdByUser = new QUser("createdByUser");

    private static final List<ReservationStatus> ACTIVE_STATUSES = List.of(ReservationStatus.RESERVED,
            ReservationStatus.RUNNING);

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
                        reservation.status.in(ReservationStatus.RESERVED, ReservationStatus.RUNNING),
                        reservation.startTime.lt(endTime),
                        reservation.expectedCompletionTime.gt(startTime),
                        excludeReservationId != null ? reservation.id.ne(excludeReservationId) : null)
                .fetchFirst() != null;
    }

    @Override
    public List<Reservation> findCurrentlyActiveByUser(User targetUser) {
        return jpaQueryFactory.selectFrom(reservation).join(reservation.user, user).fetchJoin()
                .join(reservation.machine, machine).fetchJoin()
                .where(reservation.user.eq(targetUser), currentlyActive()).orderBy(reservation.createdAt.desc())
                .fetch();
    }

    @Override
    public List<Reservation> findCurrentlyActiveByMachine(Machine targetMachine) {
        return jpaQueryFactory.selectFrom(reservation).join(reservation.machine, machine).fetchJoin()
                .where(reservation.machine.eq(targetMachine), currentlyActive()).orderBy(reservation.createdAt.desc())
                .fetch();
    }

    @Override
    public List<Reservation> findCurrentlyActiveByRoomNumber(String roomNumber) {
        return jpaQueryFactory.selectFrom(reservation).join(reservation.user, user).fetchJoin()
                .join(reservation.machine, machine).fetchJoin()
                .where(reservation.user.roomNumber.eq(roomNumber), currentlyActive())
                .orderBy(reservation.createdAt.desc()).fetch();
    }

    @Override
    public List<Reservation> findCurrentlyActiveByMachineId(Long machineId) {
        return jpaQueryFactory.selectFrom(reservation).join(reservation.machine, machine).fetchJoin()
                .join(reservation.user, user).fetchJoin().where(reservation.machine.id.eq(machineId), currentlyActive())
                .orderBy(reservation.createdAt.desc()).fetch();
    }

    @Override
    public List<Long> findCurrentlyActiveMachineIds() {
        return jpaQueryFactory.select(reservation.machine.id).distinct().from(reservation).where(currentlyActive())
                .fetch();
    }

    @Override
    public long countCurrentlyActive() {
        final var total = jpaQueryFactory.select(reservation.count()).from(reservation).where(currentlyActive())
                .fetchOne();

        return total != null ? total : 0L;
    }

    @Override
    public boolean existsCurrentlyActiveByUser(User targetUser) {
        return jpaQueryFactory.selectOne().from(reservation).where(reservation.user.eq(targetUser), currentlyActive())
                .fetchFirst() != null;
    }

    @Override
    public boolean existsCurrentlyActiveByMachine(Machine targetMachine) {
        return jpaQueryFactory.selectOne().from(reservation)
                .where(reservation.machine.eq(targetMachine), currentlyActive()).fetchFirst() != null;
    }

    /**
     * 만료되지 않은 활성 예약 조건을 반환합니다. {@link Reservation#isCurrentlyActive()}와 동일한 규칙을 쿼리
     * 조건으로 표현한 것으로, 전체를 로드한 뒤 메모리에서 거르지 않도록 합니다.
     *
     * <p>
     * 타임아웃 유무와 길이는 엔티티와 마찬가지로 {@link ReservationStatus}의 설정에서 파생되므로, 상태별 타임아웃을 바꾸면
     * 양쪽 판정이 함께 따라옵니다.
     *
     * @return 만료되지 않은 활성 예약 조건
     */
    private BooleanExpression currentlyActive() {
        final LocalDateTime now = DateTimeUtil.nowInKorea();

        return ACTIVE_STATUSES.stream().map(status -> notExpired(status, now)).reduce(BooleanExpression::or)
                .orElseThrow();
    }

    /**
     * 특정 상태의 만료되지 않은 예약 조건을 반환합니다. 타임아웃이 없는 상태는 상태 일치만으로 통과시키고, 타임아웃이 있는 상태는 컷오프
     * 이후에 예약된 건만 남깁니다.
     *
     * @param status
     *            판정 대상 예약 상태
     * @param now
     *            컷오프 계산 기준 시각
     * @return 해당 상태의 만료되지 않은 예약 조건
     */
    private BooleanExpression notExpired(final ReservationStatus status, final LocalDateTime now) {
        final BooleanExpression statusMatches = reservation.status.eq(status);

        if (!status.hasTimeout()) {
            return statusMatches;
        }

        return statusMatches.and(reservation.reservedAt.gt(now.minusMinutes(status.getTimeoutMinutes())));
    }

    @Override
    public List<Reservation> findExpiredReservations(ReservationStatus status,
            LocalDateTime threshold,
            LocalDateTime recentCutoff) {

        return jpaQueryFactory.selectFrom(reservation).leftJoin(reservation.machine, machine).fetchJoin()
                .where(reservation.status.eq(status),
                        reservation.createdAt.goe(recentCutoff),
                        reservation.reservedAt.lt(threshold))
                .fetch();
    }

    @Override
    public Page<Reservation> findAllWithFilters(String userName,
            String machineName,
            ReservationStatus status,
            LocalDateTime startDate,
            LocalDateTime endDate,
            MachineType machineType,
            Pageable pageable) {

        final var latestReservationIds = JPAExpressions.select(latestReservation.id.max()).from(latestReservation)
                .where(StringUtils.hasText(userName) ? latestReservation.user.name.contains(userName) : null,
                        StringUtils.hasText(machineName) ? latestReservation.machine.name.contains(machineName) : null,
                        status != null ? latestReservation.status.eq(status) : null,
                        startDate != null ? latestReservation.startTime.goe(startDate) : null,
                        endDate != null ? latestReservation.startTime.loe(endDate) : null,
                        machineType != null ? latestReservation.machine.type.eq(machineType) : null)
                .groupBy(latestReservation.machine.id);

        final var content = jpaQueryFactory.selectFrom(reservation).leftJoin(reservation.user, user).fetchJoin()
                .leftJoin(reservation.machine, machine).fetchJoin().leftJoin(reservation.createdBy, createdByUser)
                .fetchJoin().where(reservation.id.in(latestReservationIds)).orderBy(reservation.createdAt.desc())
                .offset(pageable.getOffset()).limit(pageable.getPageSize()).fetch();

        final var total = jpaQueryFactory.select(reservation.count()).from(reservation)
                .where(reservation.id.in(latestReservationIds)).fetchOne();

        final var count = total != null ? total : 0L;

        return new PageImpl<>(content, pageable, count);
    }

    @Override
    public Page<Reservation> findMachineReservationHistory(Long machineId,
            ReservationStatus status,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable) {

        List<Reservation> results = jpaQueryFactory.selectFrom(reservation).leftJoin(reservation.user, user).fetchJoin()
                .leftJoin(reservation.machine, machine).fetchJoin()
                .where(reservation.machine.id.eq(machineId),
                        status != null ? reservation.status.eq(status) : null,
                        startDate != null ? reservation.startTime.goe(startDate) : null,
                        endDate != null ? reservation.startTime.loe(endDate) : null)
                .orderBy(reservation.createdAt.desc()).offset(pageable.getOffset()).limit(pageable.getPageSize())
                .fetch();

        Long total = jpaQueryFactory.select(reservation.count()).from(reservation)
                .where(reservation.machine.id.eq(machineId),
                        status != null ? reservation.status.eq(status) : null,
                        startDate != null ? reservation.startTime.goe(startDate) : null,
                        endDate != null ? reservation.startTime.loe(endDate) : null)
                .fetchOne();

        long count = total != null ? total : 0L;

        return new PageImpl<>(results, pageable, count);
    }

    @Override
    public List<Reservation> findAllByMachineNameFilter(String machineName) {
        return jpaQueryFactory.selectFrom(reservation).leftJoin(reservation.user, user).fetchJoin()
                .leftJoin(reservation.machine, machine).fetchJoin()
                .where(StringUtils.hasText(machineName) ? reservation.machine.name.contains(machineName) : null)
                .orderBy(reservation.machine.name.asc(), reservation.createdAt.desc()).fetch();
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

    private BooleanExpression machineTypeEquals(MachineType machineType) {
        return machineType != null ? reservation.machine.type.eq(machineType) : null;
    }
}
