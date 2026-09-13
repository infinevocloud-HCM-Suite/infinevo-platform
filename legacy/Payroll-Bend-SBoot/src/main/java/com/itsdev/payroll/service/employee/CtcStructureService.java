package com.itsdev.payroll.service.employee;

import com.itsdev.payroll.dto.employee.CtcStructureDTO;
import com.itsdev.payroll.dto.employee.EmployeeCTCDTO;
import com.itsdev.payroll.dto.employee.SalaryRevision.EmployeeCtcRevisionDTO;
import com.itsdev.payroll.dto.employee.SalaryRevision.EmployeeCtcRevisionListDTO;
// import com.itsdev.payroll.dto.employee.SalaryRevision.ProcessLaterRevisionDTO;
import com.itsdev.payroll.dto.employee.SalaryRevision.ProcessLaterRevisionDTO;
import org.springframework.data.domain.Page;

import java.util.List;

public interface CtcStructureService {

    // Create new CTC structure
    EmployeeCTCDTO create(String organizationId, EmployeeCTCDTO dto);

    // Update existing CTC structure (by numeric id)
    EmployeeCTCDTO update(String organizationId,  EmployeeCTCDTO dto);

    // Get single CTC structure
    EmployeeCTCDTO get(String organizationId, Long ctcStructureId);

    // List all CTC structures for an organization
    List<EmployeeCTCDTO> list(String organizationId);

    // Delete CTC structure
    void delete(String organizationId, Long id);

//    EmployeeCTCDTO getByEmployeeId(String organizationId, Long employeeId);

    List<EmployeeCTCDTO> getAllByEmployeeId(String organizationId, Long employeeId);
    
    CtcStructureDTO getSalaryStructureByWorkMail(String email);


    // ✅ NEW: Salary revision (creates new CTC + deactivates old one)
    EmployeeCTCDTO revise(String organizationId, EmployeeCtcRevisionDTO dto);


    EmployeeCTCDTO updateRevision(String organizationId, EmployeeCtcRevisionDTO dto);


    void deleteRevision(String organizationId, Long revisionId, Long ctcStructureId);


    EmployeeCTCDTO getRevision(String organizationId, Long revisionId, Long ctcStructureId);


    Page<EmployeeCtcRevisionListDTO> getAllRevisedCtcs(
            String organizationId,
            int page,
            int size
    );

    byte[] exportRevisedCtcs(String organizationId);

    void processLaterRevisions(String organizationId, ProcessLaterRevisionDTO dto);


}
