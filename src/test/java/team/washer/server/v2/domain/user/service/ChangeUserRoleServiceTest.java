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

import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.enums.UserRole;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.domain.user.service.impl.ChangeUserRoleServiceImpl;
import team.washer.server.v2.global.security.provider.CurrentUserProvider;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChangeUserRoleServiceImpl 클래스의")
class ChangeUserRoleServiceTest {

    @InjectMocks
    private ChangeUserRoleServiceImpl changeUserRoleService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private User createAdmin() {
        return User.builder().name("관리자").studentId("20210001").roomNumber("301").grade(3).floor(3).role(UserRole.ADMIN)
                .build();
    }

    private User createCouncil() {
        return User.builder().name("자치위원").studentId("20210003").roomNumber("303").grade(3).floor(3)
                .role(UserRole.DORMITORY_COUNCIL).build();
    }

    private User createUser() {
        return User.builder().name("일반사용자").studentId("20210002").roomNumber("302").grade(3).floor(3)
                .role(UserRole.USER).build();
    }

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Nested
        @DisplayName("관리자가 일반 사용자를 자치위원회로 승격할 때")
        class Context_with_admin_promoting_user {

            @Test
            @DisplayName("권한을 정상적으로 변경하고 변경된 정보를 반환해야 한다")
            void it_changes_role() {
                // Given
                var adminId = 1L;
                var targetId = 2L;
                var admin = createAdmin();
                var target = createUser();
                given(currentUserProvider.getCurrentUserId()).willReturn(adminId);
                given(userRepository.findById(adminId)).willReturn(Optional.of(admin));
                given(userRepository.findById(targetId)).willReturn(Optional.of(target));

                // When
                var result = changeUserRoleService.execute(targetId, UserRole.DORMITORY_COUNCIL);

                // Then
                assertThat(result.role()).isEqualTo(UserRole.DORMITORY_COUNCIL);
                assertThat(target.getRole()).isEqualTo(UserRole.DORMITORY_COUNCIL);
                then(userRepository).should(times(1)).save(target);
            }
        }

        @Nested
        @DisplayName("관리자가 다른 관리자를 강등할 때 (관리자가 2명 이상)")
        class Context_with_admin_demoting_another_admin {

            @Test
            @DisplayName("권한을 정상적으로 강등해야 한다")
            void it_demotes_admin() {
                // Given
                var adminId = 1L;
                var targetId = 2L;
                var admin = createAdmin();
                var target = createAdmin();
                given(currentUserProvider.getCurrentUserId()).willReturn(adminId);
                given(userRepository.findById(adminId)).willReturn(Optional.of(admin));
                given(userRepository.findById(targetId)).willReturn(Optional.of(target));
                given(userRepository.countByRole(UserRole.ADMIN)).willReturn(2L);

                // When
                var result = changeUserRoleService.execute(targetId, UserRole.USER);

                // Then
                assertThat(result.role()).isEqualTo(UserRole.USER);
                assertThat(target.getRole()).isEqualTo(UserRole.USER);
            }
        }

        @Nested
        @DisplayName("자치위원회가 권한 변경을 시도할 때")
        class Context_with_council_actor {

            @Test
            @DisplayName("ExpectedException이 발생하고 FORBIDDEN 상태를 반환해야 한다")
            void it_throws_forbidden_exception() {
                // Given
                var councilId = 1L;
                given(currentUserProvider.getCurrentUserId()).willReturn(councilId);
                given(userRepository.findById(councilId)).willReturn(Optional.of(createCouncil()));

                // When & Then
                assertThatThrownBy(() -> changeUserRoleService.execute(2L, UserRole.DORMITORY_COUNCIL))
                        .isInstanceOf(ExpectedException.class).hasMessage("권한을 변경할 수 있는 관리자가 아닙니다.")
                        .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                                .isEqualTo(HttpStatus.FORBIDDEN));

                then(userRepository).should(never()).save(any());
            }
        }

        @Nested
        @DisplayName("존재하지 않는 호출자 ID로 요청할 때")
        class Context_with_nonexistent_actor {

            @Test
            @DisplayName("ExpectedException이 발생하고 NOT_FOUND 상태를 반환해야 한다")
            void it_throws_not_found_exception() {
                // Given
                var actorId = 999L;
                given(currentUserProvider.getCurrentUserId()).willReturn(actorId);
                given(userRepository.findById(actorId)).willReturn(Optional.empty());

                // When & Then
                assertThatThrownBy(() -> changeUserRoleService.execute(1L, UserRole.ADMIN))
                        .isInstanceOf(ExpectedException.class).hasMessage("사용자를 찾을 수 없습니다")
                        .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                                .isEqualTo(HttpStatus.NOT_FOUND));
            }
        }

        @Nested
        @DisplayName("관리자가 자기 자신의 권한을 변경하려 할 때")
        class Context_with_self_change {

            @Test
            @DisplayName("ExpectedException이 발생하고 BAD_REQUEST 상태를 반환해야 한다")
            void it_throws_bad_request_exception() {
                // Given
                var adminId = 1L;
                given(currentUserProvider.getCurrentUserId()).willReturn(adminId);
                given(userRepository.findById(adminId)).willReturn(Optional.of(createAdmin()));

                // When & Then
                assertThatThrownBy(() -> changeUserRoleService.execute(adminId, UserRole.USER))
                        .isInstanceOf(ExpectedException.class).hasMessage("자신의 권한은 변경할 수 없습니다.")
                        .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                                .isEqualTo(HttpStatus.BAD_REQUEST));

                then(userRepository).should(never()).save(any());
            }
        }

        @Nested
        @DisplayName("대상 사용자가 이미 해당 권한을 가지고 있을 때")
        class Context_with_same_role {

            @Test
            @DisplayName("ExpectedException이 발생하고 BAD_REQUEST 상태를 반환해야 한다")
            void it_throws_bad_request_exception() {
                // Given
                var adminId = 1L;
                var targetId = 2L;
                given(currentUserProvider.getCurrentUserId()).willReturn(adminId);
                given(userRepository.findById(adminId)).willReturn(Optional.of(createAdmin()));
                given(userRepository.findById(targetId)).willReturn(Optional.of(createUser()));

                // When & Then
                assertThatThrownBy(() -> changeUserRoleService.execute(targetId, UserRole.USER))
                        .isInstanceOf(ExpectedException.class).hasMessage("이미 해당 권한을 가진 사용자입니다.")
                        .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                                .isEqualTo(HttpStatus.BAD_REQUEST));

                then(userRepository).should(never()).save(any());
            }
        }

        @Nested
        @DisplayName("마지막 관리자를 강등하려 할 때 (관리자가 1명)")
        class Context_with_last_admin {

            @Test
            @DisplayName("ExpectedException이 발생하고 BAD_REQUEST 상태를 반환해야 한다")
            void it_throws_bad_request_exception() {
                // Given
                var adminId = 1L;
                var targetId = 2L;
                given(currentUserProvider.getCurrentUserId()).willReturn(adminId);
                given(userRepository.findById(adminId)).willReturn(Optional.of(createAdmin()));
                given(userRepository.findById(targetId)).willReturn(Optional.of(createAdmin()));
                given(userRepository.countByRole(UserRole.ADMIN)).willReturn(1L);

                // When & Then
                assertThatThrownBy(() -> changeUserRoleService.execute(targetId, UserRole.USER))
                        .isInstanceOf(ExpectedException.class).hasMessage("마지막 관리자의 권한은 변경할 수 없습니다.")
                        .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                                .isEqualTo(HttpStatus.BAD_REQUEST));

                then(userRepository).should(never()).save(any());
            }
        }
    }
}
