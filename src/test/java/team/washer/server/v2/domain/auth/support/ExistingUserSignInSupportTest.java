package team.washer.server.v2.domain.auth.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import team.washer.server.v2.domain.auth.dto.response.TokenResDto;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExistingUserSignInSupport 단위 테스트")
class ExistingUserSignInSupportTest {

    @InjectMocks
    private ExistingUserSignInSupport support;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenGenerationSupport tokenGenerationSupport;

    @Test
    @DisplayName("기존 사용자 로그인은 탈퇴와 같은 사용자 잠금 조회를 사용해 토큰을 발급해야 한다")
    void generatesTokensFromLockedExistingUser() {
        final var user = mock(User.class);
        final var expectedTokens = new TokenResDto("access.token", 3600L, "refresh.token");

        given(userRepository.findByStudentIdForUpdate("20210001")).willReturn(Optional.of(user));
        given(user.getId()).willReturn(1L);
        given(tokenGenerationSupport.generate(1L, user.getRole())).willReturn(expectedTokens);

        assertThat(support.generateIfExistingUser("20210001")).contains(expectedTokens);

        verify(userRepository).findByStudentIdForUpdate("20210001");
        verify(tokenGenerationSupport).generate(1L, user.getRole());
    }
}
