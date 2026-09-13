package com.itsdev.payroll.repository.employee;


import com.itsdev.payroll.entity.employee.EmployeeEarning;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// ===== EarningRepository =====
@Repository("employeeEarningRepository")
public interface EarningRepository extends JpaRepository<EmployeeEarning, Long> {
    Optional<EmployeeEarning> findByIdAndOrganization_OrganizationId(Long id, String organizationId);


    List<EmployeeEarning> findAllByCtcStructure_IdAndOrganization_OrganizationId(Long ctcStructureId, String organizationId);


    void deleteAllByCtcStructure_IdAndOrganization_OrganizationId(Long ctcStructureId, String organizationId);
}
