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

    /**
     * 탈퇴한 학번을 30일간 Redis에 기록합니다.
     */
    public void markWithdrawn(final String studentId) {
        try {
            withdrawnStudentRedisRepository
                    .save(WithdrawnStudentEntity.builder().studentId(studentId).ttl(THIRTY_DAYS_IN_SECONDS).build());
            log.info("withdrawn student recorded studentId={}", studentId);
        } catch (Exception e) {
            log.error("failed to record withdrawn student studentId={}", studentId, e);
        }
    }

    /**
     * 해당 학번이 탈퇴 후 30일 이내인지 여부를 반환합니다.
     */
    public boolean isWithdrawnRecently(final String studentId) {
        try {
            return withdrawnStudentRedisRepository.existsById(studentId);
        } catch (Exception e) {
            log.warn("failed to check withdrawn student studentId={}", studentId, e);
            return false;
        }
    }
}
