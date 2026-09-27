package team.washer.server.v2.domain.notification.service;

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

import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;

import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.notification.dto.request.TestPushNotificationReqDto;
import team.washer.server.v2.domain.notification.service.impl.SendTestPushNotificationServiceImpl;
import team.washer.server.v2.domain.notification.support.FcmNotificationSupport;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.global.security.provider.CurrentUserProvider;

@ExtendWith(MockitoExtension.class)
@DisplayName("SendTestPushNotificationServiceImpl 클래스의")
class SendTestPushNotificationServiceTest {

    @InjectMocks
    private SendTestPushNotificationServiceImpl sendTestPushNotificationService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private FcmNotificationSupport fcmNotificationSupport;

    private static final Long CALLER_ID = 1L;
    private static final String TARGET_STUDENT_ID = "2108";
    private static final String MESSAGE_ID = "projects/washer/messages/0:1234567890";
    private static final String DEFAULT_TITLE = "테스트 알림";
    private static final String DEFAULT_BODY = "푸시 알림이 정상적으로 수신되는지 확인합니다.";

    private User createUserWithToken(final String studentId, final String name) {
        final var user = User.builder().name(name).studentId(studentId).roomNumber("301").grade(3).floor(3).build();
        user.updateFcmToken("fcm-token");
        return user;
    }

    private User createUserWithoutToken() {
        return User.builder().name("김철수").studentId(TARGET_STUDENT_ID).roomNumber("301").grade(3).floor(3).build();
    }

    private FirebaseMessagingException firebaseException(final MessagingErrorCode errorCode) {
        final var exception = mock(FirebaseMessagingException.class);
        given(exception.getMessagingErrorCode()).willReturn(errorCode);
        return exception;
    }

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Nested
        @DisplayName("발송 대상을 지정할 때")
        class Context_with_target_resolution {

            @Test
            @DisplayName("학번을 지정하면 해당 사용자에게 발송해야 한다")
            void it_sends_to_user_of_given_student_id() throws Exception {
                // Given
                final User target = createUserWithToken(TARGET_STUDENT_ID, "홍길동");
                given(currentUserProvider.getCurrentUserId()).willReturn(CALLER_ID);
                given(userRepository.findByStudentId(TARGET_STUDENT_ID)).willReturn(Optional.of(target));
                given(fcmNotificationSupport.sendAndGetMessageId(target, "제목", "본문")).willReturn(MESSAGE_ID);

                // When
                final var result = sendTestPushNotificationService
                        .execute(new TestPushNotificationReqDto(TARGET_STUDENT_ID, "제목", "본문"));

                // Then
                assertThat(result.studentId()).isEqualTo(TARGET_STUDENT_ID);
                assertThat(result.name()).isEqualTo("홍길동");
                assertThat(result.messageId()).isEqualTo(MESSAGE_ID);
                assertThat(result.sentAt()).isNotNull();
                then(userRepository).should(never()).findById(any());
            }

            @Test
            @DisplayName("학번을 생략하면 요청한 관리자 본인에게 발송해야 한다")
            void it_sends_to_caller_when_student_id_is_omitted() throws Exception {
                // Given
                final User caller = createUserWithToken("20210001", "관리자");
                given(currentUserProvider.getCurrentUserId()).willReturn(CALLER_ID);
                given(userRepository.findById(CALLER_ID)).willReturn(Optional.of(caller));
                given(fcmNotificationSupport.sendAndGetMessageId(eq(caller), any(), any())).willReturn(MESSAGE_ID);

                // When
                final var result = sendTestPushNotificationService
                        .execute(new TestPushNotificationReqDto(null, null, null));

                // Then
                assertThat(result.studentId()).isEqualTo("20210001");
                assertThat(result.name()).isEqualTo("관리자");
                then(userRepository).should(never()).findByStudentId(any());
            }

            @Test
            @DisplayName("존재하지 않는 학번이면 404 예외를 던져야 한다")
            void it_throws_not_found_for_nonexistent_student_id() {
                // Given
                given(currentUserProvider.getCurrentUserId()).willReturn(CALLER_ID);
                given(userRepository.findByStudentId(TARGET_STUDENT_ID)).willReturn(Optional.empty());

                // When & Then
                assertThatThrownBy(() -> sendTestPushNotificationService
                        .execute(new TestPushNotificationReqDto(TARGET_STUDENT_ID, null, null)))
                        .isInstanceOf(ExpectedException.class).hasMessage("사용자를 찾을 수 없습니다.")
                        .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
            }
        }

        @Nested
        @DisplayName("발송에 실패할 때")
        class Context_with_send_failure {

            @Test
            @DisplayName("FCM 토큰이 등록되지 않았으면 404 예외를 던지고 발송을 시도하지 않아야 한다")
            void it_throws_not_found_when_fcm_token_is_missing() throws Exception {
                // Given
                given(currentUserProvider.getCurrentUserId()).willReturn(CALLER_ID);
                given(userRepository.findByStudentId(TARGET_STUDENT_ID))
                        .willReturn(Optional.of(createUserWithoutToken()));

                // When & Then
                assertThatThrownBy(() -> sendTestPushNotificationService
                        .execute(new TestPushNotificationReqDto(TARGET_STUDENT_ID, null, null)))
                        .isInstanceOf(ExpectedException.class).hasMessage("FCM 토큰이 등록되지 않은 사용자입니다.")
                        .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);

                then(fcmNotificationSupport).should(never()).sendAndGetMessageId(any(), any(), any());
            }

            @Test
            @DisplayName("토큰이 만료되었으면 404 예외를 던져야 한다")
            void it_throws_not_found_when_token_is_unregistered() throws Exception {
                // Given
                final User target = createUserWithToken(TARGET_STUDENT_ID, "홍길동");
                final FirebaseMessagingException exception = firebaseException(MessagingErrorCode.UNREGISTERED);
                given(currentUserProvider.getCurrentUserId()).willReturn(CALLER_ID);
                given(userRepository.findByStudentId(TARGET_STUDENT_ID)).willReturn(Optional.of(target));
                given(fcmNotificationSupport.sendAndGetMessageId(eq(target), any(), any())).willThrow(exception);

                // When & Then
                assertThatThrownBy(() -> sendTestPushNotificationService
                        .execute(new TestPushNotificationReqDto(TARGET_STUDENT_ID, null, null)))
                        .isInstanceOf(ExpectedException.class).hasMessage("FCM 토큰이 만료되었습니다. 앱을 다시 실행해 주세요.")
                        .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
            }

            @Test
            @DisplayName("토큰이 유효하지 않으면 400 예외를 던져야 한다")
            void it_throws_bad_request_when_token_is_invalid() throws Exception {
                // Given
                final User target = createUserWithToken(TARGET_STUDENT_ID, "홍길동");
                final FirebaseMessagingException exception = firebaseException(MessagingErrorCode.INVALID_ARGUMENT);
                given(currentUserProvider.getCurrentUserId()).willReturn(CALLER_ID);
                given(userRepository.findByStudentId(TARGET_STUDENT_ID)).willReturn(Optional.of(target));
                given(fcmNotificationSupport.sendAndGetMessageId(eq(target), any(), any())).willThrow(exception);

                // When & Then
                assertThatThrownBy(() -> sendTestPushNotificationService
                        .execute(new TestPushNotificationReqDto(TARGET_STUDENT_ID, null, null)))
                        .isInstanceOf(ExpectedException.class).hasMessage("FCM 토큰이 유효하지 않습니다.")
                        .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);
            }

            @Test
            @DisplayName("그 외 FCM 오류이면 오류 코드를 담은 502 예외를 던져야 한다")
            void it_throws_bad_gateway_for_other_errors() throws Exception {
                // Given
                final User target = createUserWithToken(TARGET_STUDENT_ID, "홍길동");
                final FirebaseMessagingException exception = firebaseException(MessagingErrorCode.UNAVAILABLE);
                given(currentUserProvider.getCurrentUserId()).willReturn(CALLER_ID);
                given(userRepository.findByStudentId(TARGET_STUDENT_ID)).willReturn(Optional.of(target));
                given(fcmNotificationSupport.sendAndGetMessageId(eq(target), any(), any())).willThrow(exception);

                // When & Then
                assertThatThrownBy(() -> sendTestPushNotificationService
                        .execute(new TestPushNotificationReqDto(TARGET_STUDENT_ID, null, null)))
                        .isInstanceOf(ExpectedException.class).hasMessage("푸시 발송에 실패했습니다. errorCode=UNAVAILABLE")
                        .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_GATEWAY);
            }
        }

        @Nested
        @DisplayName("알림 문구를 생략할 때")
        class Context_with_omitted_message {

            @Test
            @DisplayName("제목과 본문 모두 기본 문구를 사용해야 한다")
            void it_uses_default_title_and_body() throws Exception {
                // Given
                final User target = createUserWithToken(TARGET_STUDENT_ID, "홍길동");
                given(currentUserProvider.getCurrentUserId()).willReturn(CALLER_ID);
                given(userRepository.findByStudentId(TARGET_STUDENT_ID)).willReturn(Optional.of(target));
                given(fcmNotificationSupport.sendAndGetMessageId(target, DEFAULT_TITLE, DEFAULT_BODY))
                        .willReturn(MESSAGE_ID);

                // When
                final var result = sendTestPushNotificationService
                        .execute(new TestPushNotificationReqDto(TARGET_STUDENT_ID, null, null));

                // Then
                assertThat(result.messageId()).isEqualTo(MESSAGE_ID);
                then(fcmNotificationSupport).should(times(1)).sendAndGetMessageId(target, DEFAULT_TITLE, DEFAULT_BODY);
            }

            @Test
            @DisplayName("공백 문구는 기본 문구로 대체해야 한다")
            void it_replaces_blank_message_with_default() throws Exception {
                // Given
                final User target = createUserWithToken(TARGET_STUDENT_ID, "홍길동");
                given(currentUserProvider.getCurrentUserId()).willReturn(CALLER_ID);
                given(userRepository.findByStudentId(TARGET_STUDENT_ID)).willReturn(Optional.of(target));
                given(fcmNotificationSupport.sendAndGetMessageId(target, DEFAULT_TITLE, DEFAULT_BODY))
                        .willReturn(MESSAGE_ID);

                // When
                sendTestPushNotificationService
                        .execute(new TestPushNotificationReqDto(TARGET_STUDENT_ID, "   ", "   "));

                // Then
                then(fcmNotificationSupport).should(times(1)).sendAndGetMessageId(target, DEFAULT_TITLE, DEFAULT_BODY);
            }
        }
    }
}
