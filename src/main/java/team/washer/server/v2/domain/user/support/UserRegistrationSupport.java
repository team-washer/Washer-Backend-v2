package team.washer.server.v2.domain.user.support;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.themoment.datagsm.sdk.oauth.model.Student;
import team.washer.server.v2.domain.user.entity.User;
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
                .floor(student.getDormitoryFloor()).build();
        userRepository.save(user);
        return user;
    }
}
