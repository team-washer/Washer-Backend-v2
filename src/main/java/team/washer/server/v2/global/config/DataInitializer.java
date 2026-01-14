package team.washer.server.v2.global.config;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.MachineAvailability;
import team.washer.server.v2.domain.machine.enums.MachineStatus;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.machine.enums.Position;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.smartthings.entity.SmartThingsToken;
import team.washer.server.v2.domain.smartthings.repository.SmartThingsTokenRepository;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;

@Slf4j
@Configuration
@RequiredArgsConstructor
@Profile("!prod")
public class DataInitializer {

    private final UserRepository userRepository;
    private final MachineRepository machineRepository;
    private final SmartThingsTokenRepository tokenRepository;

    @Bean
    @Transactional
    public ApplicationRunner initializeData() {
        return args -> {
            log.info("Starting data initialization...");

            initializeSmartThingsToken();
            initializeTestUsers();
            initializeTestMachines();

            log.info("Data initialization completed successfully!");
        };
    }

    private void initializeSmartThingsToken() {
        if (!tokenRepository.existsById(SmartThingsToken.SINGLETON_ID)) {
            SmartThingsToken token = SmartThingsToken.builder().accessToken("PLACEHOLDER_ACCESS_TOKEN")
                    .refreshToken("PLACEHOLDER_REFRESH_TOKEN").expiresAt(LocalDateTime.now().plusHours(1)).build();

            // Note: ID is explicitly set to SINGLETON_ID (1L) via @Builder.Default
            tokenRepository.save(token);
            log.info("SmartThingsToken singleton created (ID: {})", token.getId());
        } else {
            log.info("SmartThingsToken singleton already exists");
        }
    }

    private void initializeTestUsers() {
        if (userRepository.count() == 0) {
            User user1 = User.builder().name("김철수").studentId("20210001").roomNumber("301").grade(3).floor(3)
                    .penaltyCount(0).build();

            User user2 = User.builder().name("이영희").studentId("20210002").roomNumber("302").grade(3).floor(3)
                    .penaltyCount(0).build();

            User user3 = User.builder().name("박민수").studentId("20220001").roomNumber("401").grade(2).floor(4)
                    .penaltyCount(0).build();

            User user4 = User.builder().name("정수현").studentId("20220002").roomNumber("402").grade(2).floor(4)
                    .penaltyCount(0).build();

            User user5 = User.builder().name("최지원").studentId("20230001").roomNumber("501").grade(1).floor(5)
                    .penaltyCount(0).build();

            userRepository.saveAll(List.of(user1, user2, user3, user4, user5));

            log.info("Created {} test users", 5);
        } else {
            log.info("Test users already exist ({} users)", userRepository.count());
        }
    }

    private void initializeTestMachines() {
        if (machineRepository.count() == 0) {
            // 3F Machines (Left side)
            Machine washer3FL1 = createMachine(MachineType.WASHER, 3, Position.LEFT, 1);
            Machine washer3FL2 = createMachine(MachineType.WASHER, 3, Position.LEFT, 2);

            // 3F Machines (Right side)
            Machine dryer3FR1 = createMachine(MachineType.DRYER, 3, Position.RIGHT, 1);
            Machine dryer3FR2 = createMachine(MachineType.DRYER, 3, Position.RIGHT, 2);

            // 4F Machines (Left side)
            Machine washer4FL1 = createMachine(MachineType.WASHER, 4, Position.LEFT, 1);
            Machine dryer4FL2 = createMachine(MachineType.DRYER, 4, Position.LEFT, 2);

            // 4F Machines (Right side)
            Machine washer4FR1 = createMachine(MachineType.WASHER, 4, Position.RIGHT, 1);
            Machine dryer4FR2 = createMachine(MachineType.DRYER, 4, Position.RIGHT, 2);

            machineRepository.saveAll(List.of(washer3FL1, washer3FL2, dryer3FR1, dryer3FR2, washer4FL1, dryer4FL2,
                    washer4FR1, dryer4FR2));

            log.info("Created {} test machines", 8);
        } else {
            log.info("Test machines already exist ({} machines)", machineRepository.count());
        }
    }

    private Machine createMachine(MachineType type, Integer floor, Position position, Integer number) {
        String name = Machine.generateName(type, floor, position, number);
        String deviceId = String.format("smartthings-device-id-%s-%d%s%d", type.getCode().toLowerCase(), floor,
                position.getCode().toLowerCase(), number);

        Machine machine = Machine.builder().name(name).type(type).deviceId(deviceId).floor(floor).position(position)
                .number(number).status(MachineStatus.NORMAL).availability(MachineAvailability.AVAILABLE).build();

        log.info("Created machine: {} (deviceId: {})", name, deviceId);
        return machine;
    }
}
