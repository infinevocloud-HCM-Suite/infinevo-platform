package com.infinevo.core.attendance;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Reads and writes {@code core.attendance} (W-39.1).
 */
public interface AttendanceRepository extends JpaRepository<Attendance, UUID> {

    @EntityGraph(attributePaths = {"employee"})
    Optional<Attendance> findByTenantIdAndEmployeeIdAndAttendanceDate(
            UUID tenantId, UUID employeeId, LocalDate attendanceDate);

    @EntityGraph(attributePaths = {"employee"})
    @Query(
            """
            SELECT a FROM Attendance a
            WHERE a.tenantId = :tenantId
              AND a.attendanceDate BETWEEN :from AND :to
            ORDER BY a.attendanceDate DESC, a.employee.id ASC
            """)
    List<Attendance> findByTenantIdAndDateRange(
            @Param("tenantId") UUID tenantId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @EntityGraph(attributePaths = {"employee"})
    @Query(
            """
            SELECT a FROM Attendance a
            WHERE a.tenantId = :tenantId
              AND a.employee.id = :employeeId
              AND a.attendanceDate BETWEEN :from AND :to
            ORDER BY a.attendanceDate DESC
            """)
    List<Attendance> findByTenantIdAndEmployeeIdAndDateRange(
            @Param("tenantId") UUID tenantId,
            @Param("employeeId") UUID employeeId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @EntityGraph(attributePaths = {"employee"})
    @Query(
            """
            SELECT a FROM Attendance a
            WHERE a.tenantId = :tenantId
              AND a.employee.id = :employeeId
              AND a.attendanceDate BETWEEN :from AND :to
            ORDER BY a.attendanceDate ASC
            """)
    List<Attendance> findByTenantIdAndEmployeeIdAndDateRangeAsc(
            @Param("tenantId") UUID tenantId,
            @Param("employeeId") UUID employeeId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    Optional<Attendance> findByIdAndTenantId(UUID id, UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);
}
