package team.washer.server.v2.domain.user.service;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import team.washer.server.v2.domain.user.dto.response.UserResDto;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.domain.user.service.impl.QueryUserByIdServiceImpl;
import team.washer.server.v2.global.common.error.exception.ExpectedException;
@ExtendWith(MockitoExtension.class)
@DisplayName("QueryUserByIdServiceImpl 클래스의")
class QueryUserByIdServiceTest {
    @InjectMocks
    private QueryUserByIdServiceImpl queryUserByIdService;
    @Mock
    private UserRepository userRepository;
    @Nested
    @DisplayName("getUserById 메서드는")
    class Describe_getUserById {
        @Nested
        @DisplayName("유효한 ID로 조회할 때")
        class Context_with_valid_id {
            @Test
            @DisplayName("해당 사용자 정보를 반환해야 한다")
            void it_returns_user() {
                // Given
                Long userId = 1L;
                User user = User.builder().name("김철수").studentId("20210001").roomNumber("301").grade(3).floor(3)
                        .penaltyCount(0).build();
                given(userRepository.findById(userId)).willReturn(Optional.of(user));
                // When
                UserResDto result = queryUserByIdService.getUserById(userId);
                // Then
                assertThat(result).isNotNull();
                assertThat(result.name()).isEqualTo("김철수");
                assertThat(result.studentId()).isEqualTo("20210001");
                assertThat(result.roomNumber()).isEqualTo("301");
                assertThat(result.grade()).isEqualTo(3);
                assertThat(result.floor()).isEqualTo(3);
                assertThat(result.penaltyCount()).isEqualTo(0);
                then(userRepository).should(times(1)).findById(userId);
            }
        }
        @Nested
        @DisplayName("존재하지 않는 ID로 조회할 때")
        class Context_with_invalid_id {
            @Test
            @DisplayName("ExpectedException이 발생해야 한다")
            void it_throws_expected_exception() {
                // Given
                Long invalidUserId = 999L;
                given(userRepository.findById(invalidUserId)).willReturn(Optional.empty());
                // When & Then
                assertThatThrownBy(() -> queryUserByIdService.getUserById(invalidUserId))
                        .isInstanceOf(ExpectedException.class).hasMessage("사용자를 찾을 수 없습니다").satisfies(exception -> {
                            ExpectedException expectedException = (ExpectedException) exception;
                            assertThat(expectedException.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                        });
                then(userRepository).should(times(1)).findById(invalidUserId);
            }
        }
    }
}
