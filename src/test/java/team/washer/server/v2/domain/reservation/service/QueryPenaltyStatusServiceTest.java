package team.washer.server.v2.domain.reservation.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import team.washer.server.v2.domain.reservation.service.impl.QueryPenaltyStatusServiceImpl;
import team.washer.server.v2.domain.reservation.util.PenaltyRedisUtil;

@ExtendWith(MockitoExtension.class)
@DisplayName("QueryPenaltyStatusServiceImpl 클래스의")
class QueryPenaltyStatusServiceTest {

    @InjectMocks
    private QueryPenaltyStatusServiceImpl queryPenaltyStatusService;

    @Mock
    private PenaltyRedisUtil penaltyRedisUtil;

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Nested
        @DisplayName("패널티가 적용 중인 사용자를 조회할 때")
        class Context_with_penalized_user {

            @Test
            @DisplayName("패널티 상태와 남은 분을 반환해야 한다")
            void it_returns_penalty_status_with_remaining_minutes() {
                // Given
                var userId = 1L;
                var expiresAt = LocalDateTime.now().plusMinutes(30);
                given(penaltyRedisUtil.getPenaltyExpiryTime(userId)).willReturn(expiresAt);

                // When
                var result = queryPenaltyStatusService.execute(userId);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.userId()).isEqualTo(userId);
                assertThat(result.isPenalized()).isTrue();
                assertThat(result.penaltyExpiresAt()).isEqualTo(expiresAt);
                assertThat(result.remainingMinutes()).isNotNull();
                assertThat(result.remainingMinutes()).isGreaterThan(0L);
            }
        }

        @Nested
        @DisplayName("패널티가 없는 사용자를 조회할 때")
        class Context_with_no_penalty {

            @Test
            @DisplayName("패널티 없음 상태를 반환해야 한다")
            void it_returns_no_penalty_status() {
                // Given
                var userId = 1L;
                given(penaltyRedisUtil.getPenaltyExpiryTime(userId)).willReturn(null);

                // When
                var result = queryPenaltyStatusService.execute(userId);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.userId()).isEqualTo(userId);
                assertThat(result.isPenalized()).isFalse();
                assertThat(result.penaltyExpiresAt()).isNull();
                assertThat(result.remainingMinutes()).isNull();
            }
        }

        @Nested
        @DisplayName("패널티 만료 시간이 이미 지난 사용자를 조회할 때")
        class Context_with_expired_penalty {

            @Test
            @DisplayName("패널티 없음 상태를 반환해야 한다")
            void it_returns_no_penalty_for_expired() {
                // Given
                var userId = 1L;
                var expiredAt = LocalDateTime.now().minusMinutes(1);
                given(penaltyRedisUtil.getPenaltyExpiryTime(userId)).willReturn(expiredAt);

                // When
                var result = queryPenaltyStatusService.execute(userId);

                // Then
                assertThat(result.isPenalized()).isFalse();
                assertThat(result.remainingMinutes()).isNull();
            }
        }
    }
}
