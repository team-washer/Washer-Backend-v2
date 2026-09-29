package team.washer.server.v2.global.security.provider;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import team.washer.server.v2.global.common.error.code.ErrorCode;
import team.washer.server.v2.global.common.error.exception.ErrorCodeException;

/**
 * SecurityContext에서 현재 인증된 사용자의 ID를 추출하는 컴포넌트입니다.
 */
@Component
public class CurrentUserProvider {

    /**
     * 현재 요청의 SecurityContext에서 인증된 사용자의 ID를 반환합니다.
     *
     * @return 현재 인증된 사용자의 ID
     * @throws ExpectedException
     *             인증 정보가 존재하지 않을 경우 (401 UNAUTHORIZED)
     */
    public Long getCurrentUserId() {
        final var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new ErrorCodeException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
        return (Long) authentication.getPrincipal();
    }
}
