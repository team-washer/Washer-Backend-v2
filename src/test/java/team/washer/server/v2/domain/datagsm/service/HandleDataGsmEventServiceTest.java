package team.washer.server.v2.domain.datagsm.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import team.washer.server.v2.domain.datagsm.service.impl.HandleDataGsmEventServiceImpl;
import team.washer.server.v2.domain.datagsm.support.DataGsmEventIdempotencySupport;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.enums.UserRole;
import team.washer.server.v2.domain.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("HandleDataGsmEventService 클래스는")
class HandleDataGsmEventServiceTest {

    @InjectMocks
    private HandleDataGsmEventServiceImpl handleDataGsmEventService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DataGsmEventIdempotencySupport idempotencySupport;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    @DisplayName("student.updated 이벤트가 들어오면")
    class Describe_student_updated_event {

        @Test
        @DisplayName("기존 사용자 정보를 DataGSM 변경 정보로 갱신한다")
        void it_updates_existing_user() {
            // Given
            handleDataGsmEventService = new HandleDataGsmEventServiceImpl(objectMapper,
                    userRepository,
                    idempotencySupport);
            final var user = createUser();
            final byte[] rawBody = eventPayload("""
                    {
                      "student_id": "20210001",
                      "name": "김세탁",
                      "dormitory_room": 401,
                      "grade": 3,
                      "dormitory_floor": 4,
                      "role": "DORMITORY_MANAGER"
                    }
                    """);
            given(idempotencySupport.isProcessed("evt_student_1")).willReturn(false);
            given(userRepository.findByStudentId("20210001")).willReturn(Optional.of(user));

            // When
            handleDataGsmEventService.execute(rawBody);

            // Then
            assertThat(user.getName()).isEqualTo("김세탁");
            assertThat(user.getRoomNumber()).isEqualTo("401");
            assertThat(user.getGrade()).isEqualTo(3);
            assertThat(user.getFloor()).isEqualTo(4);
            assertThat(user.getRole()).isEqualTo(UserRole.DORMITORY_COUNCIL);
            then(idempotencySupport).should(times(1)).markProcessed("evt_student_1");
        }

        @Test
        @DisplayName("이미 처리한 이벤트이면 사용자 정보를 조회하지 않는다")
        void it_skips_when_event_already_processed() {
            // Given
            handleDataGsmEventService = new HandleDataGsmEventServiceImpl(objectMapper,
                    userRepository,
                    idempotencySupport);
            final byte[] rawBody = eventPayload("""
                    {
                      "student_id": "20210001",
                      "name": "김세탁"
                    }
                    """);
            given(idempotencySupport.isProcessed("evt_student_1")).willReturn(true);

            // When
            handleDataGsmEventService.execute(rawBody);

            // Then
            then(userRepository).shouldHaveNoInteractions();
            then(idempotencySupport).should(never()).markProcessed(anyString());
        }

        @Test
        @DisplayName("기존 사용자가 없으면 생성하지 않고 처리 완료로 기록한다")
        void it_does_not_create_user_when_user_does_not_exist() {
            // Given
            handleDataGsmEventService = new HandleDataGsmEventServiceImpl(objectMapper,
                    userRepository,
                    idempotencySupport);
            final byte[] rawBody = eventPayload("""
                    {
                      "student_id": "20210001",
                      "name": "김세탁"
                    }
                    """);
            given(idempotencySupport.isProcessed("evt_student_1")).willReturn(false);
            given(userRepository.findByStudentId("20210001")).willReturn(Optional.empty());

            // When
            handleDataGsmEventService.execute(rawBody);

            // Then
            then(userRepository).should(never()).save(any(User.class));
            then(idempotencySupport).should(times(1)).markProcessed("evt_student_1");
        }
    }

    @Nested
    @DisplayName("지원하지 않는 이벤트가 들어오면")
    class Describe_unsupported_event {

        @Test
        @DisplayName("도메인 처리를 하지 않고 처리 완료로 기록한다")
        void it_marks_processed_without_domain_handling() {
            // Given
            handleDataGsmEventService = new HandleDataGsmEventServiceImpl(objectMapper,
                    userRepository,
                    idempotencySupport);
            final byte[] rawBody = """
                    {
                      "id": "evt_club_1",
                      "event": "club.updated",
                      "timestamp": "2026-06-23T05:21:48.123Z",
                      "data": { "old": [], "new": [] }
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            given(idempotencySupport.isProcessed("evt_club_1")).willReturn(false);

            // When
            handleDataGsmEventService.execute(rawBody);

            // Then
            then(userRepository).shouldHaveNoInteractions();
            then(idempotencySupport).should(times(1)).markProcessed("evt_club_1");
        }
    }

    @Nested
    @DisplayName("필수 필드가 없으면")
    class Describe_invalid_event {

        @Test
        @DisplayName("예외를 발생시킨다")
        void it_throws_exception() {
            // Given
            handleDataGsmEventService = new HandleDataGsmEventServiceImpl(objectMapper,
                    userRepository,
                    idempotencySupport);
            final byte[] rawBody = """
                    {
                      "event": "student.updated",
                      "data": { "old": [], "new": [] }
                    }
                    """.getBytes(StandardCharsets.UTF_8);

            // When & Then
            assertThatThrownBy(() -> handleDataGsmEventService.execute(rawBody))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private byte[] eventPayload(String studentObject) {
        return """
                {
                  "id": "evt_student_1",
                  "event": "student.updated",
                  "timestamp": "2026-06-23T05:21:48.123Z",
                  "data": {
                    "old": [{ "index": 0, "object": {} }],
                    "new": [{ "index": 0, "object": %s }]
                  }
                }
                """.formatted(studentObject).getBytes(StandardCharsets.UTF_8);
    }

    private User createUser() {
        return User.builder().name("김기존").studentId("20210001").roomNumber("301").grade(2).floor(3).role(UserRole.USER)
                .build();
    }
}
