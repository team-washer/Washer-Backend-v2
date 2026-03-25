package team.washer.server.v2.domain.auth.service.impl;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import team.themoment.datagsm.sdk.oauth.DataGsmOAuthClient;
import team.themoment.datagsm.sdk.oauth.model.Student;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.auth.dto.request.TokenReqDto;
import team.washer.server.v2.domain.auth.dto.response.TokenResDto;
import team.washer.server.v2.domain.auth.service.SignInService;
import team.washer.server.v2.domain.auth.support.TokenGenerationSupport;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.domain.user.support.UserRegistrationSupport;
import team.washer.server.v2.global.thirdparty.datagsm.config.DataGsmEnvironment;

@Service
@AllArgsConstructor
public class SignInServiceImpl implements SignInService {
    private final DataGsmOAuthClient oauthClient;
    private final DataGsmEnvironment dataGsmEnvironment;
    private final UserRepository userRepository;
    private final UserRegistrationSupport userRegistrationSupport;
    private final TokenGenerationSupport tokenGenerationSupport;

    @Override
    public TokenResDto execute(TokenReqDto reqDto) {
        String accessToken = oauthClient.exchangeCodeForToken(reqDto.authCode(), dataGsmEnvironment.redirectUri())
                .getAccessToken();
        Student oauthUser = oauthClient.getUserInfo(accessToken).getStudent();
        if (oauthUser == null) {
            throw new ExpectedException("학생정보가 없는 DataGSM 계정입니다.", HttpStatus.BAD_REQUEST);
        }
        User user;
        try {
            user = userRepository.findByStudentId(oauthUser.getStudentNumber().toString())
                    .orElseGet(() -> userRegistrationSupport.register(oauthUser));
        } catch (DataIntegrityViolationException e) {
            user = userRepository.findByStudentId(oauthUser.getStudentNumber().toString()).orElseThrow(
                    () -> new ExpectedException("회원가입 과정에서 오류가 발생했습니다. 다시 시도해주세요.", HttpStatus.INTERNAL_SERVER_ERROR));
        }

        return tokenGenerationSupport.generate(user.getId(), user.getRole());
    }
}
