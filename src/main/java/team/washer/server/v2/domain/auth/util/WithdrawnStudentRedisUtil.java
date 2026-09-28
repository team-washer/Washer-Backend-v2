package team.washer.server.v2.domain.auth.util;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.auth.entity.redis.WithdrawnStudentEntity;
import team.washer.server.v2.domain.auth.repository.redis.WithdrawnStudentRedisRepository;

@Slf4j
@Component
@RequiredArgsConstructor
public class WithdrawnStudentRedisUtil {

    private static final long THIRTY_DAYS_IN_SECONDS = 30L * 24 * 60 * 60;

    private final WithdrawnStudentRedisRepository withdrawnStudentRedisRepository;

    public void markWithdrawn(final String studentId) {
        withdrawnStudentRedisRepository
                .save(WithdrawnStudentEntity.builder().studentId(studentId).ttl(THIRTY_DAYS_IN_SECONDS).build());
        log.info("withdrawn student record saved");
    }

    public boolean isWithdrawnRecently(final String studentId) {
        return withdrawnStudentRedisRepository.existsById(studentId);
    }

    public void removeWithdrawn(final String studentId) {
        withdrawnStudentRedisRepository.deleteById(studentId);
        log.info("withdrawn student record removed after transaction rollback");
    }
}
