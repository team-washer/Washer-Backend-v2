package team.washer.server.v2.domain.auth.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AllArgsConstructor;
import team.themoment.datagsm.sdk.oauth.DataGsmClient;
import team.themoment.datagsm.sdk.oauth.model.Student;
import team.washer.server.v2.domain.auth.dto.request.TokenReqDto;
import team.washer.server.v2.domain.auth.dto.response.TokenResDto;
import team.washer.server.v2.domain.auth.service.GenerateTokenService;
import team.washer.server.v2.domain.auth.service.SignInService;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.domain.user.service.SignUpService;
import team.washer.server.v2.global.common.error.exception.ExpectedException;

@Service
@AllArgsConstructor
public class SignInServiceImpl implements SignInService {
    private final DataGsmClient oauthClient;
    private final UserRepository userRepository;
    private final SignUpService signUpService;
    private final GenerateTokenService generateTokenService;

    @Override
    @Transactional
    public TokenResDto execute(TokenReqDto reqDto) {
        String accessToken = oauthClient.exchangeToken(reqDto.authCode()).getAccessToken();
        Student oauthUser = oauthClient.getUserInfo(accessToken).getStudent();
        if (oauthUser == null) {
            throw new ExpectedException("학생정보가 없는 DataGSM 계정입니다.", HttpStatus.BAD_REQUEST);
        }
        User user = userRepository.findByStudentId(oauthUser.getStudentNumber().toString())
                .orElse(signUpService.execute(oauthUser));

        return generateTokenService.execute(user.getId(), user.getRole());
    }
}
