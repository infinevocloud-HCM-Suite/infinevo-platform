package com.itsdev.payroll.repository.payRun.offCyclePayrun;

import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.entity.payRun.offCyclePayrun.OffCyclePayRun;
import com.itsdev.payroll.entity.payRun.offCyclePayrun.OffCyclePayrunEmployee;
import com.itsdev.payroll.entity.employee.BasicDetails;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OffCyclePayrunEmployeeRepository extends JpaRepository<OffCyclePayrunEmployee, Long> {

    // Find all employees for a given payroll run entity
    List<OffCyclePayrunEmployee> findByPayrollRun(OffCyclePayRun payrollRun);

    // Find all employees for a payroll run and organization (multi-org safety)
    List<OffCyclePayrunEmployee> findByPayrollRunAndOrganization(OffCyclePayRun payrollRun, Organization org);

    // Find a specific employee in a payroll run
    Optional<OffCyclePayrunEmployee> findByPayrollRunAndEmployee(OffCyclePayRun payrollRun, BasicDetails employee);

    // Find all employees by Organization entity
    List<OffCyclePayrunEmployee> findByOrganization(Organization organization);

    // Find by employee ID and organization
    List<OffCyclePayrunEmployee> findByEmployee_IdAndOrganization(Long employeeId, Organization org);

    // Find all employees by payrollRunId (String) + organization
    List<OffCyclePayrunEmployee> findByPayrollRun_PayrollRunIdAndOrganization(String payrollRunId, Organization org);

}
