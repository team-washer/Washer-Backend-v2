package team.washer.server.v2.domain.user.support;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import team.themoment.datagsm.sdk.oauth.model.Student;
import team.themoment.datagsm.sdk.oauth.model.StudentRole;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.enums.UserRole;
import team.washer.server.v2.domain.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserRegistrationSupport 클래스의")
class UserRegistrationSupportTest {

    @InjectMocks
    private UserRegistrationSupport userRegistrationSupport;

    @Mock
    private UserRepository userRepository;

    private Student createStudent(final StudentRole role) {
        final Student student = new Student();
        student.setStudentNumber(20210001);
        student.setName("김철수");
        student.setDormitoryRoom(301);
        student.setGrade(3);
        student.setDormitoryFloor(3);
        student.setRole(role);
        return student;
    }

    @Nested
    @DisplayName("register 메서드는")
    class Describe_register {

        @Test
        @DisplayName("DORMITORY_MANAGER 학생은 기숙사자치위원회로 등록한다")
        void it_maps_dormitory_manager_to_council() {
            final User user = userRegistrationSupport.register(createStudent(StudentRole.DORMITORY_MANAGER));

            assertThat(user.getRole()).isEqualTo(UserRole.DORMITORY_COUNCIL);
        }

        @Test
        @DisplayName("STUDENT_COUNCIL 학생은 일반 사용자로 등록한다")
        void it_maps_student_council_to_user() {
            final User user = userRegistrationSupport.register(createStudent(StudentRole.STUDENT_COUNCIL));

            assertThat(user.getRole()).isEqualTo(UserRole.USER);
        }

        @Test
        @DisplayName("GENERAL_STUDENT 학생은 일반 사용자로 등록한다")
        void it_maps_general_student_to_user() {
            final User user = userRegistrationSupport.register(createStudent(StudentRole.GENERAL_STUDENT));

            assertThat(user.getRole()).isEqualTo(UserRole.USER);
        }

        @Test
        @DisplayName("역할 정보가 없으면 일반 사용자로 등록한다")
        void it_maps_null_role_to_user() {
            final User user = userRegistrationSupport.register(createStudent(null));

            assertThat(user.getRole()).isEqualTo(UserRole.USER);
        }
    }
}
