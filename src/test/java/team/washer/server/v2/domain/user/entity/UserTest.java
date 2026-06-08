package team.washer.server.v2.domain.user.entity;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.user.enums.UserRole;

@DisplayName("User 엔티티의")
class UserTest {

    // 2026-06-08(월), 06-09(화), 06-07(일), 06-05(금), 06-06(토)
    private User createUser(final Integer grade, final UserRole role) {
        return User.builder().name("김철수").studentId("20210001").roomNumber("301").grade(grade).floor(3).penaltyCount(0)
                .role(role).build();
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

    @Nested
    @DisplayName("promoteToDormitoryCouncil 메서드는")
    class Describe_promoteToDormitoryCouncil {

        @Test
        @DisplayName("일반 사용자를 기숙사자치위원회로 승격하고 true를 반환한다")
        void it_promotes_user() {
            final User user = createUser(3, UserRole.USER);

            final boolean promoted = user.promoteToDormitoryCouncil();

            assertThat(promoted).isTrue();
            assertThat(user.getRole()).isEqualTo(UserRole.DORMITORY_COUNCIL);
        }

        @Test
        @DisplayName("이미 기숙사자치위원회면 변경 없이 false를 반환한다")
        void it_keeps_council() {
            final User user = createUser(3, UserRole.DORMITORY_COUNCIL);

            final boolean promoted = user.promoteToDormitoryCouncil();

            assertThat(promoted).isFalse();
            assertThat(user.getRole()).isEqualTo(UserRole.DORMITORY_COUNCIL);
        }

        @Test
        @DisplayName("관리자는 강등 없이 false를 반환한다")
        void it_keeps_admin() {
            final User user = createUser(3, UserRole.ADMIN);

            final boolean promoted = user.promoteToDormitoryCouncil();

            assertThat(promoted).isFalse();
            assertThat(user.getRole()).isEqualTo(UserRole.ADMIN);
        }
    }
}
