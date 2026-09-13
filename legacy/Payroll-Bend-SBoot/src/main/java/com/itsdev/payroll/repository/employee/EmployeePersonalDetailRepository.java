package com.itsdev.payroll.repository.employee;



import com.itsdev.payroll.entity.employee.EmployeePersonalDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeePersonalDetailRepository extends JpaRepository<EmployeePersonalDetail, Long> {

    // Fetch personal detail by employeeId + organizationId
    Optional<EmployeePersonalDetail> findByEmployee_IdAndOrganization_OrganizationId(Long employeeId, String organizationId);

    // List all personal details for an organization
    List<EmployeePersonalDetail> findAllByOrganization_OrganizationId(String organizationId);

    // Delete personal detail by employeeId + organizationId
    void deleteByEmployee_IdAndOrganization_OrganizationId(Long employeeId, String organizationId);

    // OPTIONAL helper
    Optional<EmployeePersonalDetail> findByEmployee_Id(Long employeeId);

    boolean existsByOrganization_OrganizationId(String organizationId);
    
    Optional<EmployeePersonalDetail> findByPanAndOrganization_OrganizationId(String pan, String organizationId);



}

