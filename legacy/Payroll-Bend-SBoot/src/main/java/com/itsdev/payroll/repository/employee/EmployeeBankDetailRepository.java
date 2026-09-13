package com.itsdev.payroll.repository.employee;

import com.itsdev.payroll.entity.employee.EmployeeBankDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface EmployeeBankDetailRepository extends JpaRepository<EmployeeBankDetail, Long> {

    // Get by employeeId + organizationId
    Optional<EmployeeBankDetail> findByEmployee_IdAndOrganization_OrganizationId(Long employeeId, String organizationId);

    // List all bank details for an organization
    List<EmployeeBankDetail> findAllByOrganization_OrganizationId(String organizationId);

    // Delete by employeeId + organizationId
    void deleteByEmployee_IdAndOrganization_OrganizationId(Long employeeId, String organizationId);
}

