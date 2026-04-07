package team.washer.server.v2.domain.user.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.reservation.util.PenaltyRedisUtil;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.domain.user.service.impl.QueryMyInfoServiceImpl;
import team.washer.server.v2.global.security.provider.CurrentUserProvider;

@ExtendWith(MockitoExtension.class)
@DisplayName("QueryMyInfoServiceImpl 클래스의")
class QueryMyInfoServiceTest {

    @InjectMocks
    private QueryMyInfoServiceImpl queryMyInfoService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PenaltyRedisUtil penaltyRedisUtil;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private User createUser() {
        return User.builder().name("김철수").studentId("20210001").roomNumber("301").grade(3).floor(3)
                .penaltyCount(0).build();
    }

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Nested
        @DisplayName("패널티가 없는 사용자가 내 정보를 조회할 때")
        class Context_with_no_penalty_user {

            @Test
            @DisplayName("예약 가능 상태로 내 정보를 반환해야 한다")
            void it_returns_my_info_with_can_reserve_true() {
                // Given
                var userId = 1L;
                var user = createUser();
                given(currentUserProvider.getCurrentUserId()).willReturn(userId);
                given(userRepository.findById(userId)).willReturn(Optional.of(user));
                given(penaltyRedisUtil.getPenaltyExpiryTime(userId)).willReturn(null);

                // When
                var result = queryMyInfoService.execute();

                // Then
                assertThat(result).isNotNull();
                assertThat(result.name()).isEqualTo("김철수");
                assertThat(result.studentId()).isEqualTo("20210001");
                assertThat(result.roomNumber()).isEqualTo("301");
                assertThat(result.canReserve()).isTrue();
                assertThat(result.penaltyExpiresAt()).isNull();
            }
        }

        @Nested
        @DisplayName("패널티가 적용 중인 사용자가 내 정보를 조회할 때")
        class Context_with_penalized_user {

            @Test
            @DisplayName("예약 불가 상태와 패널티 만료 시간을 함께 반환해야 한다")
            void it_returns_my_info_with_penalty_info() {
                // Given
                var userId = 1L;
                var user = createUser();
                var penaltyExpiresAt = LocalDateTime.now().plusMinutes(30);
                given(currentUserProvider.getCurrentUserId()).willReturn(userId);
                given(userRepository.findById(userId)).willReturn(Optional.of(user));
                given(penaltyRedisUtil.getPenaltyExpiryTime(userId)).willReturn(penaltyExpiresAt);

                // When
                var result = queryMyInfoService.execute();

                // Then
                assertThat(result).isNotNull();
                assertThat(result.canReserve()).isFalse();
                assertThat(result.penaltyExpiresAt()).isEqualTo(penaltyExpiresAt);
            }
        }

        @Nested
        @DisplayName("존재하지 않는 사용자 ID로 조회할 때")
        class Context_with_nonexistent_user {

            @Test
            @DisplayName("ExpectedException이 발생하고 NOT_FOUND 상태를 반환해야 한다")
            void it_throws_not_found_exception() {
                // Given
                var userId = 999L;
                given(currentUserProvider.getCurrentUserId()).willReturn(userId);
                given(userRepository.findById(userId)).willReturn(Optional.empty());

                // When & Then
                assertThatThrownBy(() -> queryMyInfoService.execute())
                        .isInstanceOf(ExpectedException.class)
                        .hasMessage("사용자를 찾을 수 없습니다")
                        .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                                .isEqualTo(HttpStatus.NOT_FOUND));
            }
        }
    }
}
