package com.infinevo.payroll.payslip;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service for rendering and retrieving payslips (W-36.2 §4).
 * Rendered on-the-fly from pay run rows, never stored.
 */
public interface PayslipService {

    /**
     * Renders a payslip on-the-fly for an employee pay run row (used by anonymous signed links).
     *
     * @param tenantId the bound tenant from the signed link token
     * @param employeePayrunId the employee payrun row id
     * @param linkExpiresAt the expiration timestamp to return in the link group, or null
     * @return the complete rendered payslip
     */
    PayslipResponse render(UUID tenantId, UUID employeePayrunId, Instant linkExpiresAt);

    /**
     * Officer read for an employee's payslip in any computed run.
     *
     * @param payrunId the pay run id
     * @param employeeId the employee id
     * @return the complete rendered payslip
     */
    PayslipResponse forOfficer(UUID payrunId, UUID employeeId);

    /**
     * Employee list of own payslips from PAID runs only, newest first.
     */
    Page<PayslipSummaryResponse> listOwn(Pageable pageable);

    /**
     * Employee read of own payslip for a specific pay run (PAID only).
     */
    PayslipResponse own(UUID payrunId);
}
