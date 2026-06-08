package team.washer.server.v2.domain.user.listener;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.user.service.SyncDormitoryCouncilRoleService;

/**
 * 애플리케이션 기동 완료 시 기숙사자치위원회 권한을 1회 동기화하는 백필 리스너.
 * <p>
 * 회원가입 시점의 DataGSM 역할 매핑이 도입되기 전에 가입해 일반 사용자로 등록된 기존 기자위 학생을 보정하기 위한 일회성 코드입니다.
 * 멱등하게 동작하므로 재기동마다 안전하게 재실행되며, 운영 환경에서 한 번 정상 실행된 이후에는 제거해야 합니다.
 *
 * @deprecated 일회성 백필 용도이므로 운영 1회 실행 후 클래스 전체를 삭제할 것
 */
@Deprecated
@Component
@RequiredArgsConstructor
@Slf4j
public class DormitoryCouncilRoleSyncListener {

    private final SyncDormitoryCouncilRoleService syncDormitoryCouncilRoleService;

    @EventListener(ApplicationReadyEvent.class)
    public void syncDormitoryCouncilRoleOnStartup() {
        try {
            final int promoted = syncDormitoryCouncilRoleService.execute();
            log.info("startup dormitory council role sync done promoted={}", promoted);
        } catch (Exception e) {
            log.error("startup dormitory council role sync failed", e);
        }
    }
}
