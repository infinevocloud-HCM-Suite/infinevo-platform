package com.itsdev.payroll.service.payruns;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;

import com.itsdev.payroll.dto.payruns.EmployeePayRunDTO;
import com.itsdev.payroll.dto.payruns.EmployeePayslipDTO;
import com.itsdev.payroll.dto.payruns.PayslipResponseDTO;
import com.itsdev.payroll.entity.payruns.PayRun;

public interface EmployeePayRunService {
	
    public List<EmployeePayRunDTO> getEmployeePayRunList(String organizationId, String processingPeriod);
    
    List<EmployeePayRunDTO> generateEmployeePayRuns(String organizationId,  PayRun payRun);
    
    public List<EmployeePayRunDTO> getEmployeePayRunListByPayRun(String organizationId, String payrunId);
    
    Page<EmployeePayslipDTO> getEmployeePayslips(String organizationId, String employeeId, int page, int size, Integer year);
    
    PayslipResponseDTO getEmployeePayslip(String organizationId, String employeeId, String payRunId);

    byte[] generateEmployeePayRunCsv(String organizationId, String payrunId, List<String> employeeIds);

}
