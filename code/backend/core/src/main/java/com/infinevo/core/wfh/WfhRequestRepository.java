package com.infinevo.core.wfh;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link WfhRequest} entities (W-74).
 */
@Repository
public interface WfhRequestRepository extends JpaRepository<WfhRequest, UUID>, JpaSpecificationExecutor<WfhRequest> {

    List<WfhRequest> findByTenantIdAndEmployeeIdOrderByFromDateDesc(UUID tenantId, UUID employeeId);

    Optional<WfhRequest> findByTenantIdAndId(UUID tenantId, UUID id);

    @Query("SELECT COUNT(r) > 0 FROM WfhRequest r "
            + "WHERE r.tenantId = :tenantId AND r.employeeId = :employeeId "
            + "AND r.status IN :statuses "
            + "AND r.fromDate <= :toDate AND r.toDate >= :fromDate")
    boolean existsOverlappingRequest(
            @Param("tenantId") UUID tenantId,
            @Param("employeeId") UUID employeeId,
            @Param("statuses") Collection<WfhRequestStatus> statuses,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    @Query("SELECT COUNT(r) > 0 FROM WfhRequest r "
            + "WHERE r.tenantId = :tenantId AND r.employeeId = :employeeId "
            + "AND r.status IN :statuses AND r.id <> :excludeId "
            + "AND r.fromDate <= :toDate AND r.toDate >= :fromDate")
    boolean existsOverlappingRequestExcluding(
            @Param("tenantId") UUID tenantId,
            @Param("employeeId") UUID employeeId,
            @Param("statuses") Collection<WfhRequestStatus> statuses,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("excludeId") UUID excludeId);
}
