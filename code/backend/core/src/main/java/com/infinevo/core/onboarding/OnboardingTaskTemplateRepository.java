package com.infinevo.core.onboarding;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OnboardingTaskTemplateRepository extends JpaRepository<OnboardingTaskTemplate, UUID> {

    List<OnboardingTaskTemplate> findByTenantIdAndIsActiveTrueOrderBySortOrderAscCreatedAtAsc(UUID tenantId);

    List<OnboardingTaskTemplate> findByTenantIdOrderBySortOrderAscCreatedAtAsc(UUID tenantId);

    Optional<OnboardingTaskTemplate> findByIdAndTenantId(UUID id, UUID tenantId);
}
