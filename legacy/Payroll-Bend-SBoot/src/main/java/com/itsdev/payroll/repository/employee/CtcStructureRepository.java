package com.itsdev.payroll.repository.employee;

import com.itsdev.payroll.entity.employee.CtcStructure;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;


import java.util.List;
import java.util.Optional;

@Repository
public interface CtcStructureRepository extends JpaRepository<CtcStructure, Long> {

    // Fetch single CTC structure by id + org
    Optional<CtcStructure> findByIdAndOrganization_OrganizationId(Long id, String organizationId);

    // Fetch all CTC structures for an org
    List<CtcStructure> findAllByOrganization_OrganizationId(String organizationId);

    // Check existence in org scope
    boolean existsByIdAndOrganization_OrganizationId(Long id, String organizationId);

    // Hard delete scoped to org
    void deleteByIdAndOrganization_OrganizationId(Long id, String organizationId);

  //  Optional<CtcStructure> findByOrganization_OrganizationIdAndEmployee_Id(String organizationId, Long employeeId);
  List<CtcStructure> findByOrganization_OrganizationIdAndEmployee_Id(
          String organizationId, Long employeeId);
  
  List<CtcStructure> findByOrganization_OrganizationIdAndEmployee_EmployeeId(
	        String organizationId,
	        String employeeId);


    List<CtcStructure> findByOrganization_OrganizationIdAndEmployee_IdAndIsActiveTrue(
            String organizationId,
            Long employeeId
    );

    Optional<CtcStructure> findFirstByOrganization_OrganizationIdAndEmployee_IdAndIsActiveTrueOrderByCreatedAtDesc(
            String organizationId,
            Long employeeId
    );


    Optional<CtcStructure> findFirstByOrganization_OrganizationIdAndEmployee_IdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
            String organizationId,
            Long employeeId,
            java.time.LocalDate effectiveDate
    );


    List<CtcStructure> findByOrganization_OrganizationIdAndEmployee_IdOrderByEffectiveDateDesc(
            String organizationId,
            Long employeeId
    );


    @Modifying
    @Query("""
    update CtcStructure c
    set c.isActive = false,
        c.updatedAt = :updatedAt
    where c.organization.organizationId = :organizationId
      and c.employee.id = :employeeId
      and c.isActive = true
""")
    int deactivateActiveCtcs(
            @Param("organizationId") String organizationId,
            @Param("employeeId") Long employeeId,
            @Param("updatedAt") LocalDateTime updatedAt
    );


    @Modifying
    @Query("""
    update CtcStructure c
    set c.isActive = false,
        c.updatedAt = :updatedAt
    where c.organization.organizationId = :organizationId
      and c.employee.id = :employeeId
      and c.isActive = true
      and c.id <> :excludeId
""")
    int deactivateOtherActiveCtcs(
            @Param("organizationId") String organizationId,
            @Param("employeeId") Long employeeId,
            @Param("excludeId") Long excludeId,
            @Param("updatedAt") LocalDateTime updatedAt
    );


    Optional<CtcStructure> findFirstByOrganization_OrganizationIdAndEmployee_IdAndEffectiveDateLessThanOrderByEffectiveDateDesc(
            String organizationId,
            Long employeeId,
            LocalDate effectiveDate
    );


  @Query("""
    select c
    from CtcStructure c
    where c.organization.organizationId = :organizationId
      and c.revision = true
      and c.revisionStatus is not null
      and c.deleted = false
    order by c.createdAt desc
""")
  Page<CtcStructure> findRevisedCtcs(
          @Param("organizationId") String organizationId,
          Pageable pageable
  );




    long countByOrganization_OrganizationIdAndEmployee_IdAndDeletedFalse(
            String organizationId,
            Long employeeId
    );

    List<CtcStructure>
    findAllByOrganization_OrganizationIdAndRevisionStatusIsNotNullAndDeletedFalseOrderByCreatedAtDesc(
            String organizationId
    );


    Optional<CtcStructure>
    findFirstByOrganization_OrganizationIdAndEmployee_IdAndEffectiveDateLessThanAndDeletedFalseOrderByEffectiveDateDesc(
            String organizationId,
            Long employeeId,
            LocalDate effectiveDate
    );

    List<CtcStructure> findAllByIdInAndOrganization_OrganizationId(
            List<Long> ids,
            String organizationId
    );





  Optional<CtcStructure>
  findFirstByOrganization_OrganizationIdAndEmployee_IdAndRevisionStatusIsNotNullAndDeletedFalseOrderByCreatedAtDesc(
          String organizationId,
          Long employeeId
  );


  @Query("""
select c
from CtcStructure c
where c.organization.organizationId = :organizationId
  and c.revisionStatus is not null
  and c.deleted = false
  and c.createdAt = (
        select max(c2.createdAt)
        from CtcStructure c2
        where c2.organization.organizationId = :organizationId
          and c2.employee.id = c.employee.id
          and c2.revisionStatus is not null
          and c2.deleted = false
  )
order by c.createdAt desc
""")
  List<CtcStructure> findLatestRevisionsPerEmployee(
          @Param("organizationId") String organizationId
  );

  Optional<CtcStructure>
  findFirstByOrganization_OrganizationIdAndEmployee_IdAndRevisionStatusIsNotNullAndDeletedFalseAndCreatedAtLessThanOrderByCreatedAtDesc(
          String organizationId,
          Long employeeId,
          LocalDateTime createdAt
  );


  boolean existsByOrganization_OrganizationIdAndEmployee_IdAndAppliedInPayrunFalseAndRevisionStatusIsNotNullAndDeletedFalse(
          String organizationId,
          Long employeeId
  );

}
