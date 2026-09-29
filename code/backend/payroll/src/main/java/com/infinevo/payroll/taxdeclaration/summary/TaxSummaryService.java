package com.infinevo.payroll.taxdeclaration.summary;

import com.infinevo.payroll.taxdeclaration.summary.dto.TaxSummaryFigures;
import com.infinevo.payroll.taxdeclaration.summary.dto.TaxSummaryResponse;
import java.util.UUID;

/**
 * Service managing consolidated tax declaration summaries and computation records (W-32.4).
 */
public interface TaxSummaryService {

    TaxSummaryResponse summaryOwn(String financialYear);

    TaxSummaryResponse summary(UUID employeeId, String financialYear);

    void record(UUID declarationId, String regime, TaxSummaryFigures figures);
}
