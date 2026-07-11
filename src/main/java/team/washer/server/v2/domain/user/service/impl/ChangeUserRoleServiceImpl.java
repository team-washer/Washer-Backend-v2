package team.washer.server.v2.domain.user.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.user.dto.response.UserRoleUpdateResDto;
import team.washer.server.v2.domain.user.enums.UserRole;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.domain.user.service.ChangeUserRoleService;
import team.washer.server.v2.global.security.provider.CurrentUserProvider;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChangeUserRoleServiceImpl implements ChangeUserRoleService {

    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional
    public UserRoleUpdateResDto execute(final Long targetUserId, final UserRole newRole) {
        final var actorId = currentUserProvider.getCurrentUserId();
        final var actor = userRepository.findById(actorId)
                .orElseThrow(() -> new ExpectedException("사용자를 찾을 수 없습니다", HttpStatus.NOT_FOUND));

        if (!actor.getRole().isAdmin()) {
            throw new ExpectedException("권한을 변경할 수 있는 관리자가 아닙니다.", HttpStatus.FORBIDDEN);
        }

        if (actorId.equals(targetUserId)) {
            throw new ExpectedException("자신의 권한은 변경할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }

        final var target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ExpectedException("사용자를 찾을 수 없습니다", HttpStatus.NOT_FOUND));

        final var currentRole = target.getRole();
        if (currentRole == newRole) {
            throw new ExpectedException("이미 해당 권한을 가진 사용자입니다.", HttpStatus.BAD_REQUEST);
        }

        if (currentRole == UserRole.ADMIN && newRole != UserRole.ADMIN
                && userRepository.countByRole(UserRole.ADMIN) <= 1) {
            throw new ExpectedException("마지막 관리자의 권한은 변경할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }

        target.changeRole(newRole);
        userRepository.save(target);

        log.info("user role changed actorId={} targetId={} from={} to={}", actorId, targetUserId, currentRole, newRole);

        return new UserRoleUpdateResDto(target.getId(), target.getName(), target.getStudentId(), target.getRole());
    }
}
