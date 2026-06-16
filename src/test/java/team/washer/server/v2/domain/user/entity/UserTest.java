package team.washer.server.v2.domain.user.entity;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import jakarta.persistence.Column;
import jakarta.validation.constraints.Size;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.user.enums.UserRole;
import team.washer.server.v2.global.common.constants.NotificationConstants;

@DisplayName("User 엔티티의")
class UserTest {

    // 2026-06-08(월), 06-09(화), 06-07(일), 06-05(금), 06-06(토)
    private User createUser(final Integer grade, final UserRole role) {
        return User.builder().name("김철수").studentId("20210001").roomNumber("301").grade(grade).floor(3).penaltyCount(0)
                .role(role).build();
    }

    @Nested
    @DisplayName("FCM 토큰 필드는")
    class Describe_fcmToken {

        @Test
        @DisplayName("긴 등록 토큰을 저장할 수 있도록 4096자까지 허용한다")
        void it_allows_long_registration_token() throws NoSuchFieldException {
            final var field = User.class.getDeclaredField("fcmToken");
            final var column = field.getAnnotation(Column.class);
            final var size = field.getAnnotation(Size.class);

            assertThat(column.length()).isEqualTo(NotificationConstants.FCM_TOKEN_MAX_LENGTH);
            assertThat(size.max()).isEqualTo(NotificationConstants.FCM_TOKEN_MAX_LENGTH);
        }
    }

    @Nested
    @DisplayName("validateTimeRestriction 메서드는")
    class Describe_validateTimeRestriction {

        @Nested
        @DisplayName("월~목요일 일반 사용자일 때 (전 학년 공통 21:20)")
        class Context_weekday_user {

            @Test
            @DisplayName("21:20 이전이면 예외를 던진다")
            void it_throws_before_2120() {
                final User user = createUser(1, UserRole.USER);
                final LocalDateTime time = LocalDateTime.of(2026, 6, 8, 21, 19);

                assertThatThrownBy(() -> user.validateTimeRestriction(time)).isInstanceOf(ExpectedException.class)
                        .hasMessageContaining("21:20");
            }

            @Test
            @DisplayName("21:20 정각이면 통과한다")
            void it_passes_at_2120() {
                final User user = createUser(1, UserRole.USER);
                final LocalDateTime time = LocalDateTime.of(2026, 6, 8, 21, 20);

                assertThatCode(() -> user.validateTimeRestriction(time)).doesNotThrowAnyException();
            }

            @Test
            @DisplayName("21:21 이후이면 통과한다")
            void it_passes_after_2120() {
                final User user = createUser(3, UserRole.USER);
                final LocalDateTime time = LocalDateTime.of(2026, 6, 9, 21, 21);

                assertThatCode(() -> user.validateTimeRestriction(time)).doesNotThrowAnyException();
            }

            @Test
            @DisplayName("학년과 무관하게 동일한 21:20 기준이 적용된다")
            void it_applies_same_time_regardless_of_grade() {
                final User grade3 = createUser(3, UserRole.USER);
                final LocalDateTime time = LocalDateTime.of(2026, 6, 8, 21, 19);

                assertThatThrownBy(() -> grade3.validateTimeRestriction(time)).isInstanceOf(ExpectedException.class)
                        .hasMessageContaining("21:20");
            }
        }

        @Nested
        @DisplayName("일요일 일반 사용자일 때 (학년별)")
        class Context_sunday_user {

            @Test
            @DisplayName("1학년은 20:00 이전이면 예외를 던진다")
            void it_throws_grade1_before_2000() {
                final User user = createUser(1, UserRole.USER);
                final LocalDateTime time = LocalDateTime.of(2026, 6, 7, 19, 59);

                assertThatThrownBy(() -> user.validateTimeRestriction(time)).isInstanceOf(ExpectedException.class)
                        .hasMessageContaining("1학년").hasMessageContaining("20:00");
            }

            @Test
            @DisplayName("1학년은 20:00 정각이면 통과한다")
            void it_passes_grade1_at_2000() {
                final User user = createUser(1, UserRole.USER);
                final LocalDateTime time = LocalDateTime.of(2026, 6, 7, 20, 0);

                assertThatCode(() -> user.validateTimeRestriction(time)).doesNotThrowAnyException();
            }

            @Test
            @DisplayName("2학년은 20:20 이전이면 예외를 던진다")
            void it_throws_grade2_before_2020() {
                final User user = createUser(2, UserRole.USER);
                final LocalDateTime time = LocalDateTime.of(2026, 6, 7, 20, 19);

                assertThatThrownBy(() -> user.validateTimeRestriction(time)).isInstanceOf(ExpectedException.class)
                        .hasMessageContaining("2학년").hasMessageContaining("20:20");
            }

            @Test
            @DisplayName("2학년은 20:20 정각이면 통과한다")
            void it_passes_grade2_at_2020() {
                final User user = createUser(2, UserRole.USER);
                final LocalDateTime time = LocalDateTime.of(2026, 6, 7, 20, 20);

                assertThatCode(() -> user.validateTimeRestriction(time)).doesNotThrowAnyException();
            }

            @Test
            @DisplayName("3학년은 20:40 이전이면 예외를 던진다")
            void it_throws_grade3_before_2040() {
                final User user = createUser(3, UserRole.USER);
                final LocalDateTime time = LocalDateTime.of(2026, 6, 7, 20, 39);

                assertThatThrownBy(() -> user.validateTimeRestriction(time)).isInstanceOf(ExpectedException.class)
                        .hasMessageContaining("3학년").hasMessageContaining("20:40");
            }

            @Test
            @DisplayName("3학년은 20:40 정각이면 통과한다")
            void it_passes_grade3_at_2040() {
                final User user = createUser(3, UserRole.USER);
                final LocalDateTime time = LocalDateTime.of(2026, 6, 7, 20, 40);

                assertThatCode(() -> user.validateTimeRestriction(time)).doesNotThrowAnyException();
            }
        }

        @Nested
        @DisplayName("금요일과 토요일에는")
        class Context_weekend {

            @Test
            @DisplayName("금요일 00:00에도 제한 없이 통과한다")
            void it_passes_friday_midnight() {
                final User user = createUser(1, UserRole.USER);
                final LocalDateTime time = LocalDateTime.of(2026, 6, 5, 0, 0);

                assertThatCode(() -> user.validateTimeRestriction(time)).doesNotThrowAnyException();
            }

            @Test
            @DisplayName("토요일 00:00에도 제한 없이 통과한다")
            void it_passes_saturday_midnight() {
                final User user = createUser(3, UserRole.USER);
                final LocalDateTime time = LocalDateTime.of(2026, 6, 6, 0, 0);

                assertThatCode(() -> user.validateTimeRestriction(time)).doesNotThrowAnyException();
            }
        }

        @Nested
        @DisplayName("자정부터 08:00 이전 새벽 시간대에는")
        class Context_before_restriction_start {

            @Test
            @DisplayName("월요일 00:00이면 제한 없이 통과한다")
            void it_passes_weekday_midnight() {
                final User user = createUser(1, UserRole.USER);
                final LocalDateTime time = LocalDateTime.of(2026, 6, 8, 0, 0);

                assertThatCode(() -> user.validateTimeRestriction(time)).doesNotThrowAnyException();
            }

            @Test
            @DisplayName("월요일 07:59이면 제한 없이 통과한다")
            void it_passes_weekday_before_0800() {
                final User user = createUser(1, UserRole.USER);
                final LocalDateTime time = LocalDateTime.of(2026, 6, 8, 7, 59);

                assertThatCode(() -> user.validateTimeRestriction(time)).doesNotThrowAnyException();
            }

            @Test
            @DisplayName("일요일 07:59이면 제한 없이 통과한다")
            void it_passes_sunday_before_0800() {
                final User user = createUser(1, UserRole.USER);
                final LocalDateTime time = LocalDateTime.of(2026, 6, 7, 7, 59);

                assertThatCode(() -> user.validateTimeRestriction(time)).doesNotThrowAnyException();
            }

            @Test
            @DisplayName("월요일 08:00 정각부터는 제한이 적용되어 예외를 던진다")
            void it_throws_weekday_at_0800() {
                final User user = createUser(1, UserRole.USER);
                final LocalDateTime time = LocalDateTime.of(2026, 6, 8, 8, 0);

                assertThatThrownBy(() -> user.validateTimeRestriction(time)).isInstanceOf(ExpectedException.class)
                        .hasMessageContaining("21:20");
            }
        }

        @Nested
        @DisplayName("기숙사자치위원회와 관리자는")
        class Context_bypass {

            @Test
            @DisplayName("기숙사자치위원회는 월요일 제한 시각 이전에도 통과한다")
            void it_bypasses_for_dormitory_council() {
                final User user = createUser(1, UserRole.DORMITORY_COUNCIL);
                final LocalDateTime time = LocalDateTime.of(2026, 6, 8, 0, 0);

                assertThatCode(() -> user.validateTimeRestriction(time)).doesNotThrowAnyException();
            }

            @Test
            @DisplayName("관리자는 일요일 제한 시각 이전에도 통과한다")
            void it_bypasses_for_admin() {
                final User user = createUser(3, UserRole.ADMIN);
                final LocalDateTime time = LocalDateTime.of(2026, 6, 7, 0, 0);

                assertThatCode(() -> user.validateTimeRestriction(time)).doesNotThrowAnyException();
            }
        }
    }
}
