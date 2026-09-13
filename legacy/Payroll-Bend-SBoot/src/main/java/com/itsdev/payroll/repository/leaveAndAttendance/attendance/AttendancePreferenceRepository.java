package com.itsdev.payroll.repository.leaveAndAttendance.attendance;


import com.itsdev.payroll.entity.leaveAndAttedance.attendance.AttendancePreference;
import com.itsdev.payroll.entity.organization.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AttendancePreferenceRepository extends JpaRepository<AttendancePreference, Long> {

    // Find all preferences by organization
    List<AttendancePreference> findByOrganization(Organization organization);

    // Find preference by preferenceId & organization
    Optional<AttendancePreference> findByIdAndOrganization(Long id, Organization organization);

//    // Find active (not deleted) preferences by organization
//    List<AttendancePreference> findByOrganizationAndDeleteFalse(Organization organization);

    // Find by periodType & organization
//    Optional<AttendancePreference> findByPeriodTypeAndOrganization(String periodType, Organization organization);
}
