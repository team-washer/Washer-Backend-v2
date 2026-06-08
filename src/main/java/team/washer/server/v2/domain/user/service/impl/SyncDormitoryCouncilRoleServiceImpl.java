package team.washer.server.v2.domain.user.service.impl;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.enums.UserRole;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.domain.user.service.SyncDormitoryCouncilRoleService;
import team.washer.server.v2.global.thirdparty.datagsm.config.DataGsmEnvironment;
import team.washer.server.v2.global.thirdparty.datagsm.dto.response.DataGsmStudentSearchResDto;
import team.washer.server.v2.global.thirdparty.datagsm.feign.DataGsmOpenApiClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class SyncDormitoryCouncilRoleServiceImpl implements SyncDormitoryCouncilRoleService {

    private static final String DORMITORY_MANAGER_ROLE = "DORMITORY_MANAGER";
    private static final int PAGE_SIZE = 300;

    private final DataGsmOpenApiClient dataGsmOpenApiClient;
    private final DataGsmEnvironment dataGsmEnvironment;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public int execute() {
        if (!dataGsmEnvironment.hasApiKey()) {
            log.warn("dormitory council role sync skipped reason=missing_api_key");
            return 0;
        }

        final Set<String> managerStudentIds = fetchDormitoryManagerStudentIds();
        if (managerStudentIds.isEmpty()) {
            log.info("dormitory council role sync no_targets fetched=0");
            return 0;
        }

        final List<User> candidates = userRepository.findByRoleAndStudentIdIn(UserRole.USER, managerStudentIds);
        int promoted = 0;
        for (final User user : candidates) {
            if (user.promoteToDormitoryCouncil()) {
                promoted++;
            }
        }

        log.info("dormitory council role sync completed fetched={} candidates={} promoted={}",
                managerStudentIds.size(),
                candidates.size(),
                promoted);
        return promoted;
    }

    /**
     * DataGSM OpenAPI를 페이지 단위로 순회하며 기숙사 관리 역할 학생의 학번을 수집합니다.
     *
     * @return 기숙사 관리 역할 학생의 학번 집합
     */
    private Set<String> fetchDormitoryManagerStudentIds() {
        final Set<String> studentIds = new HashSet<>();
        int page = 0;
        int totalPages = 1;
        do {
            final DataGsmStudentSearchResDto response = dataGsmOpenApiClient
                    .searchStudents(dataGsmEnvironment.apiKey(), DORMITORY_MANAGER_ROLE, true, page, PAGE_SIZE);
            if (response == null || response.data() == null || response.data().students() == null) {
                break;
            }
            for (final DataGsmStudentSearchResDto.Student student : response.data().students()) {
                if (student.studentNumber() != null) {
                    studentIds.add(student.studentNumber().toString());
                }
            }
            final Integer responseTotalPages = response.data().totalPages();
            totalPages = responseTotalPages != null ? responseTotalPages : page + 1;
            page++;
        } while (page < totalPages);
        return studentIds;
    }
}
