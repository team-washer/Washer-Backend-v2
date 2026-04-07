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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import team.washer.server.v2.domain.reservation.util.PenaltyRedisUtil;
import team.washer.server.v2.domain.user.dto.response.UserListResDto;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.domain.user.service.impl.SearchUserServiceImpl;

@ExtendWith(MockitoExtension.class)
@DisplayName("SearchUserServiceImpl 클래스의")
class SearchUserServiceTest {

    @InjectMocks
    private SearchUserServiceImpl searchUserService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PenaltyRedisUtil penaltyRedisUtil;

    private final Pageable defaultPageable = PageRequest.of(0, 20);

    private User createUser(String name, String studentId, String roomNumber, Integer grade, Integer floor) {
        return User.builder().name(name).studentId(studentId).roomNumber(roomNumber).grade(grade).floor(floor)
                .penaltyCount(0).build();
    }

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

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
                Page<User> userPage = new PageImpl<>(users, defaultPageable, users.size());

                given(userRepository.findUsersByFilter(searchName, null, null, null, null, defaultPageable))
                        .willReturn(userPage);

                // When
                UserListResDto result = searchUserService.execute(searchName, null, null, null, null, defaultPageable);

                // Then
                assertThat(result.totalCount()).isEqualTo(2);
                assertThat(result.users()).allMatch(u -> u.name().contains("김"));
                then(userRepository).should(times(1))
                        .findUsersByFilter(searchName, null, null, null, null, defaultPageable);
            }
        }

        @Nested
        @DisplayName("학번으로 필터링할 때")
        class Context_with_student_id_filter {

            @Test
            @DisplayName("학번을 포함하는 사용자 목록을 반환해야 한다")
            void it_returns_users_by_student_id() {
                // Given
                String studentId = "2021";
                User user1 = createUser("김철수", "20210001", "301", 3, 3);
                User user2 = createUser("이영희", "20210002", "302", 2, 3);
                List<User> users = Arrays.asList(user1, user2);
                Page<User> userPage = new PageImpl<>(users, defaultPageable, users.size());

                given(userRepository.findUsersByFilter(null, studentId, null, null, null, defaultPageable))
                        .willReturn(userPage);

                // When
                UserListResDto result = searchUserService.execute(null, studentId, null, null, null, defaultPageable);

                // Then
                assertThat(result.totalCount()).isEqualTo(2);
                assertThat(result.users()).allMatch(u -> u.studentId().contains("2021"));
                then(userRepository).should(times(1))
                        .findUsersByFilter(null, studentId, null, null, null, defaultPageable);
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
                Page<User> userPage = new PageImpl<>(users, defaultPageable, users.size());

                given(userRepository.findUsersByFilter(null, null, roomNumber, null, null, defaultPageable))
                        .willReturn(userPage);

                // When
                UserListResDto result = searchUserService.execute(null, null, roomNumber, null, null, defaultPageable);

                // Then
                assertThat(result.totalCount()).isEqualTo(1);
                assertThat(result.users().get(0).roomNumber()).isEqualTo("301");
                then(userRepository).should(times(1))
                        .findUsersByFilter(null, null, roomNumber, null, null, defaultPageable);
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
                Page<User> userPage = new PageImpl<>(users, defaultPageable, users.size());

                given(userRepository.findUsersByFilter(null, null, null, grade, floor, defaultPageable))
                        .willReturn(userPage);

                // When
                UserListResDto result = searchUserService.execute(null, null, null, grade, floor, defaultPageable);

                // Then
                assertThat(result.totalCount()).isEqualTo(2);
                assertThat(result.users()).allMatch(u -> u.grade().equals(3) && u.floor().equals(3));
                then(userRepository).should(times(1))
                        .findUsersByFilter(null, null, null, grade, floor, defaultPageable);
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
                Page<User> userPage = new PageImpl<>(users, defaultPageable, users.size());

                given(userRepository.findUsersByFilter(null, null, null, null, null, defaultPageable))
                        .willReturn(userPage);

                // When
                UserListResDto result = searchUserService.execute(null, null, null, null, null, defaultPageable);

                // Then
                assertThat(result.totalCount()).isEqualTo(2);
                then(userRepository).should(times(1)).findUsersByFilter(null, null, null, null, null, defaultPageable);
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
                Page<User> userPage = new PageImpl<>(users, defaultPageable, users.size());

                given(userRepository.findUsersByFilter(name, null, null, grade, floor, defaultPageable))
                        .willReturn(userPage);

                // When
                UserListResDto result = searchUserService.execute(name, null, null, grade, floor, defaultPageable);

                // Then
                assertThat(result.totalCount()).isEqualTo(1);
                assertThat(result.users().get(0).name()).contains("김");
                assertThat(result.users().get(0).grade()).isEqualTo(3);
                assertThat(result.users().get(0).floor()).isEqualTo(3);
                then(userRepository).should(times(1))
                        .findUsersByFilter(name, null, null, grade, floor, defaultPageable);
            }
        }
    }
}
