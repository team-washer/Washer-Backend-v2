package team.washer.server.v2.domain.auth.support;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.auth.dto.response.TokenResDto;
import team.washer.server.v2.domain.user.repository.UserRepository;

@Component
@RequiredArgsConstructor
public class ExistingUserSignInSupport {

    private final UserRepository userRepository;
    private final TokenGenerationSupport tokenGenerationSupport;

    @Transactional
    public Optional<TokenResDto> generateIfExistingUser(final String studentId) {
        return userRepository.findByStudentId(studentId).flatMap(user -> userRepository.findByIdForUpdate(user.getId()))
                .map(user -> tokenGenerationSupport.generate(user.getId(), user.getRole()));
    }
}
