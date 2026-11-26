package com.infinevo.hrms.survey;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SurveyRepository extends JpaRepository<Survey, UUID> {

    List<Survey> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<Survey> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, SurveyStatus status);

    Optional<Survey> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query("SELECT s FROM Survey s WHERE s.tenantId = :tenantId AND s.status = :status "
            + "AND s.startDate <= :today AND s.endDate >= :today ORDER BY s.endDate ASC")
    List<Survey> findActiveSurveys(
            @Param("tenantId") UUID tenantId, @Param("status") SurveyStatus status, @Param("today") LocalDate today);
}
