package team.washer.server.v2.domain.machine.repository.custom.impl;

import java.util.List;

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
    public List<Machine> findAllWithFilters(String name, MachineType type, Integer floor, MachineStatus status) {
        final var machine = QMachine.machine;

        return queryFactory.selectFrom(machine)
                .where(nameContains(name), typeEquals(type), floorEquals(floor), statusEquals(status))
                .orderBy(machine.floor.asc(), machine.type.asc(), machine.number.asc()).fetch();
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
