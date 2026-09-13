package com.itsdev.payroll.repository.leaveAndAttendance;




import com.itsdev.payroll.entity.leaveAndAttedance.LeaveType;
import com.itsdev.payroll.entity.organization.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, Long> {

    // Fetch all leave types for a given organization
    List<LeaveType> findByOrganization(Organization organization);

    // Check if leave type already exists by code (within an organization)
    boolean existsByCodeAndOrganization(String code, Organization organization);

    // Find leave type by ID and organization (safety in multi-org system)
    Optional<LeaveType> findByIdAndOrganization(Long id, Organization organization);

    // Fetch only active leave types
    List<LeaveType> findByOrganizationAndStatus(Organization organization, String status);

}

