package com.itsdev.payroll.repository.employee;

import com.itsdev.payroll.entity.employee.BasicDetails;
import com.itsdev.payroll.entity.organization.Organization;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BasicDetailsRepository extends JpaRepository<BasicDetails, Long>, JpaSpecificationExecutor<BasicDetails> {

    /**
     * Search employee IDs by name or employee number within an organization.
     * Query stays within the employee table only (no cross-table join),
     * avoiding "Illegal mix of collations" errors when joining with other tables.
     * The 'search' parameter must already be lowercased and wrapped is done via LIKE %..%.
     */
    @Query("SELECT b.employeeId FROM BasicDetails b " +
           "WHERE b.organization.organizationId = :organizationId " +
           "AND ( LOWER(b.firstName) LIKE %:search% " +
           "   OR LOWER(b.lastName) LIKE %:search% " +
           "   OR LOWER(CONCAT(b.firstName, ' ', b.lastName)) LIKE %:search% " +
           "   OR LOWER(b.employeeNumber) LIKE %:search% )")
    List<String> searchEmployeeIdsByOrganization(
            @Param("organizationId") String organizationId,
            @Param("search") String search
    );


    List<BasicDetails> findByOrganization_OrganizationId(String organizationId);



    Page<BasicDetails> findByOrganization_OrganizationId(String organizationId, Pageable pageable);

    Optional<BasicDetails> findByIdAndOrganization_OrganizationId(Long id, String organizationId);

    Optional<BasicDetails> findByEmployeeNumberAndOrganization(String employeeNumber, Organization organization);
    
    boolean existsByEmployeeUniqueId(String employeeUniqueId);
    
    Optional<BasicDetails> findByWorkMail(String workMail);
    
    Optional<BasicDetails> findByOrganization_OrganizationIdAndWorkMail(String organizationId, String workMail);

    Optional<BasicDetails> findByOrganization_OrganizationIdAndId(String organizationId, Long id);
    
    Optional<BasicDetails> findByOrganization_OrganizationIdAndIdAndIsDeletedFalse(String organizationId, Long id);

    List<BasicDetails> findAllByOrganization_OrganizationIdAndIsDeletedFalse(String organizationId);
    
    Page<BasicDetails> findByOrganization_OrganizationIdAndIsDeletedFalse(String organizationId, Pageable pageable);
    
    Optional<BasicDetails> findByOrganization_OrganizationIdAndEmployeeId(String organizationId, String employeeId);

    Optional<BasicDetails> findByOrganization_OrganizationIdAndEmployeeUniqueId(String organizationId, String employeeUniqueId);

    List<BasicDetails> findByOrganization_OrganizationIdAndEmployeeStatus(String organizationId, String employeeStatus);

    List<BasicDetails> findByOrganization_OrganizationIdAndIsDeletedFalse(String organizationId);
    
    Optional<BasicDetails> findByEmployeeId(String employeeId);

    Optional<BasicDetails>
    findByEmployeeIdAndOrganization_OrganizationIdAndIsDeletedFalse(
            String employeeId,
            String organizationId
    );




}
