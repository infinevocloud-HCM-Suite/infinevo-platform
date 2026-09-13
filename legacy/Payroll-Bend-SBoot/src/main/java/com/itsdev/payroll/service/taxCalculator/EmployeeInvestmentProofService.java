package com.itsdev.payroll.service.taxCalculator;

import java.io.IOException;

import com.itsdev.payroll.dto.taxCalculator.EmployeeInvestmentProofRequest;
import com.itsdev.payroll.dto.taxCalculator.EmployeeInvestmentProofResponse;

public interface EmployeeInvestmentProofService {
	
    void submitInvestmentProof(EmployeeInvestmentProofRequest request, String organizationId)  throws IOException;
    
    EmployeeInvestmentProofResponse getProofByEmployeeId(String employeeId, String organizationId, Integer financialYear);

}
