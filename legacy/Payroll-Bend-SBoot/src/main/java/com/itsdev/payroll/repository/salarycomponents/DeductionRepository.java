package com.itsdev.payroll.repository.salarycomponents;

import com.itsdev.payroll.entity.salarycomponents.Deduction;
import com.itsdev.payroll.entity.organization.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeductionRepository extends JpaRepository<Deduction, Long> {

    Optional<Deduction> findByDeductionId(String deductionId);

    List<Deduction> findByOrganization(Organization organization);

    boolean existsByOrganizationAndDeductionType(Organization organization, String deductionType);

    List<Deduction> findByOrganizationAndIsDeletedFalse(Organization organization);


    Optional<Deduction> findByDeductionNameAndOrganization(String deductionName, Organization organization);

}
