package com.itsdev.payroll.service.employeeitdeclaration;

import com.itsdev.payroll.dto.employeeitdeclaration.EmployeeInvestmentDeclarationDTO;
import com.itsdev.payroll.dto.employeeitdeclaration.EmployeeInvestmentDeclarationRequestDTO;


public interface EmployeeInvestmentDeclarationService {

    EmployeeInvestmentDeclarationDTO getDeclaration(
            String organizationId,
            String employeeId,
            Integer fiscalYear
    );

EmployeeInvestmentDeclarationDTO createDeclaration(
        String organizationId,
        String employeeId,
        Integer fiscalYear,
        EmployeeInvestmentDeclarationRequestDTO dto
);


EmployeeInvestmentDeclarationDTO updateDeclaration(
        String organizationId,
        String employeeId,
        Integer fiscalYear,
        EmployeeInvestmentDeclarationRequestDTO dto
);

    void deleteDeclaration(
            String organizationId,
            String employeeId,
            Integer fiscalYear
    );
}
