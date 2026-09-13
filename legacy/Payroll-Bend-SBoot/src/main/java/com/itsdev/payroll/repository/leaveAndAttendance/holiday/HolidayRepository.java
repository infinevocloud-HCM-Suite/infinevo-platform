package com.itsdev.payroll.repository.leaveAndAttendance.holiday;


import com.itsdev.payroll.entity.leaveAndAttedance.holiday.Holiday;
import com.itsdev.payroll.entity.organization.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    // Find all holidays by organization
    List<Holiday> findByOrganization(Organization org);

    // Find holiday by holidayId & organization
    Optional<Holiday> findByHolidayIdAndOrganization(String holidayId, Organization org);

    // Find holiday by holidayId & organization & isDeleted = false
  //  Optional<Holiday> findByHolidayIdAndOrganizationAndIsDeletedFalse(String holidayId, Organization org);

    // Find all holidays by organization & isDeleted = false
 //   List<Holiday> findByOrganizationAndIsDeletedFalse(Organization org);

    //List<Holiday> findByOrganization(Organization organization);
}

