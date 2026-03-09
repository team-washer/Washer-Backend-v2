package team.washer.server.v2.domain.user.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.themoment.datagsm.sdk.oauth.model.Student;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.domain.user.service.SignUpService;

@Service
@RequiredArgsConstructor
public class SignUpServiceImpl implements SignUpService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    /*
    * 메서드를 호출하기전 중복된 User가 없음을 보장해야합니다.
     */
    public User execute(Student student) {
        User user = User.builder().studentId(student.getStudentNumber().toString()).name(student.getName())
                .roomNumber(student.getDormitoryRoom().toString()).grade(student.getGrade())
                .floor(student.getDormitoryFloor()).build();
        userRepository.save(user);
        return user;
    }
}
