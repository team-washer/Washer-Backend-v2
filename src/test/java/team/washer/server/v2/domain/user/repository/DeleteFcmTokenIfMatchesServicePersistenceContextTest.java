package team.washer.server.v2.domain.user.repository;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import team.washer.server.v2.domain.notification.service.DeleteFcmTokenIfMatchesService;
import team.washer.server.v2.domain.notification.service.impl.DeleteFcmTokenIfMatchesServiceImpl;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.global.config.JpaAuditingConfig;
import team.washer.server.v2.global.config.QueryDslConfig;

@DataJpaTest
@Import({JpaAuditingConfig.class, QueryDslConfig.class, DeleteFcmTokenIfMatchesServiceImpl.class})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:fcm_token_persistence_context_test;DB_CLOSE_DELAY=-1",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.docker.compose.enabled=false"})
@DisplayName("FCM 토큰 정리와 영속성 컨텍스트 동기화 테스트")
class DeleteFcmTokenIfMatchesServicePersistenceContextTest {

    private static final String OLD_TOKEN = "old-fcm-token";

    @Autowired
    private DeleteFcmTokenIfMatchesService deleteFcmTokenIfMatchesService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Long userId;

    @BeforeEach
    void setUp() {
        final User user = User.builder().name("테스트 사용자").studentId("20210001").roomNumber("301").grade(3).floor(3)
                .build();
        user.updateFcmToken(OLD_TOKEN);
        entityManager.persist(user);
        entityManager.flush();
        userId = user.getId();
        entityManager.clear();
    }

    @Test
    @DisplayName("바깥 영속성 컨텍스트의 변경이 있어도 오래된 토큰이 다시 저장되지 않아야 한다")
    void it_does_not_restore_stale_token_from_outer_persistence_context() {
        // Given
        final User user = userRepository.findById(userId).orElseThrow();
        user.updateInfo("302", null, null);

        // When
        deleteFcmTokenIfMatchesService.execute(userId, OLD_TOKEN);
        entityManager.flush();
        entityManager.clear();

        // Then
        final User reloadedUser = userRepository.findById(userId).orElseThrow();
        assertThat(reloadedUser.getFcmToken()).isNull();
        assertThat(reloadedUser.getRoomNumber()).isEqualTo("302");
    }
}
