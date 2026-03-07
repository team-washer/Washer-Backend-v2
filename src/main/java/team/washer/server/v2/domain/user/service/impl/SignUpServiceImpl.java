package team.washer.server.v2.domain.user.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.themoment.datagsm.sdk.oauth.model.Student;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.domain.user.service.SignUpService;
import team.washer.server.v2.global.common.error.exception.ExpectedException;

@Service
@RequiredArgsConstructor
public class SignUpServiceImpl implements SignUpService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public User execute(Student student) {
        if (userRepository.findByStudentId(student.getStudentNumber().toString()).isPresent()) {
            throw new ExpectedException("이미 가입된 사용자입니다.", HttpStatus.BAD_REQUEST);
        }
        User user = User.builder().studentId(student.getStudentNumber().toString())
                .name(student.getName())
                .roomNumber(student.getDormitoryRoom().toString()).grade(student.getGrade())
                .floor(student.getDormitoryFloor()).build();
        userRepository.save(user);
        return user;
    }
}
