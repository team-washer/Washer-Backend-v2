package team.washer.server.v2.domain.user.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.user.dto.UserResponseDto;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.domain.user.service.QueryUserByIdService;
import team.washer.server.v2.global.common.error.exception.ExpectedException;

@Service
@RequiredArgsConstructor
public class QueryUserByIdServiceImpl implements QueryUserByIdService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserResponseDto getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ExpectedException("사용자를 찾을 수 없습니다", HttpStatus.NOT_FOUND));
        return UserResponseDto.from(user);
    }
}
