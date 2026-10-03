package com.infinevo.payroll.priorpayroll;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service for prior payroll month records, status and queries (W-38.1 §4).
 */
public interface PriorPayrollService {

    Page<PriorPayrollMonthResponse> months(String financialYear, UUID employeeId, Pageable pageable);

    void delete(UUID id);

    PriorPayrollStatusResponse status(String financialYear);

    boolean hasImportedRows(String period);
}
