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
import team.washer.server.v2.domain.auth.repository.WithdrawnStudentRepository;
import team.washer.server.v2.domain.auth.service.SignInService;
import team.washer.server.v2.domain.auth.support.ExistingUserSignInSupport;
import team.washer.server.v2.domain.auth.support.TokenGenerationSupport;
import team.washer.server.v2.domain.auth.util.WithdrawnStudentRedisUtil;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.support.UserRegistrationSupport;
import team.washer.server.v2.global.common.error.code.ErrorCode;
import team.washer.server.v2.global.common.error.exception.ErrorCodeException;
import team.washer.server.v2.global.util.DateTimeUtil;

@Service
@AllArgsConstructor
public class SignInServiceImpl implements SignInService {
    private final DataGsmOAuthClient oauthClient;
    private final UserRegistrationSupport userRegistrationSupport;
    private final ExistingUserSignInSupport existingUserSignInSupport;
    private final TokenGenerationSupport tokenGenerationSupport;
    private final WithdrawnStudentRepository withdrawnStudentRepository;
    private final WithdrawnStudentRedisUtil withdrawnStudentRedisUtil;

    @Override
    public TokenResDto execute(TokenReqDto reqDto) {
        String accessToken = oauthClient.exchangeCodeForToken(reqDto.authCode(), reqDto.redirectUri()).getAccessToken();
        Student oauthUser = oauthClient.getUserInfo(accessToken).getStudent();
        if (oauthUser == null) {
            throw new ExpectedException("학생정보가 없는 DataGSM 계정입니다.", HttpStatus.BAD_REQUEST);
        }

        final String studentId = oauthUser.getStudentNumber().toString();
        final var existingUserTokens = existingUserSignInSupport.generateIfExistingUser(studentId);
        if (existingUserTokens.isPresent()) {
            return existingUserTokens.get();
        }

        final boolean withdrawnInDatabase = withdrawnStudentRepository.existsByStudentIdAndExpiresAtAfter(studentId,
                DateTimeUtil.nowInKorea());
        if (withdrawnInDatabase || withdrawnStudentRedisUtil.isWithdrawnRecently(studentId)) {
            throw new ErrorCodeException(ErrorCode.WITHDRAWN_REJOIN_RESTRICTED);
        }
        User user;
        try {
            user = userRegistrationSupport.register(oauthUser);
        } catch (DataIntegrityViolationException e) {
            return existingUserSignInSupport.generateIfExistingUser(studentId).orElseThrow(
                    () -> new ExpectedException("회원가입 과정에서 오류가 발생했습니다. 다시 시도해주세요.", HttpStatus.INTERNAL_SERVER_ERROR));
        }

        return tokenGenerationSupport.generate(user.getId(), user.getRole());
    }
}
