package com.itsdev.payroll.repository.salarycomponents;

import com.itsdev.payroll.entity.salarycomponents.Earning;
import com.itsdev.payroll.entity.organization.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EarningRepository extends JpaRepository<Earning, Long> {
    List<Earning> findByOrganization(Organization organization);
    boolean existsByEarningId(String earningId);
    List<Earning> findByOrganizationAndIsDeletedFalse(Organization organization);
    boolean existsByOrganization_OrganizationId(String organizationId);



    Optional<Earning> findByEarningId(String earningId);


    Optional<Earning> findByEarningNameAndOrganization(String earningName, Organization organization);


}

