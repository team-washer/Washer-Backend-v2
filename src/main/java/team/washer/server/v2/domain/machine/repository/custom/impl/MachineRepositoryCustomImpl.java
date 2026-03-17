package team.washer.server.v2.domain.machine.repository.custom.impl;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.util.StringUtils;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.entity.QMachine;
import team.washer.server.v2.domain.machine.enums.MachineStatus;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.machine.repository.custom.MachineRepositoryCustom;

@RequiredArgsConstructor
public class MachineRepositoryCustomImpl implements MachineRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Machine> findAllWithFilters(String name,
            MachineType type,
            Integer floor,
            MachineStatus status,
            boolean sorted,
            Pageable pageable) {
        final var machine = QMachine.machine;

        final var predicates = new BooleanExpression[]{nameContains(name), typeEquals(type), floorEquals(floor),
                statusEquals(status)};

        final var query = queryFactory.selectFrom(machine).where(predicates);

        if (sorted) {
            query.orderBy(machine.floor.asc(), machine.type.desc(), machine.position.asc(), machine.number.asc());
        }

        final var content = query.offset(pageable.getOffset()).limit(pageable.getPageSize()).fetch();

        final var total = queryFactory.select(machine.count()).from(machine).where(predicates).fetchOne();

        final var count = total != null ? total : 0L;

        return new PageImpl<>(content, pageable, count);
    }

    private BooleanExpression nameContains(String name) {
        return StringUtils.hasText(name) ? QMachine.machine.name.contains(name) : null;
    }

    private BooleanExpression typeEquals(MachineType type) {
        return type != null ? QMachine.machine.type.eq(type) : null;
    }

    private BooleanExpression floorEquals(Integer floor) {
        return floor != null ? QMachine.machine.floor.eq(floor) : null;
    }

    private BooleanExpression statusEquals(MachineStatus status) {
        return status != null ? QMachine.machine.status.eq(status) : null;
    }
}
