package com.infinevo.core.org;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Reads and writes {@code core.reporting_line} (W-14.2).
 */
public interface ReportingLineRepository extends JpaRepository<ReportingLine, UUID> {

    @EntityGraph(attributePaths = {"employee", "manager"})
    @Query(
            """
            SELECT r FROM ReportingLine r
            WHERE r.tenantId = :tenantId
              AND r.employee.id = :employeeId
              AND (:kind IS NULL OR r.kind = :kind)
              AND r.effectiveFrom <= :asOf
              AND (r.effectiveTo IS NULL OR r.effectiveTo >= :asOf)
            ORDER BY r.effectiveFrom DESC
            """)
    List<ReportingLine> findActiveLines(
            @Param("tenantId") UUID tenantId,
            @Param("employeeId") UUID employeeId,
            @Param("kind") ReportingLineKind kind,
            @Param("asOf") LocalDate asOf);

    @EntityGraph(attributePaths = {"employee", "manager"})
    @Query(
            """
            SELECT r FROM ReportingLine r
            WHERE r.tenantId = :tenantId
              AND r.employee.id = :employeeId
              AND r.kind = :kind
              AND r.effectiveTo IS NULL
            """)
    Optional<ReportingLine> findCurrentOpenLine(
            @Param("tenantId") UUID tenantId,
            @Param("employeeId") UUID employeeId,
            @Param("kind") ReportingLineKind kind);

    @EntityGraph(attributePaths = {"employee", "manager"})
    @Query(
            """
            SELECT r FROM ReportingLine r
            WHERE r.tenantId = :tenantId
              AND r.manager.id = :managerId
              AND r.effectiveFrom <= :asOf
              AND (r.effectiveTo IS NULL OR r.effectiveTo >= :asOf)
            """)
    List<ReportingLine> findDirectReports(
            @Param("tenantId") UUID tenantId, @Param("managerId") UUID managerId, @Param("asOf") LocalDate asOf);

    @EntityGraph(attributePaths = {"employee", "manager"})
    @Query(
            """
            SELECT r FROM ReportingLine r
            WHERE r.tenantId = :tenantId
              AND r.employee.id = :employeeId
            """)
    List<ReportingLine> findAllByEmployee(@Param("tenantId") UUID tenantId, @Param("employeeId") UUID employeeId);
}
