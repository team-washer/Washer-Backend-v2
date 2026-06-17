package team.washer.server.v2.domain.appversion.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import team.washer.server.v2.domain.appversion.entity.AppVersionPolicy;
import team.washer.server.v2.domain.appversion.enums.AppPlatform;

public interface AppVersionPolicyRepository extends JpaRepository<AppVersionPolicy, Long> {
    Optional<AppVersionPolicy> findByPlatform(AppPlatform platform);
}
