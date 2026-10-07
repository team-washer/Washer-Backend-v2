package team.washer.server.v2.domain.auth.service.impl;

import java.io.IOException;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.themoment.datagsm.sdk.oauth.DataGsmOAuthClient;
import team.themoment.datagsm.sdk.oauth.exception.BadRequestException;
import team.themoment.datagsm.sdk.oauth.exception.DataGsmException;
import team.themoment.datagsm.sdk.oauth.exception.UnauthorizedException;
import team.themoment.datagsm.sdk.oauth.model.Student;
import team.themoment.datagsm.sdk.oauth.model.TokenResponse;
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
@Slf4j
public class SignInServiceImpl implements SignInService {
    private static final Pattern CODE_VERIFIER_PATTERN = Pattern.compile(TokenReqDto.CODE_VERIFIER_REGEX);

    private final DataGsmOAuthClient oauthClient;
    private final UserRegistrationSupport userRegistrationSupport;
    private final ExistingUserSignInSupport existingUserSignInSupport;
    private final TokenGenerationSupport tokenGenerationSupport;
    private final WithdrawnStudentRepository withdrawnStudentRepository;
    private final WithdrawnStudentRedisUtil withdrawnStudentRedisUtil;

    @Override
    public TokenResDto execute(TokenReqDto reqDto) {
        final var pkce = reqDto.codeVerifier() != null;
        final var tokenResponse = exchangeCodeForToken(reqDto);
        if (tokenResponse == null || tokenResponse.getAccessToken() == null) {
            throw new ErrorCodeException(ErrorCode.SERVICE_UNAVAILABLE);
        }
        final var accessToken = tokenResponse.getAccessToken();
        final Student oauthUser;
        try {
            final var userInfo = oauthClient.getUserInfo(accessToken);
            if (userInfo == null) {
                throw new ErrorCodeException(ErrorCode.SERVICE_UNAVAILABLE);
            }
            oauthUser = userInfo.getStudent();
        } catch (BadRequestException | UnauthorizedException e) {
            logAuthenticationRejected("user_info", e, pkce);
            throw invalidAuthenticationException();
        } catch (DataGsmException e) {
            throw mapTransientAuthenticationFailure("user_info", e, pkce);
        }
        if (oauthUser == null || oauthUser.getStudentNumber() == null) {
            throw new ExpectedException("학생정보가 없는 DataGSM 계정입니다.", HttpStatus.BAD_REQUEST);
        }

        logAuthenticationSucceeded(pkce);

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
        final User user;
        try {
            user = userRegistrationSupport.register(oauthUser);
        } catch (DataIntegrityViolationException e) {
            return existingUserSignInSupport.generateIfExistingUser(studentId).orElseThrow(
                    () -> new ExpectedException("회원가입 과정에서 오류가 발생했습니다. 다시 시도해주세요.", HttpStatus.INTERNAL_SERVER_ERROR));
        }

        return tokenGenerationSupport.generate(user.getId(), user.getRole());
    }

    private TokenResponse exchangeCodeForToken(final TokenReqDto reqDto) {
        final var codeVerifier = reqDto.codeVerifier();
        if (codeVerifier != null && !CODE_VERIFIER_PATTERN.matcher(codeVerifier).matches()) {
            throw new ExpectedException(TokenReqDto.CODE_VERIFIER_FORMAT_MESSAGE, HttpStatus.BAD_REQUEST);
        }
        try {
            if (codeVerifier != null) {
                return oauthClient.exchangeCodeForToken(reqDto.authCode(), reqDto.redirectUri(), codeVerifier);
            }
            return oauthClient.exchangeCodeForToken(reqDto.authCode(), reqDto.redirectUri());
        } catch (BadRequestException | UnauthorizedException e) {
            logAuthenticationRejected("token_exchange", e, codeVerifier != null);
            throw invalidAuthenticationException();
        } catch (DataGsmException e) {
            throw mapTransientAuthenticationFailure("token_exchange", e, codeVerifier != null);
        }
    }

    private static RuntimeException mapTransientAuthenticationFailure(final String operation,
            final DataGsmException exception,
            final boolean pkce) {
        final var status = exception.hasStatusCode() ? String.valueOf(exception.getStatusCode()) : "none";
        final var cause = exception.getCause() == null ? "none" : exception.getCause().getClass().getSimpleName();
        if (!isTransientAuthenticationFailure(exception)) {
            log.error("datagsm authentication provider failure operation={} exception={} status={} cause={} pkce={}",
                    operation,
                    exception.getClass().getSimpleName(),
                    status,
                    cause,
                    pkce);
            return new ErrorCodeException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        log.warn("datagsm authentication temporarily unavailable operation={} exception={} status={} cause={} pkce={}",
                operation,
                exception.getClass().getSimpleName(),
                status,
                cause,
                pkce);
        return new ErrorCodeException(ErrorCode.SERVICE_UNAVAILABLE);
    }

    private static boolean isTransientAuthenticationFailure(final DataGsmException exception) {
        if (!exception.hasStatusCode()) {
            return exception.getCause() instanceof IOException;
        }
        final var statusCode = exception.getStatusCode();
        return statusCode == HttpStatus.REQUEST_TIMEOUT.value() || statusCode == HttpStatus.TOO_MANY_REQUESTS.value()
                || statusCode >= HttpStatus.INTERNAL_SERVER_ERROR.value();
    }

    private static void logAuthenticationRejected(final String operation,
            final Exception exception,
            final boolean pkce) {
        log.warn("datagsm authentication rejected operation={} exception={} pkce={}",
                operation,
                exception.getClass().getSimpleName(),
                pkce);
    }

    private static void logAuthenticationSucceeded(final boolean pkce) {
        log.info("datagsm authentication succeeded pkce={}", pkce);
    }

    private static ExpectedException invalidAuthenticationException() {
        return new ExpectedException("인증 정보가 올바르지 않습니다. 다시 로그인해 주세요.", HttpStatus.UNAUTHORIZED);
    }
}
