package com.infinevo.payroll.taxdeclaration.summary;

import com.infinevo.payroll.taxdeclaration.summary.dto.OtherIncomeRequest;
import com.infinevo.payroll.taxdeclaration.summary.dto.OtherIncomeResponse;
import java.util.List;
import java.util.UUID;

/**
 * Service managing other income declarations (W-32.4).
 */
public interface OtherIncomeService {

    List<OtherIncomeResponse> readOwn(String financialYear);

    List<OtherIncomeResponse> read(UUID employeeId, String financialYear);

    List<OtherIncomeResponse> replaceOwn(String financialYear, List<OtherIncomeRequest> requests);

    List<OtherIncomeResponse> replace(UUID employeeId, String financialYear, List<OtherIncomeRequest> requests);
}
