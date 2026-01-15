package team.washer.server.v2.domain.user.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.util.Arrays;
import java.util.Collections;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("QueryAllUsersServiceImpl 테스트")
class QueryAllUsersServiceImplTest {

    @InjectMocks
    private QueryAllUsersServiceImpl queryAllUsersService;

    @Mock
    private UserRepository userRepository;

    @Nested
    @DisplayName("Describe: getAllUsers 메서드는")
    class Describe_getAllUsers {

        @Nested
        @DisplayName("Context: 사용자가 존재할 때")
        class Context_with_existing_users {

            @Test
            @DisplayName("It: 모든 사용자 목록을 반환한다")
            void it_returns_all_users() {
                // Given
                User user1 = User.builder().name("김철수").studentId("20210001").roomNumber("301").grade(3).floor(3)
                        .penaltyCount(0).build();

                User user2 = User.builder().name("이영희").studentId("20210002").roomNumber("302").grade(3).floor(3)
                        .penaltyCount(1).build();

                List<User> users = Arrays.asList(user1, user2);
                given(userRepository.findAll()).willReturn(users);

                // When
                UserListResponseDto result = queryAllUsersService.getAllUsers();

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getTotalCount()).isEqualTo(2);
                assertThat(result.getUsers()).hasSize(2);
                assertThat(result.getUsers().get(0).getName()).isEqualTo("김철수");
                assertThat(result.getUsers().get(1).getName()).isEqualTo("이영희");

                then(userRepository).should(times(1)).findAll();
            }
        }

        @Nested
        @DisplayName("Context: 사용자가 존재하지 않을 때")
        class Context_with_no_users {

            @Test
            @DisplayName("It: 빈 목록을 반환한다")
            void it_returns_empty_list() {
                // Given
                given(userRepository.findAll()).willReturn(Collections.emptyList());

                // When
                UserListResponseDto result = queryAllUsersService.getAllUsers();

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getTotalCount()).isEqualTo(0);
                assertThat(result.getUsers()).isEmpty();

                then(userRepository).should(times(1)).findAll();
            }
        }
    }
}
