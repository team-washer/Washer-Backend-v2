package team.washer.server.v2.domain.user.service;

// TODO: 현재 유일한 호출부는 일회성 백필 리스너(DormitoryCouncilRoleSyncListener, @Deprecated)다.
// 리스너 제거 시 이 서비스(및 DataGsmOpenApiClient)도 함께 삭제하거나 관리자용 수동 동기화 엔드포인트로 재구성할 것.
public interface SyncDormitoryCouncilRoleService {

    /**
     * DataGSM OpenAPI에서 기숙사 관리(DORMITORY_MANAGER) 역할 학생을 조회해 일치하는 일반 사용자를 기숙사자치위원회로
     * 승격합니다.
     *
     * @return 실제로 승격된 사용자 수
     */
    int execute();
}
