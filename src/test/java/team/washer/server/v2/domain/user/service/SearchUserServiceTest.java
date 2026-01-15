package team.washer.server.v2.domain.user.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import team.washer.server.v2.domain.user.dto.UserListResponseDto;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.domain.user.service.impl.SearchUserServiceImpl;

@ExtendWith(MockitoExtension.class)
@DisplayName("QueryUsersByFilterServiceImpl 클래스의")
class SearchUserServiceTest {

    @InjectMocks
    private SearchUserServiceImpl queryUsersByFilterService;

    @Mock
    private UserRepository userRepository;

    private User createUser(String name, String studentId, String roomNumber, Integer grade, Integer floor) {
        return User.builder().name(name).studentId(studentId).roomNumber(roomNumber).grade(grade).floor(floor)
                .penaltyCount(0).build();
    }

    @Nested
    @DisplayName("getUsersByFilter 메서드는")
    class Describe_getUsersByFilter {

        @Nested
        @DisplayName("이름으로 필터링할 때")
        class Context_with_name_filter {

            @Test
            @DisplayName("이름을 포함하는 사용자 목록을 반환해야 한다")
            void it_returns_users_by_name() {
                // Given
                String searchName = "김";
                User user1 = createUser("김철수", "20210001", "301", 3, 3);
                User user2 = createUser("김영희", "20210002", "302", 2, 3);
                List<User> users = Arrays.asList(user1, user2);

                given(userRepository.findUsersByFilter(searchName, null, null, null)).willReturn(users);

                // When
                UserListResponseDto result = queryUsersByFilterService.getUsersByFilter(searchName, null, null, null);

                // Then
                assertThat(result.getTotalCount()).isEqualTo(2);
                assertThat(result.getUsers()).allMatch(u -> u.getName().contains("김"));
                then(userRepository).should(times(1)).findUsersByFilter(searchName, null, null, null);
            }
        }

        @Nested
        @DisplayName("호실로 필터링할 때")
        class Context_with_room_number_filter {

            @Test
            @DisplayName("해당 호실의 사용자 목록을 반환해야 한다")
            void it_returns_users_by_room_number() {
                // Given
                String roomNumber = "301";
                User user = createUser("김철수", "20210001", "301", 3, 3);
                List<User> users = List.of(user);

                given(userRepository.findUsersByFilter(null, roomNumber, null, null)).willReturn(users);

                // When
                UserListResponseDto result = queryUsersByFilterService.getUsersByFilter(null, roomNumber, null, null);

                // Then
                assertThat(result.getTotalCount()).isEqualTo(1);
                assertThat(result.getUsers().get(0).getRoomNumber()).isEqualTo("301");
                then(userRepository).should(times(1)).findUsersByFilter(null, roomNumber, null, null);
            }
        }

        @Nested
        @DisplayName("학년과 층으로 필터링할 때")
        class Context_with_grade_and_floor_filter {

            @Test
            @DisplayName("해당 학년과 층의 사용자 목록을 반환해야 한다")
            void it_returns_users_by_grade_and_floor() {
                // Given
                Integer grade = 3;
                Integer floor = 3;
                User user1 = createUser("김철수", "20210001", "301", 3, 3);
                User user2 = createUser("이영희", "20210002", "302", 3, 3);
                List<User> users = Arrays.asList(user1, user2);

                given(userRepository.findUsersByFilter(null, null, grade, floor)).willReturn(users);

                // When
                UserListResponseDto result = queryUsersByFilterService.getUsersByFilter(null, null, grade, floor);

                // Then
                assertThat(result.getTotalCount()).isEqualTo(2);
                assertThat(result.getUsers()).allMatch(u -> u.getGrade().equals(3) && u.getFloor().equals(3));
                then(userRepository).should(times(1)).findUsersByFilter(null, null, grade, floor);
            }
        }

        @Nested
        @DisplayName("학년으로만 필터링할 때")
        class Context_with_grade_only_filter {

            @Test
            @DisplayName("해당 학년의 사용자 목록을 반환해야 한다")
            void it_returns_users_by_grade() {
                // Given
                Integer grade = 2;
                User user = createUser("박민수", "20220001", "201", 2, 2);
                List<User> users = List.of(user);

                given(userRepository.findUsersByFilter(null, null, grade, null)).willReturn(users);

                // When
                UserListResponseDto result = queryUsersByFilterService.getUsersByFilter(null, null, grade, null);

                // Then
                assertThat(result.getTotalCount()).isEqualTo(1);
                assertThat(result.getUsers().get(0).getGrade()).isEqualTo(2);
                then(userRepository).should(times(1)).findUsersByFilter(null, null, grade, null);
            }
        }

        @Nested
        @DisplayName("층으로만 필터링할 때")
        class Context_with_floor_only_filter {

            @Test
            @DisplayName("해당 층의 사용자 목록을 반환해야 한다")
            void it_returns_users_by_floor() {
                // Given
                Integer floor = 4;
                User user = createUser("최지우", "20200001", "401", 4, 4);
                List<User> users = List.of(user);

                given(userRepository.findUsersByFilter(null, null, null, floor)).willReturn(users);

                // When
                UserListResponseDto result = queryUsersByFilterService.getUsersByFilter(null, null, null, floor);

                // Then
                assertThat(result.getTotalCount()).isEqualTo(1);
                assertThat(result.getUsers().get(0).getFloor()).isEqualTo(4);
                then(userRepository).should(times(1)).findUsersByFilter(null, null, null, floor);
            }
        }

        @Nested
        @DisplayName("필터가 없을 때")
        class Context_without_filter {

            @Test
            @DisplayName("모든 사용자 목록을 반환해야 한다")
            void it_returns_all_users() {
                // Given
                User user1 = createUser("김철수", "20210001", "301", 3, 3);
                User user2 = createUser("이영희", "20210002", "302", 2, 2);
                List<User> users = Arrays.asList(user1, user2);

                given(userRepository.findUsersByFilter(null, null, null, null)).willReturn(users);

                // When
                UserListResponseDto result = queryUsersByFilterService.getUsersByFilter(null, null, null, null);

                // Then
                assertThat(result.getTotalCount()).isEqualTo(2);
                then(userRepository).should(times(1)).findUsersByFilter(null, null, null, null);
            }

            @Test
            @DisplayName("빈 문자열 필터도 무시하고 모든 사용자를 반환해야 한다")
            void it_ignores_empty_string_filters() {
                // Given
                User user = createUser("김철수", "20210001", "301", 3, 3);
                List<User> users = List.of(user);

                given(userRepository.findUsersByFilter("", "", null, null)).willReturn(users);

                // When
                UserListResponseDto result = queryUsersByFilterService.getUsersByFilter("", "", null, null);

                // Then
                assertThat(result.getTotalCount()).isEqualTo(1);
                then(userRepository).should(times(1)).findUsersByFilter("", "", null, null);
            }
        }

        @Nested
        @DisplayName("여러 조건으로 동시에 필터링할 때")
        class Context_with_multiple_filters {

            @Test
            @DisplayName("모든 조건을 AND로 조합하여 사용자를 반환해야 한다")
            void it_returns_users_matching_all_conditions() {
                // Given
                String name = "김";
                Integer grade = 3;
                Integer floor = 3;
                User user = createUser("김철수", "20210001", "301", 3, 3);
                List<User> users = List.of(user);

                given(userRepository.findUsersByFilter(name, null, grade, floor)).willReturn(users);

                // When
                UserListResponseDto result = queryUsersByFilterService.getUsersByFilter(name, null, grade, floor);

                // Then
                assertThat(result.getTotalCount()).isEqualTo(1);
                assertThat(result.getUsers().get(0).getName()).contains("김");
                assertThat(result.getUsers().get(0).getGrade()).isEqualTo(3);
                assertThat(result.getUsers().get(0).getFloor()).isEqualTo(3);
                then(userRepository).should(times(1)).findUsersByFilter(name, null, grade, floor);
            }
        }
    }
}
