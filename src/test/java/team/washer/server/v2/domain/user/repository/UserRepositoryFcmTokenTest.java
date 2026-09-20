package team.washer.server.v2.domain.user.repository;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.global.config.JpaAuditingConfig;
import team.washer.server.v2.global.config.QueryDslConfig;

@DataJpaTest
@Import({JpaAuditingConfig.class, QueryDslConfig.class})
@TestPropertySource(properties = {"spring.datasource.url=jdbc:h2:mem:fcm_token_test;DB_CLOSE_DELAY=-1",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.docker.compose.enabled=false"})
@DisplayName("UserRepository FCM 토큰 조건부 삭제 쿼리 테스트")
class UserRepositoryFcmTokenTest {

    private static final String OLD_TOKEN = "old-fcm-token";
    private static final String NEW_TOKEN = "new-fcm-token";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Long userId;

    @BeforeEach
    void setUp() {
        final User user = entityManager
                .persist(User.builder().name("김철수").studentId("20210001").roomNumber("301").grade(3).floor(3).build());
        user.updateFcmToken(OLD_TOKEN);
        entityManager.flush();
        userId = user.getId();
        entityManager.clear();
    }

    @Nested
    @DisplayName("clearFcmTokenIfMatches 메서드는")
    class Describe_clearFcmTokenIfMatches {

        @Test
        @DisplayName("저장된 토큰과 일치할 때만 토큰을 삭제해야 한다")
        void it_clears_matching_token() {
            // Given
            final Long versionBeforeDeletion = entityManager.find(User.class, userId).getVersion();

            // When
            final int deletedCount = userRepository.clearFcmTokenIfMatches(userId, OLD_TOKEN);

            // Then
            assertThat(deletedCount).isOne();
            final User user = entityManager.find(User.class, userId);
            assertThat(user.getFcmToken()).isNull();
            assertThat(user.getVersion()).isEqualTo(versionBeforeDeletion + 1);
        }

        @Test
        @DisplayName("새 토큰이 저장된 뒤 이전 토큰으로 삭제하면 새 토큰을 보존해야 한다")
        void it_preserves_new_token_when_stale_cleanup_runs_after_registration() {
            // Given
            final User user = userRepository.findById(userId).orElseThrow();
            user.updateFcmToken(NEW_TOKEN);
            entityManager.flush();
            entityManager.clear();

            // When
            final int deletedCount = userRepository.clearFcmTokenIfMatches(userId, OLD_TOKEN);

            // Then
            assertThat(deletedCount).isZero();
            assertThat(entityManager.find(User.class, userId).getFcmToken()).isEqualTo(NEW_TOKEN);
        }
    }
}
