package team.washer.server.v2.domain.notification.support;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;

import team.washer.server.v2.domain.notification.service.DeleteFcmTokenService;
import team.washer.server.v2.domain.user.entity.User;

@ExtendWith(MockitoExtension.class)
@DisplayName("FcmNotificationSupport 클래스는")
class FcmNotificationSupportTest {

    @InjectMocks
    private FcmNotificationSupport fcmNotificationSupport;

    @Mock
    private FirebaseMessaging firebaseMessaging;

    @Mock
    private DeleteFcmTokenService deleteFcmTokenService;

    private User createUserWithToken() {
        final var user = User.builder().name("김철수").studentId("20210001").roomNumber("301").grade(3).floor(3).build();
        user.updateFcmToken("fcm-token");
        return user;
    }

    @Nested
    @DisplayName("send 메서드는")
    class Describe_send {

        @Test
        @DisplayName("제목이 null이어도 예외 없이 전송해야 한다")
        void it_omits_null_title_data() throws Exception {
            // Given
            User user = createUserWithToken();
            given(firebaseMessaging.send(any(Message.class))).willReturn("message-id");

            // When & Then
            assertThatCode(() -> fcmNotificationSupport.send(user, null, "본문")).doesNotThrowAnyException();
            then(firebaseMessaging).should(times(1)).send(any(Message.class));
        }

        @Test
        @DisplayName("본문이 null이어도 예외 없이 전송해야 한다")
        void it_omits_null_body_data() throws Exception {
            // Given
            User user = createUserWithToken();
            given(firebaseMessaging.send(any(Message.class))).willReturn("message-id");

            // When & Then
            assertThatCode(() -> fcmNotificationSupport.send(user, "제목", null)).doesNotThrowAnyException();
            then(firebaseMessaging).should(times(1)).send(any(Message.class));
        }
    }
}
