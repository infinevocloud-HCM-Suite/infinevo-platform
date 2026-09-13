package com.itsdev.payroll.repository.payRun.offCyclePayrun;

import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.entity.payRun.offCyclePayrun.OffCyclePayRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OffCyclePayRunRepository extends JpaRepository<OffCyclePayRun, Long> {

    // Find all payroll runs for an organization
    List<OffCyclePayRun> findByOrganization(Organization organization);

    // Find a payroll run by its business ID and organization
    Optional<OffCyclePayRun> findByPayrollRunIdAndOrganization(String payrollRunId, Organization organization);
}

