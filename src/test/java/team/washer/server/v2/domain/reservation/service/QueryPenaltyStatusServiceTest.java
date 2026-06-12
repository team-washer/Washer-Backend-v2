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
        @DisplayName("쿨다운만 있는 사용자를 조회할 때")
        class Context_with_cooldown_only_user {

            @Test
            @DisplayName("isPenalized=true, isRoomBlocked=false를 반환해야 한다")
            void it_returns_penalized_but_not_room_blocked() {
                // Given
                var userId = 1L;
                var expiresAt = LocalDateTime.now().plusMinutes(5);
                given(penaltyRedisUtil.getPenaltyExpiryTime(userId)).willReturn(expiresAt);
                given(penaltyRedisUtil.getBlockExpiryTime(userId)).willReturn(null);

                // When
                var result = queryPenaltyStatusService.execute(userId);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.userId()).isEqualTo(userId);
                assertThat(result.isPenalized()).isTrue();
                assertThat(result.penaltyExpiresAt()).isEqualTo(expiresAt);
                assertThat(result.remainingMinutes()).isGreaterThan(0L);
                assertThat(result.isRoomBlocked()).isFalse();
                assertThat(result.blockExpiresAt()).isNull();
            }
        }

        @Nested
        @DisplayName("호실 차단 중인 사용자를 조회할 때")
        class Context_with_room_blocked_user {

            @Test
            @DisplayName("isPenalized=true, isRoomBlocked=true를 반환해야 한다")
            void it_returns_penalized_and_room_blocked() {
                // Given
                var userId = 1L;
                var blockExpiresAt = LocalDateTime.now().plusHours(40);
                given(penaltyRedisUtil.getPenaltyExpiryTime(userId)).willReturn(blockExpiresAt);
                given(penaltyRedisUtil.getBlockExpiryTime(userId)).willReturn(blockExpiresAt);

                // When
                var result = queryPenaltyStatusService.execute(userId);

                // Then
                assertThat(result.isPenalized()).isTrue();
                assertThat(result.isRoomBlocked()).isTrue();
                assertThat(result.blockExpiresAt()).isEqualTo(blockExpiresAt);
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
                given(penaltyRedisUtil.getBlockExpiryTime(userId)).willReturn(null);

                // When
                var result = queryPenaltyStatusService.execute(userId);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.userId()).isEqualTo(userId);
                assertThat(result.isPenalized()).isFalse();
                assertThat(result.penaltyExpiresAt()).isNull();
                assertThat(result.remainingMinutes()).isNull();
                assertThat(result.isRoomBlocked()).isFalse();
                assertThat(result.blockExpiresAt()).isNull();
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
                given(penaltyRedisUtil.getBlockExpiryTime(userId)).willReturn(null);

                // When
                var result = queryPenaltyStatusService.execute(userId);

                // Then
                assertThat(result.isPenalized()).isFalse();
                assertThat(result.remainingMinutes()).isNull();
                assertThat(result.isRoomBlocked()).isFalse();
            }
        }
    }
}
