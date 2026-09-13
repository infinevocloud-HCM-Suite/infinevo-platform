package com.itsdev.payroll.repository.salarycomponents;

import com.itsdev.payroll.entity.salarycomponents.Benefit;
import com.itsdev.payroll.entity.organization.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BenefitRepository extends JpaRepository<Benefit, Long> {
    List<Benefit> findByOrganization(Organization org);
    Optional<Benefit> findByBenefitIdAndOrganization(String benefitId, Organization org);
    Optional<Benefit> findByBenefitIdAndOrganizationAndIsDeletedFalse(String benefitId, Organization org);
    List<Benefit> findByOrganizationAndIsDeletedFalse(Organization organization);

  //  Optional<Benefit> findByBenefitId(String benefitId);


}
