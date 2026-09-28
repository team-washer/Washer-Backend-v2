package team.washer.server.v2.domain.notification.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import team.washer.server.v2.domain.notification.service.impl.DeleteFcmTokenIfMatchesServiceImpl;
import team.washer.server.v2.domain.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeleteFcmTokenIfMatchesServiceImpl 클래스의")
class DeleteFcmTokenIfMatchesServiceTest {

    private static final Long USER_ID = 1L;
    private static final String STALE_TOKEN = "stale-fcm-token";

    @InjectMocks
    private DeleteFcmTokenIfMatchesServiceImpl deleteFcmTokenIfMatchesService;

    @Mock
    private UserRepository userRepository;

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Test
        @DisplayName("저장된 토큰과 일치하면 토큰 삭제를 완료해야 한다")
        void it_deletes_matching_token() {
            // Given
            given(userRepository.clearFcmTokenIfMatches(USER_ID, STALE_TOKEN)).willReturn(1);

            // When
            deleteFcmTokenIfMatchesService.execute(USER_ID, STALE_TOKEN);

            // Then
            then(userRepository).should(times(1)).clearFcmTokenIfMatches(USER_ID, STALE_TOKEN);
        }

        @Test
        @DisplayName("저장된 토큰이 변경되었으면 최신 토큰을 유지해야 한다")
        void it_keeps_changed_token() {
            // Given
            given(userRepository.clearFcmTokenIfMatches(USER_ID, STALE_TOKEN)).willReturn(0);

            // When
            assertThatCode(() -> deleteFcmTokenIfMatchesService.execute(USER_ID, STALE_TOKEN))
                    .doesNotThrowAnyException();

            // Then
            then(userRepository).should(times(1)).clearFcmTokenIfMatches(USER_ID, STALE_TOKEN);
        }

        @Test
        @DisplayName("빈 토큰이면 저장소를 호출하지 않아야 한다")
        void it_skips_blank_token() {
            // When
            deleteFcmTokenIfMatchesService.execute(USER_ID, " ");

            // Then
            then(userRepository).shouldHaveNoInteractions();
        }
    }
}
