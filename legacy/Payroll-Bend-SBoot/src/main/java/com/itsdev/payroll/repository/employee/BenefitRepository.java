package com.itsdev.payroll.repository.employee;

// Common imports used below

import com.itsdev.payroll.entity.employee.EmployeeBenefit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.*;

// ===== BenefitRepository =====
@Repository("employeeBenefitRepository")
public interface BenefitRepository extends JpaRepository<EmployeeBenefit, Long> {
    Optional<EmployeeBenefit> findByIdAndOrganization_OrganizationId(Long id, String organizationId);


    List<EmployeeBenefit> findAllByCtcStructure_IdAndOrganization_OrganizationId(Long ctcStructureId, String organizationId);


    void deleteAllByCtcStructure_IdAndOrganization_OrganizationId(Long ctcStructureId, String organizationId);
}
