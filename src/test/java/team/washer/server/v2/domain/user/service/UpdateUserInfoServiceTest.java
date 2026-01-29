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

import team.washer.server.v2.domain.user.dto.response.UserUpdateResDto;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.domain.user.service.impl.UpdateUserInfoServiceImpl;
import team.washer.server.v2.global.common.error.exception.ExpectedException;

@ExtendWith(MockitoExtension.class)
@DisplayName("UpdateUserInfoServiceImpl 클래스의")
class UpdateUserInfoServiceTest {

    @InjectMocks
    private UpdateUserInfoServiceImpl updateUserInfoService;

    @Mock
    private UserRepository userRepository;

    private User createUser() {
        return User.builder().name("김철수").studentId("20210001").roomNumber("301").grade(3).floor(3).penaltyCount(0)
                .build();
    }

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Nested
        @DisplayName("모든 필드를 수정할 때")
        class Context_with_all_fields {

            @Test
            @DisplayName("모든 필드가 변경되어야 한다")
            void it_updates_all_fields() {
                // Given
                Long userId = 1L;
                User user = createUser();
                String newRoomNumber = "401";
                Integer newGrade = 4;
                Integer newFloor = 4;

                given(userRepository.findById(userId)).willReturn(Optional.of(user));
                given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

                // When
                UserUpdateResDto result = updateUserInfoService.execute(userId, newRoomNumber, newGrade, newFloor);

                // Then
                assertThat(result.roomNumber()).isEqualTo("401");
                assertThat(result.grade()).isEqualTo(4);
                assertThat(result.floor()).isEqualTo(4);
                assertThat(user.getRoomNumber()).isEqualTo("401");
                assertThat(user.getGrade()).isEqualTo(4);
                assertThat(user.getFloor()).isEqualTo(4);
                then(userRepository).should(times(1)).findById(userId);
                then(userRepository).should(times(1)).save(any(User.class));
            }
        }

        @Nested
        @DisplayName("호실만 수정할 때")
        class Context_with_room_number_only {

            @Test
            @DisplayName("호실만 변경되고 나머지는 유지되어야 한다")
            void it_updates_room_number_only() {
                // Given
                Long userId = 1L;
                User user = createUser();
                String newRoomNumber = "401";

                given(userRepository.findById(userId)).willReturn(Optional.of(user));
                given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

                // When
                UserUpdateResDto result = updateUserInfoService.execute(userId, newRoomNumber, null, null);

                // Then
                assertThat(result.roomNumber()).isEqualTo("401");
                assertThat(result.grade()).isEqualTo(3);
                assertThat(result.floor()).isEqualTo(3);
                assertThat(user.getRoomNumber()).isEqualTo("401");
                assertThat(user.getGrade()).isEqualTo(3);
                assertThat(user.getFloor()).isEqualTo(3);
                then(userRepository).should(times(1)).findById(userId);
                then(userRepository).should(times(1)).save(any(User.class));
            }
        }

        @Nested
        @DisplayName("학년만 수정할 때")
        class Context_with_grade_only {

            @Test
            @DisplayName("학년만 변경되고 나머지는 유지되어야 한다")
            void it_updates_grade_only() {
                // Given
                Long userId = 1L;
                User user = createUser();
                Integer newGrade = 4;

                given(userRepository.findById(userId)).willReturn(Optional.of(user));
                given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

                // When
                UserUpdateResDto result = updateUserInfoService.execute(userId, null, newGrade, null);

                // Then
                assertThat(result.roomNumber()).isEqualTo("301");
                assertThat(result.grade()).isEqualTo(4);
                assertThat(result.floor()).isEqualTo(3);
                assertThat(user.getRoomNumber()).isEqualTo("301");
                assertThat(user.getGrade()).isEqualTo(4);
                assertThat(user.getFloor()).isEqualTo(3);
                then(userRepository).should(times(1)).findById(userId);
                then(userRepository).should(times(1)).save(any(User.class));
            }
        }

        @Nested
        @DisplayName("층만 수정할 때")
        class Context_with_floor_only {

            @Test
            @DisplayName("층만 변경되고 나머지는 유지되어야 한다")
            void it_updates_floor_only() {
                // Given
                Long userId = 1L;
                User user = createUser();
                Integer newFloor = 4;

                given(userRepository.findById(userId)).willReturn(Optional.of(user));
                given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

                // When
                UserUpdateResDto result = updateUserInfoService.execute(userId, null, null, newFloor);

                // Then
                assertThat(result.roomNumber()).isEqualTo("301");
                assertThat(result.grade()).isEqualTo(3);
                assertThat(result.floor()).isEqualTo(4);
                assertThat(user.getRoomNumber()).isEqualTo("301");
                assertThat(user.getGrade()).isEqualTo(3);
                assertThat(user.getFloor()).isEqualTo(4);
                then(userRepository).should(times(1)).findById(userId);
                then(userRepository).should(times(1)).save(any(User.class));
            }
        }

        @Nested
        @DisplayName("모든 필드가 null일 때")
        class Context_with_all_null {

            @Test
            @DisplayName("아무것도 변경되지 않아야 한다")
            void it_updates_nothing() {
                // Given
                Long userId = 1L;
                User user = createUser();

                given(userRepository.findById(userId)).willReturn(Optional.of(user));
                given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

                // When
                UserUpdateResDto result = updateUserInfoService.execute(userId, null, null, null);

                // Then
                assertThat(result.roomNumber()).isEqualTo("301");
                assertThat(result.grade()).isEqualTo(3);
                assertThat(result.floor()).isEqualTo(3);
                assertThat(user.getRoomNumber()).isEqualTo("301");
                assertThat(user.getGrade()).isEqualTo(3);
                assertThat(user.getFloor()).isEqualTo(3);
                then(userRepository).should(times(1)).findById(userId);
                then(userRepository).should(times(1)).save(any(User.class));
            }
        }

        @Nested
        @DisplayName("존재하지 않는 사용자 ID로 요청할 때")
        class Context_with_nonexistent_user_id {

            @Test
            @DisplayName("ExpectedException을 던져야 한다")
            void it_throws_expected_exception() {
                // Given
                Long userId = 999L;

                given(userRepository.findById(userId)).willReturn(Optional.empty());

                // When & Then
                assertThatThrownBy(() -> updateUserInfoService.execute(userId, "401", 4, 4))
                        .isInstanceOf(ExpectedException.class).hasMessage("사용자를 찾을 수 없습니다")
                        .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);

                then(userRepository).should(times(1)).findById(userId);
                then(userRepository).should(never()).save(any(User.class));
            }
        }
    }
}
