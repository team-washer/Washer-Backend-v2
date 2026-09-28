package team.washer.server.v2.domain.notification.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.notification.dto.request.TestPushNotificationReqDto;
import team.washer.server.v2.domain.notification.dto.response.TestPushNotificationResDto;
import team.washer.server.v2.domain.notification.service.SendTestPushNotificationService;
import team.washer.server.v2.domain.notification.support.FcmNotificationSupport;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.global.security.provider.CurrentUserProvider;
import team.washer.server.v2.global.util.DateTimeUtil;

@Slf4j
@Service
@RequiredArgsConstructor
public class SendTestPushNotificationServiceImpl implements SendTestPushNotificationService {

    private static final String DEFAULT_TITLE = "테스트 알림";
    private static final String DEFAULT_BODY = "푸시 알림이 정상적으로 수신되는지 확인합니다.";

    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final FcmNotificationSupport fcmNotificationSupport;

    /**
     * 관리자가 지정한 사용자에게 테스트 푸시 알림을 즉시 발송합니다.
     *
     * <p>
     * 알림함 오염을 막기 위해 {@code Notification} 엔티티는 저장하지 않습니다.
     * </p>
     *
     * @param reqDto
     *            발송 대상 학번과 알림 문구 (모두 선택)
     * @return 발송 대상 정보와 Firebase 메시지 ID
     */
    @Override
    @Transactional(readOnly = true)
    public TestPushNotificationResDto execute(final TestPushNotificationReqDto reqDto) {
        final Long callerId = currentUserProvider.getCurrentUserId();
        final User target = resolveTarget(reqDto.studentId(), callerId);

        final String token = target.getFcmToken();
        if (token == null || token.isBlank()) {
            throw new ExpectedException("FCM 토큰이 등록되지 않은 사용자입니다.", HttpStatus.NOT_FOUND);
        }

        final String title = resolveOrDefault(reqDto.title(), DEFAULT_TITLE);
        final String body = resolveOrDefault(reqDto.body(), DEFAULT_BODY);

        final String messageId = send(target, title, body);
        log.info("test push sent callerId={} targetId={} messageId={}", callerId, target.getId(), messageId);

        return new TestPushNotificationResDto(target.getStudentId(),
                target.getName(),
                messageId,
                DateTimeUtil.nowInKorea());
    }

    private User resolveTarget(final String studentId, final Long callerId) {
        if (studentId == null || studentId.isBlank()) {
            return userRepository.findById(callerId)
                    .orElseThrow(() -> new ExpectedException("사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        }

        return userRepository.findByStudentId(studentId)
                .orElseThrow(() -> new ExpectedException("사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
    }

    private String resolveOrDefault(final String value, final String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    private String send(final User target, final String title, final String body) {
        try {
            return fcmNotificationSupport.sendAndGetMessageId(target, title, body);
        } catch (FirebaseMessagingException e) {
            throw toExpectedException(e.getMessagingErrorCode());
        }
    }

    /**
     * FCM 오류 코드를 호출자가 원인을 구분할 수 있는 응답으로 변환합니다.
     *
     * @param errorCode
     *            FCM이 반환한 오류 코드
     * @return 오류 코드에 대응하는 예외
     */
    private ExpectedException toExpectedException(final MessagingErrorCode errorCode) {
        if (errorCode == MessagingErrorCode.UNREGISTERED) {
            return new ExpectedException("FCM 토큰이 만료되었습니다. 앱을 다시 실행해 주세요.", HttpStatus.NOT_FOUND);
        }
        if (errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
            return new ExpectedException("FCM 토큰이 유효하지 않습니다.", HttpStatus.BAD_REQUEST);
        }
        return new ExpectedException(String.format("푸시 발송에 실패했습니다. errorCode=%s", errorCode), HttpStatus.BAD_GATEWAY);
    }
}
