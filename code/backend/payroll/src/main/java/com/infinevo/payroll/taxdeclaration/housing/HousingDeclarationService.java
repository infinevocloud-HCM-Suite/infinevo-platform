package com.infinevo.payroll.taxdeclaration.housing;

import com.infinevo.payroll.taxdeclaration.housing.dto.HomeLoanRequest;
import com.infinevo.payroll.taxdeclaration.housing.dto.HouseRentRequest;
import com.infinevo.payroll.taxdeclaration.housing.dto.HousingDeclarationResponse;
import com.infinevo.payroll.taxdeclaration.housing.dto.LetOutPropertyRequest;
import java.util.List;
import java.util.UUID;

/**
 * Service managing housing declarations: house rent, home loans, and let-out properties (W-32.2).
 */
public interface HousingDeclarationService {

    HousingDeclarationResponse readOwn(String financialYear);

    HousingDeclarationResponse read(UUID employeeId, String financialYear);

    HousingDeclarationResponse readByDeclarationId(UUID declarationId);

    HousingDeclarationResponse replaceHouseRentOwn(String financialYear, List<HouseRentRequest> requests);

    HousingDeclarationResponse replaceHouseRent(UUID employeeId, String financialYear, List<HouseRentRequest> requests);

    HousingDeclarationResponse replaceHomeLoansOwn(String financialYear, List<HomeLoanRequest> requests);

    HousingDeclarationResponse replaceHomeLoans(UUID employeeId, String financialYear, List<HomeLoanRequest> requests);

    HousingDeclarationResponse replaceLetOutPropertiesOwn(String financialYear, List<LetOutPropertyRequest> requests);

    HousingDeclarationResponse replaceLetOutProperties(
            UUID employeeId, String financialYear, List<LetOutPropertyRequest> requests);
}
