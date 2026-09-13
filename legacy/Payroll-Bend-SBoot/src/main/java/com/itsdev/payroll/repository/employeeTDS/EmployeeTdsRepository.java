package com.itsdev.payroll.repository.employeeTDS;

import com.itsdev.payroll.entity.employeeTDS.EmployeeTds;
import com.itsdev.payroll.enumeration.TdsSourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeTdsRepository extends JpaRepository<EmployeeTds, Long> {

    // ✅ Find active TDS record for employee in fiscal year
    @Query("SELECT et FROM EmployeeTds et WHERE et.employeeId = :employeeId " +
           "AND et.fiscalYear = :fiscalYear AND et.isActive = true")
    Optional<EmployeeTds> findActiveByEmployeeAndFiscalYear(
            @Param("employeeId") String employeeId, 
            @Param("fiscalYear") Integer fiscalYear);

    // ✅ Find all TDS records (active + inactive) for employee in fiscal year
    @Query("SELECT et FROM EmployeeTds et WHERE et.employeeId = :employeeId " +
           "AND et.fiscalYear = :fiscalYear ORDER BY et.createdAt DESC")
    List<EmployeeTds> findByEmployeeAndFiscalYear(
            @Param("employeeId") String employeeId, 
            @Param("fiscalYear") Integer fiscalYear);

    // ✅ Check if employee has any TDS record for fiscal year
    boolean existsByEmployeeIdAndFiscalYear(
            String employeeId, 
            Integer fiscalYear);


    // ✅ Find active TDS records by organization, fiscal year and source type
    @Query("SELECT et FROM EmployeeTds et WHERE et.organizationId = :organizationId " +
           "AND et.fiscalYear = :fiscalYear AND et.tdsSourceType = :tdsSourceType " +
           "AND et.isActive = true")
    List<EmployeeTds> findActiveByOrganizationAndFiscalYearAndSourceType(
            @Param("organizationId") String organizationId,
            @Param("fiscalYear") Integer fiscalYear,
            @Param("tdsSourceType") TdsSourceType tdsSourceType);


      // ✅ Find active TDS record for employee in fiscal year and organization      
          @Query("""
           SELECT et FROM EmployeeTds et
           WHERE et.organizationId = :organizationId
             AND et.employeeId = :employeeId
             AND et.fiscalYear = :fiscalYear
             AND et.isActive = true
           """)
    Optional<EmployeeTds> findActiveByOrganizationAndEmployeeAndFiscalYear(
            @Param("organizationId") String organizationId,
            @Param("employeeId") String employeeId,
            @Param("fiscalYear") Integer fiscalYear);



    @Modifying
    @Query("""
    UPDATE EmployeeTds et
    SET et.effectiveFromMonth = :month
    WHERE et.organizationId = :organizationId
""")
    int updateEffectiveMonthByOrganization(
            @Param("organizationId") String organizationId,
            @Param("month") String month);
}


