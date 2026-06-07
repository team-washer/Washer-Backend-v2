package team.washer.server.v2.domain.user.entity;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.user.enums.UserRole;

@DisplayName("User 엔티티의")
class UserEntityTest {

    private User createUser(final int grade, final UserRole role) {
        return User.builder().name("김테스트").studentId("20210001").roomNumber("301").grade(grade).floor(3).role(role)
                .build();
    }

    @Nested
    @DisplayName("validateTimeRestriction 메서드는")
    class Describe_validateTimeRestriction {

        @Nested
        @DisplayName("자정부터 08:00 이전 시간대이면")
        class Context_before_restriction_start_time {

            @Test
            @DisplayName("1학년이라도 예외 없이 통과한다")
            void it_passes_without_exception_for_grade1() {
                // Given
                var user = createUser(1, UserRole.USER);
                var reservationTime = LocalDateTime.of(2023, 10, 29, 7, 59); // 일요일 07:59

                // When & Then
                assertThatCode(() -> user.validateTimeRestriction(reservationTime)).doesNotThrowAnyException();
            }

            @Test
            @DisplayName("자정(00:00)에도 예외 없이 통과한다")
            void it_passes_at_midnight() {
                // Given
                var user = createUser(1, UserRole.USER);
                var reservationTime = LocalDateTime.of(2023, 10, 23, 0, 0); // 월요일 00:00

                // When & Then
                assertThatCode(() -> user.validateTimeRestriction(reservationTime)).doesNotThrowAnyException();
            }
        }

        @Nested
        @DisplayName("08:00 이후이고 학년별 시작 시간 이전이면")
        class Context_after_restriction_start_but_before_grade_time {

            @Test
            @DisplayName("1학년 사용자에게 예외를 발생시킨다")
            void it_throws_exception_for_grade1() {
                // Given
                var user = createUser(1, UserRole.USER);
                var reservationTime = LocalDateTime.of(2023, 10, 23, 19, 49); // 월요일 19:49 (1학년 시작: 19:50)

                // When & Then
                assertThatThrownBy(() -> user.validateTimeRestriction(reservationTime))
                        .isInstanceOf(ExpectedException.class);
            }

            @Test
            @DisplayName("2학년 사용자에게 예외를 발생시킨다")
            void it_throws_exception_for_grade2() {
                // Given
                var user = createUser(2, UserRole.USER);
                var reservationTime = LocalDateTime.of(2023, 10, 23, 20, 9); // 월요일 20:09 (2학년 시작: 20:10)

                // When & Then
                assertThatThrownBy(() -> user.validateTimeRestriction(reservationTime))
                        .isInstanceOf(ExpectedException.class);
            }

            @Test
            @DisplayName("3학년 이상 사용자에게 예외를 발생시킨다")
            void it_throws_exception_for_grade3() {
                // Given
                var user = createUser(3, UserRole.USER);
                var reservationTime = LocalDateTime.of(2023, 10, 23, 20, 29); // 월요일 20:29 (3학년 시작: 20:30)

                // When & Then
                assertThatThrownBy(() -> user.validateTimeRestriction(reservationTime))
                        .isInstanceOf(ExpectedException.class);
            }
        }

        @Nested
        @DisplayName("학년별 시작 시간 이후이면")
        class Context_after_grade_start_time {

            @Test
            @DisplayName("예외 없이 통과한다")
            void it_passes_after_grade_start_time() {
                // Given
                var user = createUser(1, UserRole.USER);
                var reservationTime = LocalDateTime.of(2023, 10, 23, 19, 50); // 월요일 19:50 (1학년 시작: 19:50)

                // When & Then
                assertThatCode(() -> user.validateTimeRestriction(reservationTime)).doesNotThrowAnyException();
            }
        }

        @Nested
        @DisplayName("금요일 또는 토요일이면")
        class Context_on_weekend {

            @Test
            @DisplayName("어떤 시간이든 예외 없이 통과한다")
            void it_passes_on_friday() {
                // Given
                var user = createUser(1, UserRole.USER);
                var reservationTime = LocalDateTime.of(2023, 10, 27, 8, 0); // 금요일 08:00

                // When & Then
                assertThatCode(() -> user.validateTimeRestriction(reservationTime)).doesNotThrowAnyException();
            }

            @Test
            @DisplayName("토요일에도 예외 없이 통과한다")
            void it_passes_on_saturday() {
                // Given
                var user = createUser(1, UserRole.USER);
                var reservationTime = LocalDateTime.of(2023, 10, 28, 8, 0); // 토요일 08:00

                // When & Then
                assertThatCode(() -> user.validateTimeRestriction(reservationTime)).doesNotThrowAnyException();
            }
        }

        @Nested
        @DisplayName("기숙사자치위원회 또는 관리자 역할이면")
        class Context_with_bypass_role {

            @Test
            @DisplayName("기숙사자치위원회는 시간 제한 없이 통과한다")
            void it_passes_for_dormitory_council() {
                // Given
                var user = createUser(1, UserRole.DORMITORY_COUNCIL);
                var reservationTime = LocalDateTime.of(2023, 10, 23, 8, 0); // 월요일 08:00

                // When & Then
                assertThatCode(() -> user.validateTimeRestriction(reservationTime)).doesNotThrowAnyException();
            }

            @Test
            @DisplayName("관리자는 시간 제한 없이 통과한다")
            void it_passes_for_admin() {
                // Given
                var user = createUser(1, UserRole.ADMIN);
                var reservationTime = LocalDateTime.of(2023, 10, 23, 8, 0); // 월요일 08:00

                // When & Then
                assertThatCode(() -> user.validateTimeRestriction(reservationTime)).doesNotThrowAnyException();
            }
        }
    }
}
