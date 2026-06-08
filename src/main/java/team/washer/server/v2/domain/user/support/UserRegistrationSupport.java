package team.washer.server.v2.domain.user.support;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.themoment.datagsm.sdk.oauth.model.Student;
import team.themoment.datagsm.sdk.oauth.model.StudentRole;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.enums.UserRole;
import team.washer.server.v2.domain.user.repository.UserRepository;

/**
 * 신규 사용자 등록을 담당하는 지원 컴포넌트.
 */
@Component
@RequiredArgsConstructor
public class UserRegistrationSupport {

    private final UserRepository userRepository;

    /**
     * 신규 사용자를 등록한다. 메서드를 호출하기 전 중복된 User가 없음을 보장해야 합니다.
     */
    @Transactional
    public User register(Student student) {
        User user = User.builder().studentId(student.getStudentNumber().toString()).name(student.getName())
                .roomNumber(student.getDormitoryRoom().toString()).grade(student.getGrade())
                .floor(student.getDormitoryFloor()).role(mapRole(student.getRole())).build();
        userRepository.save(user);
        return user;
    }

    /**
     * DataGSM 학생 역할을 서비스 사용자 권한으로 변환합니다. 기숙사 관리는 기숙사자치위원회로 매핑되며, 학생회와 일반 학생 및 역할
     * 미상은 일반 사용자로 매핑됩니다. 관리자 권한은 DataGSM에서 부여되지 않으며 별도로 지정합니다.
     *
     * @param role
     *            DataGSM 학생 역할(null 가능)
     * @return 매핑된 서비스 사용자 권한
     */
    private UserRole mapRole(final StudentRole role) {
        if (role == null) {
            return UserRole.USER;
        }
        return switch (role) {
            case DORMITORY_MANAGER -> UserRole.DORMITORY_COUNCIL;
            default -> UserRole.USER;
        };
    }
}
