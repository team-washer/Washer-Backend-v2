package team.washer.server.v2.domain.user.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.user.dto.response.UserUpdateResDto;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.domain.user.service.UpdateUserInfoService;
import team.washer.server.v2.global.common.error.exception.ExpectedException;

@Service
@RequiredArgsConstructor
public class UpdateUserInfoServiceImpl implements UpdateUserInfoService {

    private final UserRepository userRepository;

    @Transactional
    @Override
    public UserUpdateResDto execute(Long userId, String roomNumber, Integer grade, Integer floor) {
        final var user = userRepository.findById(userId)
                .orElseThrow(() -> new ExpectedException("사용자를 찾을 수 없습니다", HttpStatus.NOT_FOUND));

        user.updateInfo(roomNumber, grade, floor);

        final var savedUser = userRepository.save(user);

        return new UserUpdateResDto(savedUser.getId(),
                savedUser.getName(),
                savedUser.getStudentId(),
                savedUser.getRoomNumber(),
                savedUser.getGrade(),
                savedUser.getFloor());
    }
}
